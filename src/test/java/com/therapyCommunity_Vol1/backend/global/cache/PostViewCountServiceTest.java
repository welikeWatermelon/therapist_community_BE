package com.therapyCommunity_Vol1.backend.global.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PostViewCountServiceTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private PostViewCountService postViewCountService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        postViewCountService = new PostViewCountService(stringRedisTemplate);
    }

    @Test
    void 첫_조회시_true_반환() {
        when(valueOperations.setIfAbsent("post_view:1:10", "1", 1800, TimeUnit.SECONDS))
                .thenReturn(true);

        assertThat(postViewCountService.isFirstView(1L, 10L)).isTrue();
    }

    @Test
    void 중복_조회시_false_반환() {
        when(valueOperations.setIfAbsent("post_view:1:10", "1", 1800, TimeUnit.SECONDS))
                .thenReturn(false);

        assertThat(postViewCountService.isFirstView(1L, 10L)).isFalse();
    }

    @Test
    void Redis_장애시_true_반환_가용성_우선() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThat(postViewCountService.isFirstView(1L, 10L)).isTrue();
    }
}
