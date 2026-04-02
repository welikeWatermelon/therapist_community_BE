package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.PostSortType;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostAttachmentRepository;
import com.therapyCommunity_Vol1.backend.post.dto.*;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostService {

    private final TherapyPostRepository therapyPostRepository;
    private final TherapyPostAttachmentRepository therapyPostAttachmentRepository;
    private final UserRepository userRepository;

    @Transactional
    public TherapyPostDetailResponse createPost(
            Long userId,
            CreateTherapyPostRequest request
    ) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        TherapyPost post = TherapyPost.create(
                request.getTitle(),
                request.getContent(),
                request.getTherapyArea(),
                request.getAgeGroup(),
                author
        );
        TherapyPost saved = therapyPostRepository.save(post);

        return TherapyPostDetailResponse.from(saved, userId, author.getRole());
    }

    public PostListResponse getPosts(
            int page,
            int size,
            PostSortType sortType,
            PostSearchCondition condition
    ) {
        Page<TherapyPost> result;

        if (condition.isEmpty()) {
            Sort sort = toSort(sortType);
            Pageable pageable = PageRequest.of(page, size, sort);
            result = therapyPostRepository.findByDeletedAtIsNull(pageable);
        } else if (condition.hasKeyword()) {
            result = searchByKeyword(page, size, condition);
        } else {
            Sort sort = toSort(sortType);
            Pageable pageable = PageRequest.of(page, size, sort);
            result = therapyPostRepository.searchByFilter(
                    condition.getTherapyArea(),
                    condition.getPostType(),
                    pageable
            );
        }

        List<TherapyPostSummaryResponse> posts = result.getContent()
                .stream()
                .map(TherapyPostSummaryResponse::from)
                .toList();

        return new PostListResponse(
                posts,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private Page<TherapyPost> searchByKeyword(int page, int size, PostSearchCondition condition) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Long> idPage = therapyPostRepository.searchIdsByKeyword(
                condition.getKeyword(),
                condition.hasTherapyArea() ? condition.getTherapyArea().name() : null,
                condition.hasPostType() ? condition.getPostType().name() : null,
                pageable
        );

        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, idPage.getTotalElements());
        }

        Map<Long, TherapyPost> postMap = therapyPostRepository.findAllWithAuthorByIdIn(ids)
                .stream()
                .collect(Collectors.toMap(TherapyPost::getId, Function.identity()));

        List<TherapyPost> ordered = ids.stream()
                .map(postMap::get)
                .toList();

        return new PageImpl<>(ordered, pageable, idPage.getTotalElements());
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

        post.increaseViewCount();

        List<PostAttachmentResponse> attachments = therapyPostAttachmentRepository
                .findByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(PostAttachmentResponse::from)
                .toList();

        return TherapyPostDetailResponse.from(post, attachments, currentUserId, currentUserRole);
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
                request.getTitle(),
                request.getContent(),
                request.getTherapyArea(),
                request.getAgeGroup()
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
