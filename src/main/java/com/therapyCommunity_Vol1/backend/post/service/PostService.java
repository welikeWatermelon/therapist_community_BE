package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.domain.Visibility;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.global.common.PagedResponse;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.scrap.repository.TherapyPostScrapRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final TherapyPostRepository therapyPostRepository;
    private final TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private final TherapyPostScrapRepository therapyPostScrapRepository;
    private final UserRepository userRepository;

    @Transactional
    public TherapyPostDetailResponse createPost(
            Long userId,
            CreateTherapyPostRequest request
    ) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        TherapyPost post = TherapyPost.create(
                request.getContent(),
                request.getTherapyArea(),
                request.getVisibility(),
                author
        );
        TherapyPost saved = therapyPostRepository.save(post);

        return TherapyPostDetailResponse.from(saved, userId, author.getRole());
    }

    public PagedResponse<TherapyPostSummaryResponse> getPosts(
            Long currentUserId,
            int page,
            int size,
            PostSortType sortType,
            PostSearchCondition condition
    ) {
        Page<TherapyPost> result;

        Sort sort = toSort(sortType);

        Pageable pageable = PageRequest.of(page, size, sort);

        if (condition.isEmpty()) {
            result = therapyPostRepository.findByDeletedAtIsNull(pageable);
        } else if (condition.hasKeyword()) {
            String keyword = condition.getEscapedKeyword().trim();
            result = therapyPostRepository.searchByKeyword(
                    keyword,
                    condition.getTherapyArea(),
                    condition.getPostType(),
                    pageable
            );
        } else {
            result = therapyPostRepository.searchByFilter(
                    condition.getTherapyArea(),
                    condition.getPostType(),
                    pageable
            );
        }

        List<Long> postIds = result.getContent().stream()
                .map(TherapyPost::getId)
                .toList();

        Set<Long> scrappedPostIds = (currentUserId != null && !postIds.isEmpty())
                ? therapyPostScrapRepository.findScrappedPostIdsByUserIdAndPostIdIn(currentUserId, postIds)
                : Collections.emptySet();

        List<TherapyPostSummaryResponse> posts = result.getContent()
                .stream()
                .map(post -> TherapyPostSummaryResponse.from(post, scrappedPostIds.contains(post.getId())))
                .toList();

        return PagedResponse.from(result, posts);
    }

    private Sort toSort(PostSortType sortType) {
        return switch (sortType) {
            case MOST_VIEWED -> Sort.by(
                    Sort.Order.desc("viewCount"),
                    Sort.Order.desc("id")
            );
            case LATEST -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            );
        };
    }

    @Transactional
    public TherapyPostDetailResponse getPostDetail(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId
    ) {
        TherapyPost post = getActivePost(postId);
        validateVisibility(post, currentUserId, currentUserRole);

        post.increaseViewCount();

        List<PostAttachmentResponse> attachments = therapyPostAttachmentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(PostAttachmentResponse::from)
                .toList();

        boolean isScrapped = therapyPostScrapRepository.existsByPostIdAndUserId(postId, currentUserId);

        return TherapyPostDetailResponse.from(post, attachments, currentUserId, currentUserRole, isScrapped);
    }

    @Transactional
    public TherapyPostDetailResponse updatePost(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId,
            UpdateTherapyPostRequest request
    ) {
        TherapyPost post = getActivePost(postId);
        validateAuthorOrAdmin(post, currentUserId, currentUserRole);

        post.update(
                request.getContent(),
                request.getTherapyArea(),
                request.getVisibility()
        );
        return TherapyPostDetailResponse.from(post, currentUserId, currentUserRole);
    }

    @Transactional
    public void deletePost(
            Long currentUserId,
            UserRole currentUserRole,
            Long postId
    ) {
        TherapyPost post = getActivePost(postId);
        validateAuthorOrAdmin(post, currentUserId, currentUserRole);

        post.softDelete();
    }


    private TherapyPost getActivePost(Long postId) {
        return therapyPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    private void validateVisibility(TherapyPost post, Long currentUserId, UserRole currentUserRole) {
        if (post.getVisibility() == Visibility.PRIVATE) {
            boolean isAdmin = currentUserRole == UserRole.ADMIN;
            boolean isAuthor = post.getAuthor().getId().equals(currentUserId);
            if (!isAdmin && !isAuthor) {
                throw new CustomException(ErrorCode.POST_NOT_FOUND);
            }
        }
    }

    private void validateAuthorOrAdmin(
            TherapyPost post,
            Long currentUserId,
            UserRole currentUserRole
    ) {
        boolean isAdmin = currentUserRole == UserRole.ADMIN;
        boolean isAuthor = post.getAuthor().getId().equals(currentUserId);

        if(!isAdmin && !isAuthor) {
            throw new CustomException(ErrorCode.POST_ACCESS_DENIED);
        }
    }
}
