package com.example.quanlybaotri.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.reporting.application.StatisticsReportService.Filter;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.StatisticsReport;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.TicketWorkQueue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class StatisticsReportCacheTest {
    private final Map<String, String> redisData = new HashMap<>();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private SimpleMeterRegistry meters;
    private StatisticsReportCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        meters = new SimpleMeterRegistry();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> redisData.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisData.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.increment(StatisticsReportCache.GENERATION_KEY)).thenAnswer(invocation -> {
            long next = Long.parseLong(redisData.getOrDefault(StatisticsReportCache.GENERATION_KEY, "0")) + 1;
            redisData.put(StatisticsReportCache.GENERATION_KEY, Long.toString(next));
            return next;
        });
        when(redis.delete(anyString())).thenAnswer(invocation -> redisData.remove(invocation.getArgument(0)) != null);
        cache = new StatisticsReportCache(redis, new ObjectMapper(), meters, Duration.ofMinutes(1));
    }

    @Test
    void cachesAReportForTheSameFilterWithTheConfiguredTtl() {
        Filter filter = filter(null);
        AtomicInteger loads = new AtomicInteger();

        StatisticsReport first = cache.getOrLoad(filter, () -> report(loads.incrementAndGet()));
        StatisticsReport second = cache.getOrLoad(filter, () -> report(loads.incrementAndGet()));

        assertThat(second).isEqualTo(first);
        assertThat(loads).hasValue(1);
        verify(values).set(anyString(), anyString(), eq(Duration.ofMinutes(1)));
        assertThat(meters.counter("maintenance.report.statistics.cache.miss").count()).isEqualTo(1);
        assertThat(meters.counter("maintenance.report.statistics.cache.hit").count()).isEqualTo(1);
    }

    @Test
    void separatesFiltersAndReloadsAfterInvalidation() {
        AtomicInteger loads = new AtomicInteger();
        Filter all = filter(null);
        Filter high = filter(TicketPriority.HIGH);

        cache.getOrLoad(all, () -> report(loads.incrementAndGet()));
        cache.getOrLoad(high, () -> report(loads.incrementAndGet()));
        cache.invalidate();
        cache.getOrLoad(all, () -> report(loads.incrementAndGet()));

        assertThat(loads).hasValue(3);
        assertThat(StatisticsReportCache.key("0", all)).isNotEqualTo(StatisticsReportCache.key("0", high));
        assertThat(redisData).containsEntry(StatisticsReportCache.GENERATION_KEY, "1");
    }

    @Test
    void fallsBackToTheLoaderWhenRedisIsUnavailable() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        StatisticsReport loaded = cache.getOrLoad(filter(null), () -> report(7));

        assertThat(loaded.openTotal()).isEqualTo(7);
        assertThat(meters.counter("maintenance.report.statistics.cache.error").count()).isEqualTo(1);
    }

    @Test
    void returnsTheReportWhenCacheWriteFails() {
        doThrow(new RedisConnectionFailureException("redis down"))
            .when(values).set(anyString(), anyString(), any(Duration.class));

        StatisticsReport loaded = cache.getOrLoad(filter(null), () -> report(8));

        assertThat(loaded.openTotal()).isEqualTo(8);
        assertThat(meters.counter("maintenance.report.statistics.cache.error").count()).isEqualTo(1);
    }

    @Test
    void doesNotFailTheWriteTransactionWhenInvalidationFails() {
        when(values.increment(StatisticsReportCache.GENERATION_KEY))
            .thenThrow(new RedisConnectionFailureException("redis down"));

        cache.invalidate();

        assertThat(meters.counter("maintenance.report.statistics.cache.error").count()).isEqualTo(1);
    }

    @Test
    void doesNotCacheAReportIfGenerationChangesWhileLoading() {
        cache.getOrLoad(filter(null), () -> {
            redisData.put(StatisticsReportCache.GENERATION_KEY, "1");
            return report(10);
        });

        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void replacesMalformedCachedJson() {
        Filter filter = filter(null);
        redisData.put(StatisticsReportCache.key("0", filter), "not-json");

        StatisticsReport loaded = cache.getOrLoad(filter, () -> report(9));

        assertThat(loaded.openTotal()).isEqualTo(9);
        assertThat(redisData.get(StatisticsReportCache.key("0", filter))).isNotEqualTo("not-json");
        assertThat(meters.counter("maintenance.report.statistics.cache.error").count()).isEqualTo(1);
    }

    private Filter filter(TicketPriority priority) {
        return new Filter(null, null, null, null, null, priority, null);
    }

    private StatisticsReport report(long count) {
        Map<TicketWorkQueue, Long> queues = new EnumMap<>(TicketWorkQueue.class);
        for (TicketWorkQueue queue : TicketWorkQueue.values()) queues.put(queue, 0L);
        queues.put(TicketWorkQueue.IN_PROGRESS, count);
        return new StatisticsReport(Instant.parse("2026-10-07T07:00:00Z"), count, queues);
    }
}
