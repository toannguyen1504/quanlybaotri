package com.example.quanlybaotri.reporting.application;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.sql.Types;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class DashboardService {
    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public DashboardService(NamedParameterJdbcTemplate j, StringRedisTemplate r, ObjectMapper o) {
        jdbc = j;
        redis = r;
        json = o;
    }

    public Dashboard get(Instant from, Instant to) {
        String version = "0";
        try {
            String v = redis.opsForValue().get("cache:dashboard:version");
            if (v != null)
                version = v;
        } catch (RuntimeException ignored) {
        }
        String key = "cache:dashboard:" + version + ":" + from + ":" + to;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null)
                return json.readValue(cached, Dashboard.class);
        } catch (Exception ignored) {
        }
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", OffsetDateTime.ofInstant(from, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("to", OffsetDateTime.ofInstant(to, ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
        Dashboard result = new Dashboard(group(
                "select status as label,count(*) as total from tickets where submitted_at between :from and :to group by status",
                p),
                group("select priority as label,count(*) as total from tickets where submitted_at between :from and :to group by priority",
                        p),
                group("select coalesce(u.full_name,'Chưa phân công') as label,count(*) as total from tickets t left join users u on u.id=t.assignee_id where t.submitted_at between :from and :to group by u.full_name",
                        p),
                group("select c.name as label,count(*) as total from tickets t join equipment e on e.id=t.equipment_id join equipment_categories c on c.id=e.category_id where t.submitted_at between :from and :to group by c.name",
                        p),
                sla(p));
        try {
            redis.opsForValue().set(key, json.writeValueAsString(result), 60, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        return result;
    }

    private Map<String, Long> group(String sql, SqlParameterSource p) {
        Map<String, Long> out = new LinkedHashMap<>();
        jdbc.query(sql, p, rs -> {
            out.put(rs.getString("label"), rs.getLong("total"));
        });
        return out;
    }

    private SlaSummary sla(SqlParameterSource p) {
        return jdbc.queryForObject(
                "select count(*) filter(where coalesce(resolved_at,now())<=resolution_due_at) as on_time,count(*) filter(where coalesce(resolved_at,now())>resolution_due_at) as overdue from tickets where submitted_at between :from and :to and status not in ('REJECTED','CANCELLED')",
                p, (rs, n) -> new SlaSummary(rs.getLong("on_time"), rs.getLong("overdue")));
    }

    public record Dashboard(Map<String, Long> byStatus, Map<String, Long> byPriority, Map<String, Long> byTechnician,
            Map<String, Long> byCategory, SlaSummary sla) {
    }

    public record SlaSummary(long onTime, long overdue) {
    }
}
