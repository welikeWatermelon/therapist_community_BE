package com.therapyCommunity_Vol1.backend.application.mypage;

import com.therapyCommunity_Vol1.backend.application.mypage.dto.MyCommentResponse;
import com.therapyCommunity_Vol1.backend.comment.domain.TherapyPostComment;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.file.dto.StoredFileResource;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowCountResponse;
import com.therapyCommunity_Vol1.backend.follow.dto.FollowUserResponse;
import com.therapyCommunity_Vol1.backend.follow.service.FollowService;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.domain.PostType;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MyPageFacade {

    private static final String DELETED_CONTENT = "삭제된 댓글입니다";

    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final FollowService followService;

    public CurrentUserResponse getCurrentUser(Long userId) {
        FollowCountResponse counts = followService.getFollowCounts(userId);
        return userService.getCurrentUser(userId, counts.getFollowerCount(), counts.getFollowingCount());
    }

    public PagedResponse<TherapyPostSummaryResponse> getMyPosts(Long userId, int page, int size, PostType postType) {
        return postService.getMyPosts(userId, page, size, postType);
    }

    public PagedResponse<MyCommentResponse> getMyComments(Long userId, int page, int size) {
        Page<TherapyPostComment> comments = commentService.getMyComments(userId, page, size);

        List<MyCommentResponse> items = comments.getContent().stream()
                .map(c -> new MyCommentResponse(
                        c.getId(),
                        c.isDeleted() ? DELETED_CONTENT : c.getContent(),
                        c.getPost().getId(),
                        c.getCreatedAt(),
                        c.isDeleted()
                ))
                .toList();

        return PagedResponse.from(comments, items);
    }

    public CurrentUserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        FollowCountResponse counts = followService.getFollowCounts(userId);
        return userService.updateProfile(userId, request, counts.getFollowerCount(), counts.getFollowingCount());
    }

    public String uploadProfileImage(Long userId, MultipartFile file) {
        return userService.uploadProfileImage(userId, file);
    }

    public StoredFileResource loadProfileImage(String filename) {
        return userService.loadProfileImage(filename);
    }

    public FollowCountResponse getFollowCounts(Long userId) {
        return followService.getFollowCounts(userId);
    }

    public PagedResponse<FollowUserResponse> getMyFollowers(Long userId, int page, int size) {
        return followService.getFollowers(userId, page, size);
    }

    public PagedResponse<FollowUserResponse> getMyFollowings(Long userId, int page, int size) {
        return followService.getFollowings(userId, page, size);
    }

    public void withdraw(Long userId) {
        userService.withdraw(userId);
    }
}
