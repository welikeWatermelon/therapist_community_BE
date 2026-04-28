package com.therapyCommunity_Vol1.backend.post.dto;

import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.reaction.domain.PostReactionType;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class TherapyPostDetailResponse {

    private Long id;
    private String content;
    private PostType postType;
    private Long authorId;
    private String authorNickname;
    private TherapyArea therapyArea;
    private Visibility visibility;
    private Long viewCount;
    private Long commentCount;
    private Map<PostReactionType, Long> reactionCounts;
    private PostReactionType myReactionType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean canEdit;
    private boolean canDelete;
    private boolean isScrapped;
    private List<PostAttachmentResponse> attachments;
    private String autoCommentStatus;
    private String autoCommentSourceMode;

    // 생성/수정 응답 (스크랩·카운트·리액션 없음)
    public static TherapyPostDetailResponse from(
            TherapyPost post,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        return from(
                post,
                List.of(),
                0L,
                emptyReactionCounts(),
                null,
                currentUserId,
                currentUserRole,
                false
        );
    }

    // 상세 조회 (스크랩·카운트·리액션 포함)
    public static TherapyPostDetailResponse from(
            TherapyPost post,
            List<PostAttachmentResponse> attachments,
            Long commentCount,
            Map<PostReactionType, Long> reactionCounts,
            PostReactionType myReactionType,
            Long currentUserId,
            UserRole currentUserRole,
            boolean isScrapped
    ) {
        boolean canManage = canManage(post, currentUserId, currentUserRole);
        return new TherapyPostDetailResponse(
                post.getId(),
                post.getContent(),
                post.getPostType(),
                post.getAuthor().getId(),
                post.getAuthor().getDisplayNickname(),
                post.getTherapyArea(),
                post.getVisibility(),
                post.getViewCount(),
                commentCount,
                reactionCounts,
                myReactionType,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                canManage,
                canManage,
                isScrapped,
                attachments,
                null,
                null
        );
    }

    public void setAutoComment(String status, String sourceMode) {
        this.autoCommentStatus = status;
        this.autoCommentSourceMode = sourceMode;
    }

    private static Map<PostReactionType, Long> emptyReactionCounts() {
        Map<PostReactionType, Long> counts = new EnumMap<>(PostReactionType.class);
        Arrays.stream(PostReactionType.values()).forEach(t -> counts.put(t, 0L));
        return counts;
    }

    // 관리자이거나, 내 글이면 수정/삭제 가능
    private static boolean canManage(
            TherapyPost post,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = post.getAuthor().getId().equals(currentUserId);
        return isAdmin || isAuthor;
    }
}
