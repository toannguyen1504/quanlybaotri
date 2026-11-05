package com.example.quanlybaotri.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.ai.config.AiProperties;
import com.example.quanlybaotri.ai.shared.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AiRateLimiterTest {

    @Test
    void should_fail_closed_when_redis_is_unavailable() {
        RedisFixedWindowCounter counter = Mockito.mock(RedisFixedWindowCounter.class);
        when(counter.increment(anyString(), any())).thenThrow(new IllegalStateException("Redis unavailable"));
        AiRateLimiter limiter = new AiRateLimiter(counter, new AiProperties(), new SimpleMeterRegistry());

        assertThatThrownBy(() -> limiter.check(java.util.UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_RATE_LIMIT_UNAVAILABLE"));
    }
}
