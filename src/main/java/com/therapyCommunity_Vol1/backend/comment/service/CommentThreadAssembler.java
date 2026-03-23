package com.therapyCommunity_Vol1.backend.comment.service;

import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.dto.CommentResponse;
import com.therapyCommunity_Vol1.backend.comment.dto.ReplyCommentResponse;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CommentThreadAssembler {

    public List<CommentResponse> assemble(
            List<TherapyPostComment> comments,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        Map<Long, CommentResponse> rootComments = new LinkedHashMap<>();
        Map<Long, List<ReplyCommentResponse>> repliesByParentId = new LinkedHashMap<>();

        for (TherapyPostComment comment : comments) {
            if (comment.getParentComment() == null) {
                rootComments.put(comment.getId(),
                        CommentResponse.from(comment, currentUserId, currentUserRole)
                );
                continue;
            }

            repliesByParentId.computeIfAbsent(
                    comment.getParentComment().getId(),
                    ignored -> new ArrayList<>()
            ).add(ReplyCommentResponse.from(comment, currentUserId, currentUserRole));
        }

        return rootComments.values().stream()
                .map(root -> root.withReplies(repliesByParentId.getOrDefault(root.getId(), List.of())))
                .toList();
    }
}
