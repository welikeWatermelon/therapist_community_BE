package com.therapyCommunity_Vol1.backend.analytics.event;

import com.therapyCommunity_Vol1.backend.analytics.domain.EventTargetType;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEvent;
import com.therapyCommunity_Vol1.backend.analytics.domain.UserEventType;
import com.therapyCommunity_Vol1.backend.analytics.repository.UserEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserEventListenerTest {

    private UserEventRepository userEventRepository;
    private UserEventListener listener;

    @BeforeEach
    void setUp() {
        userEventRepository = mock(UserEventRepository.class);
        listener = new UserEventListener(userEventRepository);
    }

    @Test
    void payload_필드가_엔티티로_매핑되어_저장된다() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 4, 23, 9, 30);
        Map<String, Object> metadata = Map.of("reactionType", "LIKE");

        UserEventPayload payload = UserEventPayload.builder()
                .userId(1L)
                .eventType(UserEventType.POST_REACT)
                .targetType(EventTargetType.POST)
                .targetId(100L)
                .metadata(metadata)
                .occurredAt(occurredAt)
                .build();

        listener.handle(payload);

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventRepository).save(captor.capture());

        UserEvent saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getEventType()).isEqualTo(UserEventType.POST_REACT);
        assertThat(saved.getTargetType()).isEqualTo(EventTargetType.POST);
        assertThat(saved.getTargetId()).isEqualTo(100L);
        assertThat(saved.getMetadata()).containsEntry("reactionType", "LIKE");
        assertThat(saved.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void 저장_실패_시_예외를_흡수하고_요청_경로를_막지_않는다() {
        UserEventPayload payload = UserEventPayload.builder()
                .userId(1L)
                .eventType(UserEventType.POST_VIEW)
                .targetType(EventTargetType.POST)
                .targetId(100L)
                .occurredAt(LocalDateTime.now())
                .build();

        when(userEventRepository.save(org.mockito.ArgumentMatchers.any(UserEvent.class)))
                .thenThrow(new RuntimeException("DB down"));

        // 예외가 바깥으로 전파되면 요청 스레드/후속 이벤트에 영향.
        // listener는 자체적으로 try-catch로 흡수해야 함.
        listener.handle(payload);

        verify(userEventRepository).save(org.mockito.ArgumentMatchers.any(UserEvent.class));
    }

    @Test
    void metadata_가_null_이어도_정상_저장된다() {
        UserEventPayload payload = UserEventPayload.builder()
                .userId(1L)
                .eventType(UserEventType.POST_VIEW)
                .targetType(EventTargetType.POST)
                .targetId(100L)
                .metadata(null)
                .occurredAt(LocalDateTime.now())
                .build();

        listener.handle(payload);

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(userEventRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).isNull();
        verify(userEventRepository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 한번의_handle_호출은_한번만_save_한다() {
        UserEventPayload payload = UserEventPayload.builder()
                .userId(1L)
                .eventType(UserEventType.COMMENT_CREATE)
                .targetType(EventTargetType.COMMENT)
                .targetId(50L)
                .occurredAt(LocalDateTime.now())
                .build();

        listener.handle(payload);

        verify(userEventRepository, org.mockito.Mockito.times(1)).save(org.mockito.ArgumentMatchers.any());
        verify(userEventRepository, never()).flush();
    }
}
