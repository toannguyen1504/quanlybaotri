package com.example.quanlybaotri.identity.application;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private static final long LIMIT = 5;
    private static final long GOOGLE_LIMIT = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final StringRedisTemplate redis;

    public LoginRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    private String key(String username, String ip) {
        return "identity:login:" + username.toLowerCase() + ":" + ip;
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
            if (count != null && count == 1) redis.expire(key, WINDOW);
        } catch (RuntimeException ignored) {}
    }

    public void success(String username, String ip) {
        try {
            redis.delete(key(username, ip));
        } catch (RuntimeException ignored) {}
    }

    public boolean registerBlocked(String email, String ip) {
        boolean ipBlocked = hit("identity:register:ip:" + ip, LIMIT);
        boolean emailBlocked = hit("identity:register:email:" + normalize(email), LIMIT);
        return ipBlocked || emailBlocked;
    }

    public boolean googleIpBlocked(String ip) {
        return hit("identity:google:ip:" + ip, GOOGLE_LIMIT);
    }

    public boolean googleEmailBlocked(String email) {
        return hit("identity:google:email:" + normalize(email), GOOGLE_LIMIT);
    }

    private boolean hit(String key, long limit) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, WINDOW);
            return count != null && count > limit;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String normalize(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase();
    }
}
