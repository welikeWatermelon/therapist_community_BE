package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.ReplyCommentResponse;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommentThreadAssembler {

    private final AiCommentProperties aiCommentProperties;

    /**
     * 댓글 + 대댓글을 트리 구조로 어셈블.
     *
     * Soft-delete 처리:
     * - 삭제된 root 댓글: placeholder("삭제된 댓글입니다")로 유지 — 자식 reply가 있을 수 있어 thread 연속성 보존.
     *   {@link CommentResponse#from}이 deleted=true일 때 자동으로 placeholder를 채움.
     * - 삭제된 reply: 응답에서 완전히 제외 — 부모 thread에 노이즈를 남기지 않음.
     *   삭제된 reply에는 자식이 없으므로(depth=2 제한) 고아 발생 가능성 없음.
     */
    public List<CommentResponse> assemble(
            List<TherapyPostComment> comments,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        String aiEmail = aiCommentProperties.getAiUserEmail();
        Map<Long, CommentResponse> rootComments = new LinkedHashMap<>();
        Map<Long, List<ReplyCommentResponse>> repliesByParentId = new LinkedHashMap<>();

        for (TherapyPostComment comment : comments) {
            if (comment.getParentComment() == null) {
                // root는 deleted여도 placeholder로 유지 (thread 연속성)
                rootComments.put(comment.getId(),
                        CommentResponse.from(comment, currentUserId, currentUserRole, aiEmail)
                );
                continue;
            }

            // reply가 deleted면 응답에서 제외 — 활성 reply만 트리에 포함
            if (comment.isDeleted()) {
                continue;
            }

            repliesByParentId.computeIfAbsent(
                    comment.getParentComment().getId(),
                    ignored -> new ArrayList<>()
            ).add(ReplyCommentResponse.from(comment, currentUserId, currentUserRole, aiEmail));
        }

        return rootComments.values().stream()
                .map(root -> root.withReplies(repliesByParentId.getOrDefault(root.getId(), List.of())))
                .toList();
    }
}
