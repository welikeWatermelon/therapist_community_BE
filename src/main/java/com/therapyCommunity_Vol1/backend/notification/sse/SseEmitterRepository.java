package com.therapyCommunity_Vol1.backend.notification.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;

@Slf4j
@Repository
public class SseEmitterRepository {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentLinkedQueue<CachedEvent>> eventCache = new ConcurrentHashMap<>();
    private final AtomicLong emitterIdGenerator = new AtomicLong();
    private final MeterRegistry meterRegistry;
    private Counter cacheEvictedCounter;

    private static final int MAX_CACHE_SIZE = 50;
    private static final int CACHE_TTL_MINUTES = 30;
    static final int MAX_EMITTERS_PER_USER = 5;

    public SseEmitterRepository(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    void initMetrics() {
        Gauge.builder("sse.active.users", emitters, ConcurrentHashMap::size)
                .description("SSE 연결이 활성화된 유저 수")
                .register(meterRegistry);
        Gauge.builder("sse.cache.users", eventCache, ConcurrentHashMap::size)
                .description("이벤트 캐시가 존재하는 유저 수")
                .register(meterRegistry);
        this.cacheEvictedCounter = Counter.builder("sse.cache.evicted")
                .description("TTL 만료로 제거된 캐시 이벤트 수")
                .register(meterRegistry);
    }

    public String save(Long userId, SseEmitter emitter) {
        String emitterId = userId + "_" + emitterIdGenerator.incrementAndGet();
        emitters.compute(userId, (key, existing) -> {
            ConcurrentHashMap<String, SseEmitter> map = (existing != null) ? existing : new ConcurrentHashMap<>();
            if (map.size() >= MAX_EMITTERS_PER_USER) {
                map.entrySet().stream().findFirst().ifPresent(oldest -> {
                    log.info("emitter limit reached, evicting oldest: userId={}, emitterId={}", userId, oldest.getKey());
                    oldest.getValue().complete();
                    map.remove(oldest.getKey());
                });
            }
            map.put(emitterId, emitter);
            return map;
        });
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

    public static String createEventId(Long notificationId) {
        return notificationId + "_" + System.currentTimeMillis();
    }

    public static Long parseNotificationId(String eventId) {
        if (eventId == null || eventId.isBlank()) return null;
        try {
            return Long.parseLong(eventId.split("_")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public List<CachedEvent> getEventsAfter(Long userId, String lastEventId) {
        ConcurrentLinkedQueue<CachedEvent> queue = eventCache.get(userId);
        Long lastNid = parseNotificationId(lastEventId);
        if (queue == null || lastNid == null) {
            return Collections.emptyList();
        }

        return queue.stream()
                .filter(event -> {
                    Long eventNid = parseNotificationId(event.eventId());
                    return eventNid != null && eventNid > lastNid;
                })
                .toList();
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanExpiredCache() {
        LocalDateTime expiry = LocalDateTime.now().minusMinutes(CACHE_TTL_MINUTES);
        eventCache.forEach((userId, queue) -> {
            int before = queue.size();
            queue.removeIf(event -> event.createdAt().isBefore(expiry));
            int evicted = before - queue.size();
            if (evicted > 0 && cacheEvictedCounter != null) {
                cacheEvictedCounter.increment(evicted);
            }
            if (queue.isEmpty()) {
                eventCache.remove(userId, queue);
            }
        });
    }

    public record CachedEvent(String eventId, Object data, LocalDateTime createdAt) {}
}