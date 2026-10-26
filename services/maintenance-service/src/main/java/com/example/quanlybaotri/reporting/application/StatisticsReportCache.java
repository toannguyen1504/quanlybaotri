package com.example.quanlybaotri.reporting.application;

import com.example.quanlybaotri.reporting.application.StatisticsReportService.Filter;
import com.example.quanlybaotri.reporting.application.StatisticsReportService.StatisticsReport;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class StatisticsReportCache {
    static final String GENERATION_KEY = "maintenance:report:statistics:generation";
    private static final String KEY_PREFIX = "maintenance:report:statistics:v1:";
    private static final Logger log = LoggerFactory.getLogger(StatisticsReportCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public StatisticsReportCache(
        StringRedisTemplate redis,
        ObjectMapper json,
        MeterRegistry meters,
        @Value("${app.reporting.statistics-cache.ttl:1m}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Statistics report cache TTL must be positive");
        }
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
        this.hits = meters.counter("maintenance.report.statistics.cache.hit");
        this.misses = meters.counter("maintenance.report.statistics.cache.miss");
        this.errors = meters.counter("maintenance.report.statistics.cache.error");
    }

    public StatisticsReport getOrLoad(Filter filter, Supplier<StatisticsReport> loader) {
        String generation;
        String key;
        try {
            generation = currentGeneration();
            key = key(generation, filter);
            String payload = redis.opsForValue().get(key);
            if (payload != null) {
                try {
                    StatisticsReport cached = json.readValue(payload, StatisticsReport.class);
                    hits.increment();
                    return cached;
                } catch (Exception exception) {
                    cacheError("Cannot deserialize cached statistics report", exception);
                    deleteQuietly(key);
                }
            }
        } catch (Exception exception) {
            cacheError("Cannot read statistics report cache", exception);
            misses.increment();
            return loader.get();
        }

        misses.increment();
        StatisticsReport report = loader.get();
        try {
            // Do not publish a snapshot under a newer generation if data changed while it was computed.
            if (Objects.equals(generation, currentGeneration())) {
                redis.opsForValue().set(key, json.writeValueAsString(report), ttl);
            }
        } catch (Exception exception) {
            cacheError("Cannot write statistics report cache", exception);
        }
        return report;
    }

    public void invalidate() {
        try {
            redis.opsForValue().increment(GENERATION_KEY);
        } catch (Exception exception) {
            cacheError("Cannot invalidate statistics report cache", exception);
        }
    }

    static String key(String generation, Filter filter) {
        return KEY_PREFIX + generation + ":" + hash(filter);
    }

    private String currentGeneration() {
        String generation = redis.opsForValue().get(GENERATION_KEY);
        return generation == null ? "0" : generation;
    }

    private static String hash(Filter filter) {
        String canonical = String.join(
            "|",
            value(filter.customerId()),
            value(filter.siteId()),
            value(filter.equipmentId()),
            value(filter.equipmentCategoryId()),
            value(filter.warrantyStatus()),
            value(filter.priority()),
            value(filter.technicianId())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "-" : value.toString();
    }

    private void deleteQuietly(String key) {
        try {
            redis.delete(key);
        } catch (Exception exception) {
            cacheError("Cannot remove invalid statistics report cache entry", exception);
        }
    }

    private void cacheError(String message, Exception exception) {
        errors.increment();
        log.warn("{}: {}", message, exception.getMessage());
    }
}
