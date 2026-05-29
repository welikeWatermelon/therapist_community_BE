package com.therapyCommunity_Vol1.backend.post.service;

import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivePostFinder {

    private final TherapyPostRepository therapyPostRepository;

    public TherapyPost findOrThrow(Long postId) {
        return therapyPostRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.POST_NOT_FOUND));
    }

    public TherapyPost findOrNull(Long postId) {
        if (postId == null) return null;
        return therapyPostRepository.findByIdAndDeletedAtIsNull(postId).orElse(null);
    }
}
