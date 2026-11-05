package com.example.quanlybaotri.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.quanlybaotri.ai.BaseIntegrationTest;
import com.example.quanlybaotri.ai.shared.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class AiRedisIntegrationTest extends BaseIntegrationTest {

    @Autowired RedisFixedWindowCounter counter;
    @Autowired ConversationStore conversations;
    @Autowired AiRateLimiter rateLimiter;
    @Autowired StringRedisTemplate redis;

    @Test
    void should_increment_and_repair_missing_expiry_atomically() {
        String key = "test:ai:rate:" + UUID.randomUUID();
        redis.opsForValue().set(key, "9");

        long value = counter.increment(key, Duration.ofMinutes(1));

        assertThat(value).isEqualTo(10);
        assertThat(redis.getExpire(key)).isPositive();
    }

    @Test
    void should_read_and_refresh_conversation_expiry_atomically() {
        UUID userId = UUID.randomUUID();
        ConversationStore.State created = conversations.create(userId);

        ConversationStore.State loaded = conversations.require(userId, created.id());

        assertThat(loaded.id()).isEqualTo(created.id());
        assertThat(redis.getExpire("ai:conversation:" + userId + ":" + created.id())).isPositive();
    }

    @Test
    void should_enforce_configured_rate_limit_boundary() {
        UUID userId = UUID.randomUUID();

        for (int request = 0; request < 10; request++) {
            rateLimiter.check(userId);
        }

        assertThatThrownBy(() -> rateLimiter.check(userId))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.code()).isEqualTo("AI_RATE_LIMITED"));
    }
}
