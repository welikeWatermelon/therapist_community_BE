package com.therapyCommunity_Vol1.backend.autocomment.event;

import com.therapyCommunity_Vol1.backend.autocomment.config.AiCommentProperties;
import com.therapyCommunity_Vol1.backend.autocomment.domain.PostAiCommentJob;
import com.therapyCommunity_Vol1.backend.autocomment.repository.PostAiCommentJobRepository;
import com.therapyCommunity_Vol1.backend.post.domain.TherapyPost;
import com.therapyCommunity_Vol1.backend.post.event.PostCreatedEvent;
import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import com.therapyCommunity_Vol1.backend.user.domain.User;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostCreatedAutoCommentListener {

    private final PostAiCommentJobRepository jobRepository;
    private final TherapyPostRepository postRepository;
    private final UserRepository userRepository;
    private final AiCommentProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(PostCreatedEvent event) {
        if (!event.isRequestAutoComment()) return;

        TherapyPost post = postRepository.findById(event.getPostId()).orElse(null);
        User author = userRepository.findById(event.getAuthorId()).orElse(null);
        if (post == null || author == null) return;

        if (!properties.isEnabled() || properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            PostAiCommentJob failedJob = PostAiCommentJob.createFailed(
                    post, author, "FEATURE_DISABLED", "AI comment feature is disabled");
            jobRepository.save(failedJob);
            return;
        }

        PostAiCommentJob job = PostAiCommentJob.create(post, author);
        PostAiCommentJob savedJob = jobRepository.save(job);

        // AFTER_COMMIT 이벤트로 비동기 처리 시작
        eventPublisher.publishEvent(new AutoCommentRequestedEvent(savedJob.getId()));
    }
}
