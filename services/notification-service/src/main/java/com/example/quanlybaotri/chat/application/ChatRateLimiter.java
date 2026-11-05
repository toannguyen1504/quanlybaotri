package com.example.quanlybaotri.chat.application;

import com.example.quanlybaotri.shared.api.ApiException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {
    private final RedisFixedWindowCounter counter;
    public ChatRateLimiter(RedisFixedWindowCounter counter) { this.counter = counter; }

    public void check(UUID userId) {
        String key = "chat:rate:" + userId;
        try {
            long count = counter.increment(key, Duration.ofMinutes(1));
            if (count > 30) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "Bạn gửi tin quá nhanh, vui lòng thử lại sau");
            }
        } catch (ApiException limited) {
            throw limited;
        } catch (RuntimeException unavailable) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_RATE_LIMIT_UNAVAILABLE", "Chưa thể kiểm tra giới hạn gửi tin");
        }
    }
}
