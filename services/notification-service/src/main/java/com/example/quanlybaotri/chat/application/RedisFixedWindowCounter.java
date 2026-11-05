package com.example.quanlybaotri.chat.application;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisFixedWindowCounter {

    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
        """
        local current = redis.call('INCR', KEYS[1])
        if redis.call('PTTL', KEYS[1]) < 0 then
          redis.call('PEXPIRE', KEYS[1], ARGV[1])
        end
        return current
        """,
        Long.class
    );

    private final StringRedisTemplate redis;

    RedisFixedWindowCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    long increment(String key, Duration window) {
        Long value = redis.execute(
            INCREMENT,
            List.of(key),
            Long.toString(window.toMillis())
        );
        if (value == null) throw new IllegalStateException("Redis did not return a rate-limit value");
        return value;
    }
}
