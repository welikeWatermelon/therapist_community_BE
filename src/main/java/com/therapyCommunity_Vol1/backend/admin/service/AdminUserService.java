package com.therapyCommunity_Vol1.backend.admin.service;

import com.therapyCommunity_Vol1.backend.admin.dto.AdminUserDetailResponse;
import com.therapyCommunity_Vol1.backend.admin.dto.AdminUserListResponse;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.service.PostService;
import com.therapyCommunity_Vol1.backend.scrap.service.ScrapService;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.service.UserService;
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
public class AdminUserService {

    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final ScrapService scrapService;

    public PagedResponse<AdminUserListResponse> getUsers(String keyword, UserRole role, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<User> result = userService.searchUsers(keyword, role, pageable);
        List<AdminUserListResponse> items = result.getContent().stream()
                .map(AdminUserListResponse::from)
                .toList();
        return PagedResponse.from(result, items);
    }

    public AdminUserDetailResponse getUserDetail(Long userId) {
        User user = userService.findUserById(userId);
        return AdminUserDetailResponse.from(
                user,
                postService.countPostsByAuthor(userId),
                commentService.countCommentsByAuthor(userId),
                scrapService.countScrapsByUser(userId)
        );
    }

    @Transactional
    public AdminUserDetailResponse changeUserRole(Long adminUserId, Long targetUserId, UserRole newRole) {
        if (adminUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.ADMIN_CANNOT_CHANGE_OWN_ROLE);
        }

        User target = userService.findUserById(targetUserId);

        // 마지막 ADMIN 강등 방지
        if (target.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            long adminCount = userService.countByRole(UserRole.ADMIN);
            if (adminCount <= 1) {
                throw new CustomException(ErrorCode.ADMIN_LAST_ADMIN);
            }
        }

        userService.changeRole(targetUserId, newRole);
        return getUserDetail(targetUserId);
    }
}
