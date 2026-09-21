package com.example.quanlybaotri.reporting.application;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import java.sql.Types;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final NamedParameterJdbcTemplate jdbc;
    private final IdentityClient identities;
    private final AssetClient assets;

    public DashboardService(
        NamedParameterJdbcTemplate jdbc,
        IdentityClient identities,
        AssetClient assets
    ) {
        this.jdbc = jdbc;
        this.identities = identities;
        this.assets = assets;
    }

    public Dashboard get(Instant from, Instant to) {
        MapSqlParameterSource p = params(from, to);
        Map<String, Long> byStatus = group(
            "select status as label,count(*) total from tickets where submitted_at between :from and :to group by status",
            p
        );
        Map<String, Long> byPriority = group(
            "select priority as label,count(*) total from tickets where submitted_at between :from and :to group by priority",
            p
        );
        Map<UUID, Long> technicianIds = uuidGroup(
            "select assignee_id as id,count(*) total from tickets where submitted_at between :from and :to group by assignee_id",
            p
        );
        Map<UUID, Long> equipmentIds = uuidGroup(
            "select equipment_id as id,count(*) total from tickets where submitted_at between :from and :to group by equipment_id",
            p
        );
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(technicianIds.keySet());
        Map<UUID, AssetClient.EquipmentRef> equipment = assets.resolve(equipmentIds.keySet());
        Map<String, Long> byTechnician = new LinkedHashMap<>();
        technicianIds.forEach((id, total) ->
            byTechnician.merge(
                id == null ? "Chưa phân công" : name(users.get(id), id),
                total,
                Long::sum
            )
        );
        Map<String, Long> byCategory = new LinkedHashMap<>();
        equipmentIds.forEach((id, total) -> {
            var item = equipment.get(id);
            byCategory.merge(item == null ? id.toString() : item.categoryName(), total, Long::sum);
        });
        return new Dashboard(byStatus, byPriority, byTechnician, byCategory, sla(p));
    }

    private String name(IdentityClient.UserRef user, UUID id) {
        return user == null ? id.toString() : user.fullName();
    }

    private MapSqlParameterSource params(Instant from, Instant to) {
        return new MapSqlParameterSource()
            .addValue(
                "from",
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE
            )
            .addValue(
                "to",
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                Types.TIMESTAMP_WITH_TIMEZONE
            );
    }

    private Map<String, Long> group(String sql, SqlParameterSource p) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(
            sql,
            p,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getString("label"), rs.getLong("total"))
        );
        return result;
    }

    private Map<UUID, Long> uuidGroup(String sql, SqlParameterSource p) {
        Map<UUID, Long> result = new LinkedHashMap<>();
        jdbc.query(
            sql,
            p,
            (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                result.put(rs.getObject("id", UUID.class), rs.getLong("total"))
        );
        return result;
    }

    private SlaSummary sla(SqlParameterSource p) {
        return jdbc.queryForObject(
            "select count(*) filter(where coalesce(resolved_at,now())<=resolution_due_at) on_time,count(*) filter(where coalesce(resolved_at,now())>resolution_due_at) overdue from tickets where submitted_at between :from and :to and status not in ('REJECTED','CANCELLED')",
            p,
            (rs, n) -> new SlaSummary(rs.getLong("on_time"), rs.getLong("overdue"))
        );
    }

    public record Dashboard(
        Map<String, Long> byStatus,
        Map<String, Long> byPriority,
        Map<String, Long> byTechnician,
        Map<String, Long> byCategory,
        SlaSummary sla
    ) {}

    public record SlaSummary(long onTime, long overdue) {}
}
