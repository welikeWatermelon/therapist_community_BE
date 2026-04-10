package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import com.therapyCommunity_Vol1.backend.autocomment.domain.ReviewStatus;
import com.therapyCommunity_Vol1.backend.autocomment.dto.AiCommentDraftResponse;
import com.therapyCommunity_Vol1.backend.autocomment.repository.PostAiCommentJobRepository;
import com.therapyCommunity_Vol1.backend.comment.dto.CreateCommentRequest;
import com.therapyCommunity_Vol1.backend.comment.service.CommentService;
import com.therapyCommunity_Vol1.backend.global.exception.CustomException;
import com.therapyCommunity_Vol1.backend.global.exception.ErrorCode;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.domain.UserRole;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiCommentReviewService {

    private final PostAiCommentJobRepository jobRepository;
    private final UserRepository userRepository;
    private final CommentService commentService;
    private final AiCommentProperties properties;

    public AiCommentDraftResponse getDraft(Long postId) {
        PostAiCommentJob job = jobRepository.findByPostId(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));
        return AiCommentDraftResponse.from(job);
    }

    @Transactional
    public AiCommentDraftResponse approve(Long postId, Long adminUserId) {
        PostAiCommentJob job = jobRepository.findByPostId(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (job.getReviewStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // AI 계정으로 댓글 생성
        User aiUser = userRepository.findByEmail(properties.getAiUserEmail())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        var comment = commentService.createComment(
                aiUser.getId(),
                UserRole.ADMIN,
                job.getPost().getId(),
                new CreateCommentRequest(job.getDraftComment(), null)
        );

        // comment 엔티티 연결을 위해 ID로 조회
        job.approve(admin, null); // comment 엔티티 연결은 생략 (ID만 있으면 충분)

        return AiCommentDraftResponse.from(job);
    }

    @Transactional
    public AiCommentDraftResponse reject(Long postId, Long adminUserId) {
        PostAiCommentJob job = jobRepository.findByPostId(postId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND));

        if (job.getReviewStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        job.reject(admin);
        return AiCommentDraftResponse.from(job);
    }
}
