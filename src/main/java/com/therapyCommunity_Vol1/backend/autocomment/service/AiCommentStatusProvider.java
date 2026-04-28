package com.therapyCommunity_Vol1.backend.autocomment.service;

import com.therapyCommunity_Vol1.backend.autocomment.repository.PostAiCommentJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiCommentStatusProvider {

    private final PostAiCommentJobRepository jobRepository;

    public record AutoCommentStatus(String status, String sourceMode) {}

    public AutoCommentStatus getStatus(Long postId) {
        return jobRepository.findByPostId(postId)
                .map(job -> new AutoCommentStatus(
                        job.getStatus().name(),
                        job.getSourceMode() != null ? job.getSourceMode().name() : null))
                .orElse(new AutoCommentStatus("NOT_REQUESTED", null));
    }
}
