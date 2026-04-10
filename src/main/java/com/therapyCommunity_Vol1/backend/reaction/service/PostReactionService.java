package com.therapyCommunity_Vol1.backend.reaction.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.notification.domain.NotificationType;
import com.therapyCommunity_Vol1.backend.notification.event.NotificationEvent;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.post.service.ActivePostFinder;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.reaction.domain.TherapyPostReaction;
import com.therapyCommunity_Vol1.backend.reaction.dto.PostReactionStatusResponse;
import com.therapyCommunity_Vol1.backend.reaction.dto.TogglePostReactionRequest;
import com.therapyCommunity_Vol1.backend.reaction.repository.TherapyPostReactionRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
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
    private final TherapyPostRepository therapyPostRepository;
    private final ActivePostFinder activePostFinder;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

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
            Long postId,
            TogglePostReactionRequest request
    ) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = activePostFinder.findOrThrow(postId);

        postReactionRepository.findByPostIdAndUserId(postId, currentUserId)
                .ifPresentOrElse(existing -> {
                    if (existing.getReactionType() == request.getReactionType()) {
                        // 같은 반응 → 삭제 (토글 off)
                        postReactionRepository.delete(existing);
                    } else {
                        // 다른 반응 → 타입 변경
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
                });

        therapyPostRepository.recalculatePopularityScore(postId);

        return getReactionStatus(currentUserId, postId);
    }

    /**
     * 반응 상태 조회.
     * - grouped count 1회 쿼리로 모든 타입의 count를 집계
     * - PostReactionType.values() 순회로 누락 없는 count map 생성
     * - top reaction 계산 (count 최대, 동률 시 displayOrder 우선)
     */
    public PostReactionStatusResponse getReactionStatus(
            Long currentUserId,
            Long postId
    ) {
        activePostFinder.findOrThrow(postId);

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
                // Legacy 필드 (하위 호환)
                reactionCounts.getOrDefault(PostReactionType.EMPATHY, 0L),
                reactionCounts.getOrDefault(PostReactionType.APPRECIATE, 0L),
                reactionCounts.getOrDefault(PostReactionType.HELPFUL, 0L),
                myReactionType,
                // 확장 필드
                reactionCounts,
                top.type(),
                top.count(),
                top.colorToken()
        );
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────

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
