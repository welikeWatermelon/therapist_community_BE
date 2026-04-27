package com.therapyCommunity_Vol1.backend.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AsyncConfigTest {

    private final AsyncConfig asyncConfig = new AsyncConfig();

    @Test
    void AsyncUncaughtExceptionHandler가_등록되어_있다() {
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();

        assertThat(handler).isNotNull();
    }

    @Test
    void 핸들러가_예외를_받아도_전파하지_않고_로그만_남긴다() throws NoSuchMethodException {
        // given
        AsyncUncaughtExceptionHandler handler = asyncConfig.getAsyncUncaughtExceptionHandler();
        RuntimeException testException = new RuntimeException("비동기 작업 실패");

        // when & then — 핸들러 자체가 예외를 던지지 않음
        assertThatCode(() -> handler.handleUncaughtException(
                testException,
                AsyncConfig.class.getMethod("notificationExecutor"),
                "testParam1", "testParam2"
        )).doesNotThrowAnyException();
    }
}
