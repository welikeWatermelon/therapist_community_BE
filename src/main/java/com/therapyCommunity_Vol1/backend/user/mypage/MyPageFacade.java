package com.therapyCommunity_Vol1.backend.user.mypage;

import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.TherapyPostSummaryResponse;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.user.dto.CurrentUserResponse;
import com.therapyCommunity_Vol1.backend.user.dto.UpdateProfileRequest;
import com.therapyCommunity_Vol1.backend.user.mypage.dto.MyCommentResponse;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class MyPageFacade {

    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;

    public CurrentUserResponse getCurrentUser(Long userId) {
        return userService.getCurrentUser(userId);
    }

    public PagedResponse<TherapyPostSummaryResponse> getMyPosts(Long userId, int page, int size) {
        return postService.getMyPosts(userId, page, size);
    }

    public PagedResponse<MyCommentResponse> getMyComments(Long userId, int page, int size) {
        return commentService.getMyComments(userId, page, size);
    }

    public CurrentUserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }

    public String uploadProfileImage(Long userId, MultipartFile file) {
        return userService.uploadProfileImage(userId, file);
    }

    public void withdraw(Long userId) {
        userService.withdraw(userId);
    }
}
