package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.event.UserEventPublisher;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.post.service.PostVisibilityAccessPolicy;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.PostReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.TogglePostReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostReactionService {

    private final TherapyPostReactionRepository postReactionRepository;
    private final PostService postService;
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final PostVisibilityAccessPolicy visibilityPolicy;
    private final UserEventPublisher userEventPublisher;

    /**
     * 게시글 목록 응답에 myReactionType을 batch로 채우기 위한 헬퍼.
     * userId가 null이면 빈 맵 반환 (anonymous 사용자).
     * 결과: postId → myReactionType (해당 사용자가 그 게시글에 남긴 반응)
     */
    public Map<Long, PostReactionType> getMyReactionByPostIds(Long userId, List<Long> postIds) {
        if (userId == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostReactionType> result = new java.util.HashMap<>();
        postReactionRepository.findByPostIdInAndUserId(postIds, userId)
                .forEach(r -> result.put(r.getPost().getId(), r.getReactionType()));
        return result;
    }

    /**
     * 반응 토글 (생성/삭제/변경).
     * 규칙:
     *  - 반응 없음 → 새 반응 생성
     *  - 같은 반응 다시 누름 → 삭제 (토글 off)
     *  - 다른 반응 누름 → 타입 변경
     */
    @Transactional
    public PostReactionStatusResponse toggleReaction(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            TogglePostReactionRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        postReactionRepository.findByPostIdAndUserId(postId, currentUserId)
                .ifPresentOrElse(existing -> {
                    if (existing.getReactionType() == request.getReactionType()) {
                        // 같은 반응 → 삭제 (토글 off). 부정 시그널이므로 analytics 미수집.
                        postReactionRepository.delete(existing);
                    } else {
                        // 다른 반응 → 타입 변경. 이미 활성 상태이므로 신규 positive signal이 아님.
                        // 동일 유저가 LIKE↔USEFUL을 반복 토글할 경우 지표가 부풀려져, 어뷰징 가능성을
                        // 차단하기 위해 analytics 미수집.
                        existing.changeReactionType(request.getReactionType());
                    }
                }, () -> {
                    // 반응 없음 → 새로 생성
                    TherapyPostReaction reaction = TherapyPostReaction.create(
                            post, user, request.getReactionType()
                    );
                    postReactionRepository.save(reaction);

                    eventPublisher.publishEvent(NotificationEvent.builder()
                            .senderId(currentUserId)
                            .receiverIds(List.of(post.getAuthor().getId()))
                            .type(NotificationType.NEW_POST_REACTION)
                            .referenceId(postId)
                            .content(user.getNickname() + "님이 회원님의 게시글에 " + request.getReactionType().getLabel() + " 반응을 남겼습니다.")
                            .build());

                    publishReactAnalytics(currentUserId, postId, request.getReactionType());
                });

        postService.recalculatePopularityScore(postId);

        return getReactionStatus(currentUserId, currentUserRole, postId);
    }

    /**
     * 반응 상태 조회.
     * - grouped count 1회 쿼리로 모든 타입의 count를 집계
     * - PostReactionType.values() 순회로 누락 없는 count map 생성
     * - top reaction 계산 (count 최대, 동률 시 displayOrder 우선)
     */
    public PostReactionStatusResponse getReactionStatus(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId
    ) {
        TherapyPost post = activePostFinder.findOrThrow(postId);
        visibilityPolicy.checkAccess(post, currentUserRole);

        // 내 반응 타입 조회
        PostReactionType myReactionType = postReactionRepository
                .findByPostIdAndUserId(postId, currentUserId)
                .map(TherapyPostReaction::getReactionType)
                .orElse(null);

        // grouped count: 1회 GROUP BY 쿼리 → Map 변환
        Map<PostReactionType, Long> reactionCounts = buildCountMap(postId);

        // top reaction 계산
        TopReaction top = resolveTopReaction(reactionCounts);

        return new PostReactionStatusResponse(
                postId,
                // 타입별 개별 필드
                reactionCounts.getOrDefault(PostReactionType.LIKE, 0L),
                reactionCounts.getOrDefault(PostReactionType.CURIOUS, 0L),
                reactionCounts.getOrDefault(PostReactionType.USEFUL, 0L),
                myReactionType,
                // 확장 필드
                reactionCounts,
                top.type(),
                top.count(),
                top.colorToken()
        );
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────

    /** positive reaction 신호만 analytics로 전송 (전문성/매칭 지표 산출용). */
    private void publishReactAnalytics(Long userId, Long postId, PostReactionType reactionType) {
        userEventPublisher.publish(
                userId,
                UserEventType.POST_REACT,
                EventTargetType.POST,
                postId,
                Map.of("reactionType", reactionType.name())
        );
    }


    /**
     * GROUP BY 쿼리 결과를 모든 반응 타입이 포함된 EnumMap으로 변환.
     * DB에 count가 0인 타입은 쿼리 결과에 없으므로 0L로 보정.
     *
     * 반응 타입이 추가되어도 이 메서드는 수정 불필요 — values() 순회.
     */
    private Map<PostReactionType, Long> buildCountMap(Long postId) {
        Map<PostReactionType, Long> counts = new EnumMap<>(PostReactionType.class);

        // 모든 타입을 0으로 초기화
        Arrays.stream(PostReactionType.values())
                .forEach(type -> counts.put(type, 0L));

        // DB 결과로 덮어쓰기
        postReactionRepository.countGroupedByPostId(postId)
                .forEach(row -> {
                    PostReactionType type = (PostReactionType) row[0];
                    Long count = (Long) row[1];
                    counts.put(type, count);
                });

        return counts;
    }

    /**
     * 대표 반응(top reaction) 결정.
     * - count가 가장 큰 반응 1개 선택
     * - 동률이면 displayOrder가 낮은(우선순위 높은) 타입 선택
     * - 모두 0이면 null
     */
    private TopReaction resolveTopReaction(Map<PostReactionType, Long> counts) {
        return counts.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Comparator
                        .comparingLong(Map.Entry<PostReactionType, Long>::getValue)
                        .thenComparing((a, b) ->
                                Integer.compare(b.getKey().getDisplayOrder(), a.getKey().getDisplayOrder()))
                )
                .map(e -> new TopReaction(e.getKey(), e.getValue(), e.getKey().getColorToken()))
                .orElse(TopReaction.NONE);
    }

    /** top reaction 계산 결과를 담는 내부 record */
    private record TopReaction(PostReactionType type, Long count, String colorToken) {
        static final TopReaction NONE = new TopReaction(null, null, null);
    }
}
