package com.therapyCommunity_Vol1.backend.global.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * app.search.strategy 설정값을 애플리케이션 시작 시점에 검증한다.
 *
 * 허용값 이외의 값(오타 등)이 설정되면 명확한 에러 메시지와 함께
 * 시작을 중단하여, @ConditionalOnProperty 미매칭으로 인한
 * 불명확한 UnsatisfiedDependencyException을 방지한다.
 */
@Configuration
public class SearchStrategyValidation {

    private static final Set<String> ALLOWED_STRATEGIES = Set.of("gin", "pgvector");

    @Value("${app.search.strategy}")
    private String strategy;

    @PostConstruct
    public void validate() {
        if (!ALLOWED_STRATEGIES.contains(strategy)) {
            throw new IllegalStateException(
                    "잘못된 검색 전략: '%s'. 허용값: %s".formatted(strategy, ALLOWED_STRATEGIES));
        }
    }
}
