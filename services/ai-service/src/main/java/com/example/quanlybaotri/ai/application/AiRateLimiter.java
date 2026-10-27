package com.example.quanlybaotri.ai.application;

import com.example.quanlybaotri.ai.config.AiProperties;
import com.example.quanlybaotri.ai.shared.ApiException;
import java.time.Duration;
import java.util.UUID;
import io.micrometer.core.instrument.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AiRateLimiter {
    private final StringRedisTemplate redis; private final AiProperties properties; private final Counter checks,limited;
    public AiRateLimiter(StringRedisTemplate redis,AiProperties properties,MeterRegistry meters){this.redis=redis;this.properties=properties;checks=meters.counter("ai.rate.checks");limited=meters.counter("ai.rate.limited");}
    public void check(UUID user){
        try{checks.increment();String key="ai:rate:"+user;Long count=redis.opsForValue().increment(key);if(count!=null&&count==1)redis.expire(key,Duration.ofMinutes(1));
            if(count!=null&&count>properties.getRatePerMinute()){limited.increment();throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_RATE_LIMITED","Bạn gửi yêu cầu quá nhanh, vui lòng thử lại sau");}
        }catch(ApiException e){throw e;}catch(RuntimeException e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"AI_RATE_LIMIT_UNAVAILABLE","Chưa thể kiểm tra giới hạn sử dụng trợ lý");}
    }
}
