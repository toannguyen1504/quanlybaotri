package com.example.quanlybaotri.cost.application;

import com.example.quanlybaotri.cost.domain.CostCategory;
import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.domain.WarrantyStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CostReportQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AssetClient assets;
    private final OrganizationClient organizations;

    public CostReportQueryService(
        NamedParameterJdbcTemplate jdbc,
        AssetClient assets,
        OrganizationClient organizations
    ) {
        this.jdbc = jdbc;
        this.assets = assets;
        this.organizations = organizations;
    }

    public Report load(
        Instant from,
        Instant to,
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID equipmentCategoryId,
        CostCategory category,
        CostStatus status
    ) {
        return load(
            from,
            to,
            customerId,
            siteId,
            equipmentId,
            equipmentCategoryId,
            null,
            null,
            null,
            category,
            status
        );
    }

    public Report load(
        Instant from,
        Instant to,
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID equipmentCategoryId,
        WarrantyStatus warrantyStatus,
        TicketPriority priority,
        UUID technicianId,
        CostCategory category,
        CostStatus status
    ) {
        Instant start = from == null ? Instant.now().minus(30, ChronoUnit.DAYS) : from;
        Instant end = to == null ? Instant.now() : to;
        StringBuilder sql = new StringBuilder(
            "select i.id,t.code,t.equipment_id,t.customer_id,t.site_id,s.status," +
            "i.category,i.description,i.quantity,i.unit,i.unit_cost,i.amount,i.incurred_at," +
            "i.vendor,i.reference_number from ticket_cost_items i " +
            "join tickets t on t.id=i.ticket_id " +
            "join ticket_cost_summaries s on s.ticket_id=t.id " +
            "where i.incurred_at between :from and :to"
        );
        MapSqlParameterSource parameters = new MapSqlParameterSource()
            .addValue("from", java.sql.Timestamp.from(start))
            .addValue("to", java.sql.Timestamp.from(end));
        if (customerId != null) {
            sql.append(" and t.customer_id=:customer");
            parameters.addValue("customer", customerId);
        }
        if (siteId != null) {
            sql.append(" and t.site_id=:site");
            parameters.addValue("site", siteId);
        }
        if (equipmentId != null) {
            sql.append(" and t.equipment_id=:equipment");
            parameters.addValue("equipment", equipmentId);
        }
        if (warrantyStatus != null) {
            sql.append(" and t.warranty_status_at_creation=:warranty");
            parameters.addValue("warranty", warrantyStatus.name());
        }
        if (priority != null) {
            sql.append(" and t.priority=:priority");
            parameters.addValue("priority", priority.name());
        }
        if (technicianId != null) {
            sql.append(" and t.assignee_id=:technician");
            parameters.addValue("technician", technicianId);
        }
        if (category != null) {
            sql.append(" and i.category=:category");
            parameters.addValue("category", category.name());
        }
        if (status != null) {
            sql.append(" and s.status=:status");
            parameters.addValue("status", status.name());
        }
        sql.append(" order by i.incurred_at desc");

        List<RowData> raw = jdbc.query(sql.toString(), parameters, (rs, row) -> new RowData(
            rs.getObject(1, UUID.class),
            rs.getString(2),
            rs.getObject(3, UUID.class),
            rs.getObject(4, UUID.class),
            rs.getObject(5, UUID.class),
            CostStatus.valueOf(rs.getString(6)),
            CostCategory.valueOf(rs.getString(7)),
            rs.getString(8),
            rs.getBigDecimal(9),
            rs.getString(10),
            rs.getBigDecimal(11),
            rs.getBigDecimal(12),
            rs.getTimestamp(13).toInstant(),
            rs.getString(14),
            rs.getString(15)
        ));
        Map<UUID, AssetClient.EquipmentRef> equipment = assets.resolve(
            raw.stream().map(RowData::equipmentId).toList()
        );
        Map<UUID, OrganizationClient.DepartmentRef> departments = organizations.resolve(
            raw.stream().map(RowData::siteId).toList()
        );
        List<Row> rows = raw.stream()
            .filter(item -> {
                var asset = equipment.get(item.equipmentId());
                return equipmentCategoryId == null ||
                    (asset != null && equipmentCategoryId.equals(asset.categoryId()));
            })
            .map(item -> {
                var asset = equipment.get(item.equipmentId());
                var department = departments.get(item.siteId());
                return new Row(
                    item.id(),
                    item.ticketCode(),
                    item.equipmentId(),
                    asset == null ? null : asset.assetCode(),
                    asset == null ? null : asset.name(),
                    item.customerId(),
                    department == null ? null : department.customerName(),
                    item.siteId(),
                    department == null ? null : department.name(),
                    item.status(),
                    item.category(),
                    item.description(),
                    item.quantity(),
                    item.unit(),
                    item.unitCost(),
                    item.amount(),
                    item.incurredAt(),
                    item.vendor(),
                    item.referenceNumber()
                );
            })
            .toList();
        EnumMap<CostCategory, BigDecimal> totals = new EnumMap<>(CostCategory.class);
        rows.forEach(row -> {
            if (row.amount() != null) totals.merge(row.category(), row.amount(), BigDecimal::add);
        });
        BigDecimal grandTotal = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Report(start, end, grandTotal, totals, rows);
    }

    private record RowData(
        UUID id,
        String ticketCode,
        UUID equipmentId,
        UUID customerId,
        UUID siteId,
        CostStatus status,
        CostCategory category,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitCost,
        BigDecimal amount,
        Instant incurredAt,
        String vendor,
        String referenceNumber
    ) {}

    public record Row(
        UUID id,
        String ticketCode,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        UUID customerId,
        String customerName,
        UUID siteId,
        String siteName,
        CostStatus status,
        CostCategory category,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitCost,
        BigDecimal amount,
        Instant incurredAt,
        String vendor,
        String referenceNumber
    ) {}

    public record Report(
        Instant from,
        Instant to,
        BigDecimal grandTotal,
        Map<CostCategory, BigDecimal> totals,
        List<Row> rows
    ) {}
}
