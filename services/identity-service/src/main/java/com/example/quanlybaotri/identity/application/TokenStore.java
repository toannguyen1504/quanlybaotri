package com.example.quanlybaotri.identity.application;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class TokenStore {

    private final StringRedisTemplate redis;

    public TokenStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void revoke(String jti, Duration ttl) {
        if (jti == null || ttl.isNegative() || ttl.isZero()) return;
        try {
            redis.opsForValue().set("identity:jwt:revoked:" + jti, "1", ttl);
        } catch (RuntimeException ignored) {}
    }

    public boolean isRevoked(String jti) {
        if (jti == null) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey("identity:jwt:revoked:" + jti));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
