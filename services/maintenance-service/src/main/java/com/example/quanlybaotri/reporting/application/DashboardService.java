package com.example.quanlybaotri.reporting.application;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.ticket.domain.WarrantyStatus;
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
        return get(from, to, null, null, null);
    }

    public Dashboard get(Instant from, Instant to, UUID customerId, UUID siteId, WarrantyStatus warrantyStatus) {
        MapSqlParameterSource p = params(from, to);
        String where = where(p, customerId, siteId, warrantyStatus);
        Map<String, Long> byStatus = group(
            "select status as label,count(*) total from tickets" + where + " group by status",
            p
        );
        Map<String, Long> byPriority = group(
            "select priority as label,count(*) total from tickets" + where + " group by priority",
            p
        );
        Map<UUID, Long> technicianIds = uuidGroup(
            "select assignee_id as id,count(*) total from tickets" + where + " group by assignee_id",
            p
        );
        Map<UUID, Long> equipmentIds = uuidGroup(
            "select equipment_id as id,count(*) total from tickets" + where + " group by equipment_id",
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
        return new Dashboard(byStatus, byPriority, byTechnician, byCategory, sla(p, where));
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

    private String where(MapSqlParameterSource parameters, UUID customerId, UUID siteId, WarrantyStatus warrantyStatus) {
        StringBuilder sql = new StringBuilder(" where submitted_at between :from and :to");
        if (customerId != null) {
            sql.append(" and customer_id=:customerId");
            parameters.addValue("customerId", customerId);
        }
        if (siteId != null) {
            sql.append(" and site_id=:siteId");
            parameters.addValue("siteId", siteId);
        }
        if (warrantyStatus != null) {
            sql.append(" and warranty_status_at_creation=:warrantyStatus");
            parameters.addValue("warrantyStatus", warrantyStatus.name());
        }
        return sql.toString();
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

    private SlaSummary sla(SqlParameterSource p, String where) {
        return jdbc.queryForObject(
            "select count(*) filter(where coalesce(resolved_at,now())<=resolution_due_at) on_time,count(*) filter(where coalesce(resolved_at,now())>resolution_due_at) overdue from tickets" + where + " and status not in ('REJECTED','CANCELLED')",
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
