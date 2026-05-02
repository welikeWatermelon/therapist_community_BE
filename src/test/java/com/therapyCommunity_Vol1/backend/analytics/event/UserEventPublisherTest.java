package com.therapyCommunity_Vol1.backend.analytics.event;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserEventPublisherTest {

    private ApplicationEventPublisher applicationEventPublisher;
    private UserEventPublisher publisher;

    @BeforeEach
    void setUp() {
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        publisher = new UserEventPublisher(applicationEventPublisher);
    }

    @Test
    void metadata_포함_publish_시_payload_필드가_채워진다() {
        Map<String, Object> metadata = Map.of("reactionType", "LIKE");
        LocalDateTime before = LocalDateTime.now();

        publisher.publish(1L, UserEventType.POST_REACT, EventTargetType.POST, 100L, metadata);

        ArgumentCaptor<UserEventPayload> captor = ArgumentCaptor.forClass(UserEventPayload.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        UserEventPayload payload = captor.getValue();
        assertThat(payload.getUserId()).isEqualTo(1L);
        assertThat(payload.getEventType()).isEqualTo(UserEventType.POST_REACT);
        assertThat(payload.getTargetType()).isEqualTo(EventTargetType.POST);
        assertThat(payload.getTargetId()).isEqualTo(100L);
        assertThat(payload.getMetadata()).containsEntry("reactionType", "LIKE");
        assertThat(payload.getOccurredAt()).isAfterOrEqualTo(before);
    }

    @Test
    void metadata_생략_publish_시_null_로_채워진다() {
        publisher.publish(2L, UserEventType.POST_VIEW, EventTargetType.POST, 200L);

        ArgumentCaptor<UserEventPayload> captor = ArgumentCaptor.forClass(UserEventPayload.class);
        verify(applicationEventPublisher).publishEvent(captor.capture());

        UserEventPayload payload = captor.getValue();
        assertThat(payload.getMetadata()).isNull();
        assertThat(payload.getEventType()).isEqualTo(UserEventType.POST_VIEW);
    }
}
