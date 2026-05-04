package com.therapyCommunity_Vol1.backend.notification.sse;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseEmitterRepositoryTest {

    private SseEmitterRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SseEmitterRepository(new SimpleMeterRegistry());
        repository.initMetrics();
    }

    @Test
    void 같은_유저에게_동시에_알림_2개가_와도_예외_없이_처리된다() throws InterruptedException {
        // given — 유저 1의 emitter 등록
        Long userId = 1L;
        SseEmitter emitter = new SseEmitter(30000L);
        repository.save(userId, emitter);

        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger errorCount = new AtomicInteger(0);

        // when — 두 스레드가 동시에 같은 유저에게 이벤트 캐싱
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                repository.cacheEvent(userId, "1_" + System.nanoTime(), "알림 데이터 1");
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                repository.cacheEvent(userId, "2_" + System.nanoTime(), "알림 데이터 2");
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executor.shutdown();

        // then — 예외 없이 두 이벤트 모두 캐싱됨
        assertThat(errorCount.get()).isZero();
        List<SseEmitterRepository.CachedEvent> events =
                repository.getEventsAfter(userId, "0_0");
        assertThat(events).hasSize(2);
    }

    @Test
    void 동시에_emitter_추가와_조회가_발생해도_ConcurrentModificationException이_없다() throws InterruptedException {
        // given
        Long userId = 1L;
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when — 5개 스레드는 emitter 추가, 5개 스레드는 emitter 조회
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        repository.save(userId, new SseEmitter(30000L));
                    } else {
                        Map<String, SseEmitter> emitters = repository.getEmitters(userId);
                        emitters.forEach((id, em) -> {
                            // 순회 중에도 예외 없어야 함
                        });
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(errorCount.get()).isZero();
    }

    @Test
    void 동시에_emitter_추가와_삭제가_발생해도_안전하다() throws InterruptedException {
        // given
        Long userId = 1L;
        String emitterId = repository.save(userId, new SseEmitter(30000L));

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when — 동시에 추가/삭제/조회
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    switch (index % 3) {
                        case 0 -> repository.save(userId, new SseEmitter(30000L));
                        case 1 -> repository.remove(userId, emitterId);
                        case 2 -> repository.getEmitters(userId);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(errorCount.get()).isZero();
    }

    @Test
    void 동시에_이벤트_캐싱과_조회가_발생해도_안전하다() throws InterruptedException {
        // given
        Long userId = 1L;
        repository.cacheEvent(userId, "1_0", "초기 데이터");

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // when — 동시에 캐싱 + getEventsAfter 조회
        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        repository.cacheEvent(userId, (index + 10) + "_" + System.nanoTime(), "데이터");
                    } else {
                        repository.getEventsAfter(userId, "0_0");
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // then
        assertThat(errorCount.get()).isZero();
    }

    // ── 다중 탭 처리 ──

    @Test
    void 같은_유저가_탭_3개를_열면_emitter가_3개_저장된다() {
        Long userId = 1L;

        String id1 = repository.save(userId, new SseEmitter(30000L));
        String id2 = repository.save(userId, new SseEmitter(30000L));
        String id3 = repository.save(userId, new SseEmitter(30000L));

        Map<String, SseEmitter> emitters = repository.getEmitters(userId);
        assertThat(emitters).hasSize(3);
        assertThat(emitters).containsKeys(id1, id2, id3);
    }

    @Test
    void 한_탭을_닫아도_나머지_탭의_emitter는_유지된다() {
        Long userId = 1L;

        String id1 = repository.save(userId, new SseEmitter(30000L));
        String id2 = repository.save(userId, new SseEmitter(30000L));
        String id3 = repository.save(userId, new SseEmitter(30000L));

        repository.remove(userId, id2);

        Map<String, SseEmitter> emitters = repository.getEmitters(userId);
        assertThat(emitters).hasSize(2);
        assertThat(emitters).containsKeys(id1, id3);
        assertThat(emitters).doesNotContainKey(id2);
    }

    @Test
    void 모든_탭을_닫으면_유저_항목이_제거된다() {
        Long userId = 1L;

        String id1 = repository.save(userId, new SseEmitter(30000L));
        String id2 = repository.save(userId, new SseEmitter(30000L));

        repository.remove(userId, id1);
        repository.remove(userId, id2);

        Map<String, SseEmitter> emitters = repository.getEmitters(userId);
        assertThat(emitters).isEmpty();
    }

    @Test
    void 다른_유저의_emitter는_서로_격리된다() {
        String userAEmitter = repository.save(1L, new SseEmitter(30000L));
        String userBEmitter = repository.save(2L, new SseEmitter(30000L));

        assertThat(repository.getEmitters(1L)).hasSize(1).containsKey(userAEmitter);
        assertThat(repository.getEmitters(2L)).hasSize(1).containsKey(userBEmitter);
    }

    // ── Last-Event-ID 재연결 ──

    @Test
    void 재연결시_lastEventId_이후의_이벤트만_반환한다() {
        Long userId = 1L;

        // 이벤트 3개 캐싱 (notificationId 기준: 10, 20, 30)
        repository.cacheEvent(userId, "10_1000", "알림1");
        repository.cacheEvent(userId, "20_2000", "알림2");
        repository.cacheEvent(userId, "30_3000", "알림3");

        // lastEventId=20 이후의 이벤트만 조회
        List<SseEmitterRepository.CachedEvent> missed = repository.getEventsAfter(userId, "20_2000");

        assertThat(missed).hasSize(1);
        assertThat(missed.get(0).eventId()).isEqualTo("30_3000");
        assertThat(missed.get(0).data()).isEqualTo("알림3");
    }

    @Test
    void 재연결_시나리오_연결끊김_후_알림2개_발생_후_재연결() {
        Long userId = 1L;

        // 1단계: 초기 연결에서 알림 1개 수신
        repository.cacheEvent(userId, "10_1000", "최초 알림");

        // 2단계: 연결 끊김 (emitter 제거, 캐시는 유지됨)
        String emitterId = repository.save(userId, new SseEmitter(30000L));
        repository.remove(userId, emitterId);
        assertThat(repository.getEmitters(userId)).isEmpty();

        // 3단계: 끊긴 동안 알림 2개 발생 (다른 경로를 통해 캐시에 추가)
        repository.cacheEvent(userId, "20_2000", "놓친 알림 1");
        repository.cacheEvent(userId, "30_3000", "놓친 알림 2");

        // 4단계: 재연결 — Last-Event-ID = "10_1000" (마지막으로 받은 이벤트)
        List<SseEmitterRepository.CachedEvent> missed =
                repository.getEventsAfter(userId, "10_1000");

        // then — 놓친 알림 2개만 반환
        assertThat(missed).hasSize(2);
        assertThat(missed.get(0).eventId()).isEqualTo("20_2000");
        assertThat(missed.get(0).data()).isEqualTo("놓친 알림 1");
        assertThat(missed.get(1).eventId()).isEqualTo("30_3000");
        assertThat(missed.get(1).data()).isEqualTo("놓친 알림 2");
    }

    @Test
    void lastEventId가_null이면_빈_리스트를_반환한다() {
        Long userId = 1L;
        repository.cacheEvent(userId, "10_1000", "알림1");

        List<SseEmitterRepository.CachedEvent> events = repository.getEventsAfter(userId, null);

        assertThat(events).isEmpty();
    }

    @Test
    void lastEventId가_비숫자이면_빈_리스트를_반환한다() {
        Long userId = 1L;
        repository.cacheEvent(userId, "10_1000", "알림1");

        List<SseEmitterRepository.CachedEvent> events = repository.getEventsAfter(userId, "invalid_id");

        assertThat(events).isEmpty();
    }

    @Test
    void 캐시가_없는_유저는_빈_리스트를_반환한다() {
        List<SseEmitterRepository.CachedEvent> events = repository.getEventsAfter(999L, "0_0");

        assertThat(events).isEmpty();
    }

    // ── emitter 수 상한 ──

    @Test
    void 유저당_emitter가_상한을_초과하면_가장_오래된_emitter가_complete된다() {
        Long userId = 1L;
        SseEmitter first = new SseEmitter(30000L);
        repository.save(userId, first);

        // 4개 더 추가 (총 5개 — 상한)
        for (int i = 0; i < 4; i++) {
            repository.save(userId, new SseEmitter(30000L));
        }
        assertThat(repository.getEmitters(userId)).hasSize(SseEmitterRepository.MAX_EMITTERS_PER_USER);

        // 6번째 추가 → 첫 번째가 evict
        repository.save(userId, new SseEmitter(30000L));
        assertThat(repository.getEmitters(userId)).hasSize(SseEmitterRepository.MAX_EMITTERS_PER_USER);
    }

    // ── eventId 유틸 ──

    @Test
    void createEventId는_notificationId_timestamp_형식을_반환한다() {
        String eventId = SseEmitterRepository.createEventId(42L);
        assertThat(eventId).startsWith("42_");
        assertThat(SseEmitterRepository.parseNotificationId(eventId)).isEqualTo(42L);
    }

    @Test
    void parseNotificationId는_잘못된_포맷에_null을_반환한다() {
        assertThat(SseEmitterRepository.parseNotificationId(null)).isNull();
        assertThat(SseEmitterRepository.parseNotificationId("")).isNull();
        assertThat(SseEmitterRepository.parseNotificationId("abc")).isNull();
        assertThat(SseEmitterRepository.parseNotificationId("abc_123")).isNull();
    }

    @Test
    void parseNotificationId는_여러_언더스코어도_처리한다() {
        assertThat(SseEmitterRepository.parseNotificationId("1_2_3")).isEqualTo(1L);
    }
}
