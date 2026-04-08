package com.therapyCommunity_Vol1.backend.notification.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;

@Repository
public class SseEmitterRepository {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<CachedEvent>> eventCache = new ConcurrentHashMap<>();

    private static final int MAX_CACHE_SIZE = 50;
    private static final int CACHE_TTL_MINUTES = 30;

    public String save(Long userId, SseEmitter emitter) {
        String emitterId = userId + "_" + System.currentTimeMillis();
        emitters.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(emitterId, emitter);
        return emitterId;
    }

    public void remove(Long userId, String emitterId) {
        emitters.computeIfPresent(userId, (key, userEmitters) -> {
            userEmitters.remove(emitterId);
            return userEmitters.isEmpty() ? null : userEmitters;
        });
    }

    public Map<String, SseEmitter> getEmitters(Long userId) {
        ConcurrentHashMap<String, SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters != null ? userEmitters : Collections.emptyMap();
    }

    public void cacheEvent(Long userId, String eventId, Object data) {
        ConcurrentLinkedQueue<CachedEvent> queue =
                eventCache.computeIfAbsent(userId, k -> new ConcurrentLinkedQueue<>());
        queue.add(new CachedEvent(eventId, data, LocalDateTime.now()));

        while (queue.size() > MAX_CACHE_SIZE) {
            queue.poll();
        }
    }

    public List<CachedEvent> getEventsAfter(Long userId, String lastEventId) {
        ConcurrentLinkedQueue<CachedEvent> queue = eventCache.get(userId);
        if (queue == null || lastEventId == null || lastEventId.isBlank()) {
            return Collections.emptyList();
        }

        long lastNotificationId;
        try {
            lastNotificationId = Long.parseLong(lastEventId.split("_")[0]);
        } catch (NumberFormatException e) {
            return Collections.emptyList();
        }

        return queue.stream()
                .filter(event -> {
                    try {
                        long eventNotificationId = Long.parseLong(event.eventId().split("_")[0]);
                        return eventNotificationId > lastNotificationId;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                })
                .toList();
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanExpiredCache() {
        LocalDateTime expiry = LocalDateTime.now().minusMinutes(CACHE_TTL_MINUTES);
        eventCache.forEach((userId, queue) -> {
            queue.removeIf(event -> event.createdAt().isBefore(expiry));
            if (queue.isEmpty()) {
                eventCache.remove(userId, queue);
            }
        });
    }

    public record CachedEvent(String eventId, Object data, LocalDateTime createdAt) {}
}