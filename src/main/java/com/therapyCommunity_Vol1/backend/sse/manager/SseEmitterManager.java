package com.therapyCommunity_Vol1.backend.sse.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SseEmitterManager {

    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; // 60분
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    // SSE 연결
    public SseEmitter createEmitter(Long userId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE connection completed for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE connection timed out for user: {}", userId);
            emitters.remove(userId);
        });

        emitter.onError(e -> {
            log.debug("SSE connection error for user: {}", userId);
            emitters.remove(userId);
        });

        // 연결 즉시 더미 이벤트 전송 (연결 확인용)
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected"));
        } catch (IOException e) {
            log.error("Failed to send connect event to user: {}", userId, e);
            emitters.remove(userId);
        }

        log.info("SSE emitter created for user: {}", userId);
        return emitter;
    }

    public void sendToUser(Long userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
                log.debug("SSE event '{}' sent to user: {}", eventName, userId);
            } catch (IOException e) {
                log.error("Failed to send SSE event to user: {}", userId, e);
                emitters.remove(userId);
            }
        }
    }

    public boolean isUserOnline(Long userId) {
        return emitters.containsKey(userId);
    }

    public void removeEmitter(Long userId) {
        SseEmitter emitter = emitters.remove(userId);
        if (emitter != null) {
            emitter.complete();
            log.debug("SSE emitter removed for user: {}", userId);
        }
    }

    public int getActiveConnectionCount() {
        return emitters.size();
    }
}
