package com.therapyCommunity_Vol1.backend.notification.service;

import com.therapyCommunity_Vol1.backend.notification.repository.NotificationRepository;
import com.therapyCommunity_Vol1.backend.notification.sse.SseEmitterRepository;
import com.therapyCommunity_Vol1.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationServiceTest {

    NotificationRepository notificationRepository;
    UserRepository userRepository;
    SseEmitterRepository sseEmitterRepository;

    NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationRepository = mock(NotificationRepository.class);
        userRepository = mock(UserRepository.class);
        sseEmitterRepository = new SseEmitterRepository();

        notificationService = new NotificationService(
                notificationRepository, userRepository, sseEmitterRepository
        );
    }

    @Test
    void subscribe_완료시_emitter가_정리된다() throws Exception {
        // Given
        Long userId = 1L;
        SseEmitter emitter = notificationService.subscribe(userId, "");

        assertThat(sseEmitterRepository.getEmitters(userId)).hasSize(1);

        // When — onCompletion 콜백 트리거
        invokeCallback(emitter, "completionCallback");

        // Then — emitter가 repository에서 제거됨
        assertThat(sseEmitterRepository.getEmitters(userId)).isEmpty();
    }

    @Test
    void subscribe_타임아웃시_emitter가_정리된다() throws Exception {
        // Given
        Long userId = 1L;
        SseEmitter emitter = notificationService.subscribe(userId, "");

        assertThat(sseEmitterRepository.getEmitters(userId)).hasSize(1);

        // When — onTimeout 콜백 트리거
        invokeCallback(emitter, "timeoutCallback");

        // Then — emitter가 repository에서 제거됨
        assertThat(sseEmitterRepository.getEmitters(userId)).isEmpty();
    }

    /**
     * ResponseBodyEmitter 내부의 DefaultCallback을 리플렉션으로 트리거한다.
     * Spring이 콜백을 호출하는 것과 동일한 효과를 검증용으로 재현한다.
     */
    private void invokeCallback(SseEmitter emitter, String callbackFieldName) throws Exception {
        Field field = ResponseBodyEmitter.class.getDeclaredField(callbackFieldName);
        field.setAccessible(true);
        Object callback = field.get(emitter);
        Method run = callback.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        run.invoke(callback);
    }
}
