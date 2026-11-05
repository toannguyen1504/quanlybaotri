package com.example.quanlybaotri.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

class LoginRateLimiterTest {

    @Test
    void should_fail_open_when_redis_is_unavailable() {
        RedisFixedWindowCounter counter = Mockito.mock(RedisFixedWindowCounter.class);
        when(counter.increment(anyString(), any())).thenThrow(new IllegalStateException("Redis unavailable"));
        LoginRateLimiter limiter = new LoginRateLimiter(Mockito.mock(StringRedisTemplate.class), counter);

        assertThat(limiter.googleIpBlocked("127.0.0.1")).isFalse();
        assertThatCode(() -> limiter.failure("user@example.com", "127.0.0.1")).doesNotThrowAnyException();
    }
}
