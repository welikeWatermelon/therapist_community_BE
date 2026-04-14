package com.therapyCommunity_Vol1.backend.admin.service;

import com.therapyCommunity_Vol1.backend.admin.dto.AdminCommentListResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.AdminPostListResponse;
import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyArea;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminContentService {

    private final PostService postService;
    private final CommentService commentService;

    public PagedResponse<AdminPostListResponse> getPosts(
            String keyword,
            TherapyArea therapyArea,
            PostType postType,
            Visibility visibility,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TherapyPost> result = postService.searchPostsForAdmin(keyword, therapyArea, postType, visibility, pageable);
        List<AdminPostListResponse> items = result.getContent().stream()
                .map(AdminPostListResponse::from)
                .toList();
        return PagedResponse.from(result, items);
    }

    @Transactional
    public AdminPostListResponse changePostVisibility(Long postId, Visibility visibility) {
        TherapyPost post = postService.changeVisibility(postId, visibility);
        return AdminPostListResponse.from(post);
    }

    @Transactional
    public void deletePost(Long postId) {
        postService.adminSoftDelete(postId);
    }

    public PagedResponse<AdminCommentListResponse> getComments(Long postId, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TherapyPostComment> result = commentService.searchCommentsForAdmin(postId, pageable);
        List<AdminCommentListResponse> items = result.getContent().stream()
                .map(AdminCommentListResponse::from)
                .toList();
        return PagedResponse.from(result, items);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        commentService.adminSoftDelete(commentId);
    }
}
