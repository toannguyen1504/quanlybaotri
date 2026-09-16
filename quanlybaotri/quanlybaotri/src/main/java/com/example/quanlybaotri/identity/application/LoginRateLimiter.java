package com.example.quanlybaotri.identity.application;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final long LIMIT = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String username, String ip) {
        return "login:" + username.toLowerCase() + ":" + ip;
    }

    public boolean blocked(String username, String ip) {
        try {
            String value = redis.opsForValue().get(key(username, ip));
            return value != null && Long.parseLong(value) >= LIMIT;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public void failure(String username, String ip) {
        try {
            String key = key(username, ip);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1)
                redis.expire(key, WINDOW);
        } catch (RuntimeException ignored) {
        }
    }

    public void success(String username, String ip) {
        try {
            redis.delete(key(username, ip));
        } catch (RuntimeException ignored) {
        }
    }
}
