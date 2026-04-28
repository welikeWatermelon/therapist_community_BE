package com.therapyCommunity_Vol1.backend.global.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * CallerRunsPolicy 발동 시 warn 로그 + Micrometer counter를 함께 기록.
 *
 * 큐가 포화되어 호출 스레드(보통 톰캣 워커)가 직접 task를 실행하면 API 응답 시간에
 * 그 task 실행 시간이 그대로 합산된다. 표준 CallerRunsPolicy는 이 발동을 silent로
 * 처리하므로 운영 단계에서 캡 튜닝 근거를 잃는다.
 *
 * 메트릭: async.executor.caller_runs (tag: executor=<풀 이름>)
 *   ex) /actuator/metrics/async.executor.caller_runs?tag=executor:analyticsExecutor
 *
 * 핸들러는 task를 실제로 실행해야 하므로 부모 클래스의 동작을 그대로 호출.
 */
@Slf4j
public class LoggingCallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy {

    private final String executorName;
    private final Counter counter;

    public LoggingCallerRunsPolicy(String executorName, MeterRegistry meterRegistry) {
        this.executorName = executorName;
        this.counter = Counter.builder("async.executor.caller_runs")
                .description("큐 포화로 호출 스레드가 직접 task를 실행한 횟수")
                .tag("executor", executorName)
                .register(meterRegistry);
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        counter.increment();
        log.warn("executor={} 큐 포화 — 호출 스레드가 task 직접 실행. activeCount={} queueSize={} maxQueueCapacity={}",
                executorName, e.getActiveCount(), e.getQueue().size(),
                e.getQueue().size() + e.getQueue().remainingCapacity());
        super.rejectedExecution(r, e);
    }
}
