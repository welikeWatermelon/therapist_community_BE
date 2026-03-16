package com.therapyCommunity_Vol1.backend.scrap.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.scrap.TherapyPostScrapRepository;
import com.therapyCommunity_Vol1.backend.scrap.domain.TherapyPostScrap;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapListResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrapStatusResponse;
import com.therapyCommunity_Vol1.backend.scrap.dto.ScrappedPostResponse;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
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
public class ScrapService {

    private final TherapyPostScrapRepository scrapRepository;
    private final TherapyPostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public ScrapStatusResponse addScrap(Long currentUserId, Long postId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        TherapyPost post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        boolean alreadyExists = scrapRepository.existsByPostIdAndUserId(postId,currentUserId);

        if (!alreadyExists) {
            TherapyPostScrap scrap = TherapyPostScrap.create(post,user);
            scrapRepository.save(scrap);
        }

        return new ScrapStatusResponse(postId, true);
    }

    @Transactional
    public ScrapStatusResponse removeScrap(Long currentUserId, Long postId) {
        postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));

        scrapRepository.findByPostIdAndUserId(postId, currentUserId)
                .ifPresent(scrapRepository::delete);
        return new ScrapStatusResponse(postId, false);
    }

    public ScrapStatusResponse getScrapStatus(Long currentUserId, Long postId) {
        postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
        boolean scrapped = scrapRepository.existsByPostIdAndUserId(postId, currentUserId);

        return new ScrapStatusResponse(postId, scrapped);
    }

    public ScrapListResponse getMyScraps(Long currentUserId, int page, int size) {
        userRepository.findById(currentUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TherapyPostScrap> result = scrapRepository.findByUserIdAndPost_DeletedAtIsNull(currentUserId, pageable);

        List<ScrappedPostResponse> scraps = result.getContent()
                .stream()
                .map(ScrappedPostResponse::from)
                .toList();

        return new ScrapListResponse(
                scraps,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
