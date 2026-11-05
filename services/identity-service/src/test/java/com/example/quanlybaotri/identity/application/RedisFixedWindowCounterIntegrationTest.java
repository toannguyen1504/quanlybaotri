package com.example.quanlybaotri.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.BaseIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisFixedWindowCounterIntegrationTest extends BaseIntegrationTest {

    @Autowired RedisFixedWindowCounter counter;
    @Autowired LoginRateLimiter rateLimiter;
    @Autowired StringRedisTemplate redis;

    @Test
    void should_increment_and_repair_missing_expiry_atomically() {
        String key = "test:identity:rate:" + UUID.randomUUID();
        redis.opsForValue().set(key, "4");

        long value = counter.increment(key, Duration.ofMinutes(15));

        assertThat(value).isEqualTo(5);
        assertThat(redis.getExpire(key)).isPositive();
    }

    @Test
    void should_enforce_google_login_rate_limit_boundary() {
        String ip = "test-" + UUID.randomUUID();

        for (int request = 0; request < 20; request++) {
            assertThat(rateLimiter.googleIpBlocked(ip)).isFalse();
        }

        assertThat(rateLimiter.googleIpBlocked(ip)).isTrue();
    }
}
