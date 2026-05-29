package com.therapyCommunity_Vol1.backend.post.event;

import com.therapyCommunity_Vol1.backend.post.repository.TherapyPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostPopularityEventListener {

    private final TherapyPostRepository therapyPostRepository;

    @EventListener
    public void onPopularityRecalculation(PopularityRecalculationEvent event) {
        therapyPostRepository.recalculatePopularityScore(event.postId());
    }
}
