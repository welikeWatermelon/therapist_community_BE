package com.therapyCommunity_Vol1.backend.comment.dto;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.reaction.domain.CommentReactionType;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class CommentResponse {

    private static final String DELETED_PLACEHOLDER = "삭제된 댓글입니다.";

    private Long id;
    private Long postId;
    private Long parentCommentId;
    private Long authorId;
    private String authorNickname;
    private String authorRole;
    private boolean authorIsAi;
    private String content;
    private boolean deleted;
    private boolean canEdit;
    private boolean canDelete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long likeCount;
    private long dislikeCount;
    private CommentReactionType myReactionType;
    private List<ReplyCommentResponse> replies;

    /**
     * 단일 댓글 응답 (생성/수정/조회 시) — reaction 정보가 없거나 별도 조회됨.
     * 호출처가 reactions 인자에 별도 조회 결과 또는 {@link CommentReactionAggregate#empty()}를 전달.
     */
    public static CommentResponse from(
            TherapyPostComment comment,
            Long currentUserId,
            UserRole currentUserRole,
            String aiUserEmail,
            CommentReactionAggregate reactions
    ) {
        boolean canManage = !comment.isDeleted() && canManage(comment, currentUserId, currentUserRole);
        return new CommentResponse(
                comment.getId(),
                comment.getPost().getId(),
                comment.getParentComment() != null ? comment.getParentComment().getId() : null,
                comment.getAuthor().getId(),
                comment.getAuthor().getDisplayNickname(),
                comment.getAuthor().getRole().getCode(),
                aiUserEmail != null && aiUserEmail.equals(comment.getAuthor().getEmail()),
                comment.isDeleted() ? DELETED_PLACEHOLDER : comment.getContent(),
                comment.isDeleted(),
                canManage,
                canManage,
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                reactions.likeCount(),
                reactions.dislikeCount(),
                reactions.myReactionType(),
                List.of()
        );
    }

    public CommentResponse withReplies(List<ReplyCommentResponse> replies) {
        return new CommentResponse(
                id,
                postId,
                parentCommentId,
                authorId,
                authorNickname,
                authorRole,
                authorIsAi,
                content,
                deleted,
                canEdit,
                canDelete,
                createdAt,
                updatedAt,
                likeCount,
                dislikeCount,
                myReactionType,
                replies
        );
    }

    private static boolean canManage(
            TherapyPostComment comment,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);
        return isAdmin || isAuthor;
    }
}
