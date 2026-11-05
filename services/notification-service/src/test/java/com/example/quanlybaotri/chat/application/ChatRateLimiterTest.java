package com.example.quanlybaotri.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.quanlybaotri.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ChatRateLimiterTest {

    @Test
    void should_fail_closed_when_redis_is_unavailable() {
        RedisFixedWindowCounter counter = Mockito.mock(RedisFixedWindowCounter.class);
        when(counter.increment(anyString(), any())).thenThrow(new IllegalStateException("Redis unavailable"));
        ChatRateLimiter limiter = new ChatRateLimiter(counter);

        assertThatThrownBy(() -> limiter.check(java.util.UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("CHAT_RATE_LIMIT_UNAVAILABLE"));
    }
}
