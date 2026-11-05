package com.example.quanlybaotri.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.quanlybaotri.BaseIntegrationTest;
import com.example.quanlybaotri.shared.api.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisFixedWindowCounterIntegrationTest extends BaseIntegrationTest {

    @Autowired RedisFixedWindowCounter counter;
    @Autowired ChatRateLimiter rateLimiter;
    @Autowired StringRedisTemplate redis;

    @Test
    void should_increment_and_repair_missing_expiry_atomically() {
        String key = "test:chat:rate:" + UUID.randomUUID();
        redis.opsForValue().set(key, "29");

        long value = counter.increment(key, Duration.ofMinutes(1));

        assertThat(value).isEqualTo(30);
        assertThat(redis.getExpire(key)).isPositive();
    }

    @Test
    void should_enforce_chat_rate_limit_boundary() {
        UUID userId = UUID.randomUUID();

        for (int message = 0; message < 30; message++) {
            rateLimiter.check(userId);
        }

        assertThatThrownBy(() -> rateLimiter.check(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("CHAT_RATE_LIMITED"));
    }
}
