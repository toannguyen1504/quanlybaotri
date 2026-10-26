package com.example.quanlybaotri.reporting.application;

import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.ticket.domain.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StatisticsReportService {
    private static final Set<TicketStatus> ACTIVE_SLA = EnumSet.of(
        TicketStatus.SUBMITTED,
        TicketStatus.ACCEPTED,
        TicketStatus.ASSIGNED,
        TicketStatus.IN_PROGRESS,
        TicketStatus.WAITING_PARTS
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final IdentityClient identities;
    private final AssetClient assets;
    private final OrganizationClient organizations;
    private final StatisticsReportCache cache;

    public StatisticsReportService(
        NamedParameterJdbcTemplate jdbc,
        IdentityClient identities,
        AssetClient assets,
        OrganizationClient organizations,
        StatisticsReportCache cache
    ) {
        this.jdbc = jdbc;
        this.identities = identities;
        this.assets = assets;
        this.organizations = organizations;
        this.cache = cache;
    }

    public StatisticsReport get(Filter filter) {
        return cache.getOrLoad(filter, () -> calculateReport(filter));
    }

    private StatisticsReport calculateReport(Filter filter) {
        Instant generatedAt = Instant.now();
        List<RawTicket> tickets = loadTickets(filter);
        if (filter.equipmentCategoryId() != null) {
            Map<UUID, AssetClient.EquipmentRef> equipment = resolveBatched(
                tickets.stream().map(RawTicket::equipmentId).distinct().toList(), assets::resolve
            );
            tickets = filterByEquipmentCategory(tickets, filter.equipmentCategoryId(), equipment);
        }
        return new StatisticsReport(generatedAt, tickets.size(), countQueues(tickets, generatedAt));
    }

    public Snapshot snapshot(Filter filter) {
        Instant generatedAt = Instant.now();
        List<RawTicket> tickets = loadTickets(filter);
        Map<UUID, AssetClient.EquipmentRef> equipment = resolveBatched(
            tickets.stream().map(RawTicket::equipmentId).distinct().toList(), assets::resolve
        );
        if (filter.equipmentCategoryId() != null) {
            tickets = filterByEquipmentCategory(tickets, filter.equipmentCategoryId(), equipment);
        }

        Map<UUID, IdentityClient.UserRef> users = resolveBatched(
            tickets.stream().map(RawTicket::assigneeId).filter(Objects::nonNull).distinct().toList(),
            identities::resolve
        );
        Map<UUID, OrganizationClient.DepartmentRef> sites = resolveBatched(
            tickets.stream().map(RawTicket::siteId).distinct().toList(), organizations::resolve
        );

        Map<TicketWorkQueue, Long> queues = countQueues(tickets, generatedAt);

        List<OperationalTicket> details = new ArrayList<>();
        for (RawTicket ticket : tickets) {
            EnumSet<TicketWorkQueue> matches = matchingQueues(ticket, generatedAt);
            AssetClient.EquipmentRef item = equipment.get(ticket.equipmentId());
            IdentityClient.UserRef assignee = ticket.assigneeId() == null ? null : users.get(ticket.assigneeId());
            OrganizationClient.DepartmentRef site = sites.get(ticket.siteId());
            details.add(new OperationalTicket(
                ticket.id(), ticket.code(), ticket.title(), ticket.customerId(),
                site == null ? null : site.customerName(), ticket.siteId(),
                site == null ? null : site.name(), ticket.equipmentId(),
                item == null ? null : item.assetCode(), item == null ? null : item.name(),
                item == null ? null : item.categoryName(), ticket.priority(), ticket.status(),
                ticket.assigneeId(), assignee == null ? null : assignee.fullName(),
                ticket.submittedAt(), ticket.responseDueAt(), ticket.resolutionDueAt(),
                ticket.costStatus() == null ? CostStatus.DRAFT : ticket.costStatus(),
                ticket.grandTotal() == null ? BigDecimal.ZERO : ticket.grandTotal(),
                List.copyOf(matches)
            ));
        }
        return new Snapshot(generatedAt, Collections.unmodifiableMap(queues), List.copyOf(details));
    }

    private List<RawTicket> filterByEquipmentCategory(
        List<RawTicket> tickets,
        UUID equipmentCategoryId,
        Map<UUID, AssetClient.EquipmentRef> equipment
    ) {
        return tickets.stream()
            .filter(ticket -> {
                AssetClient.EquipmentRef item = equipment.get(ticket.equipmentId());
                return item != null && equipmentCategoryId.equals(item.categoryId());
            })
            .toList();
    }

    private Map<TicketWorkQueue, Long> countQueues(List<RawTicket> tickets, Instant generatedAt) {
        EnumMap<TicketWorkQueue, Long> queues = new EnumMap<>(TicketWorkQueue.class);
        for (TicketWorkQueue queue : TicketWorkQueue.values()) queues.put(queue, 0L);
        for (RawTicket ticket : tickets) {
            matchingQueues(ticket, generatedAt).forEach(queue ->
                queues.compute(queue, (ignored, count) -> count == null ? 1 : count + 1)
            );
        }
        return Collections.unmodifiableMap(queues);
    }

    private List<RawTicket> loadTickets(Filter filter) {
        StringBuilder sql = new StringBuilder(
            "select t.id,t.code,t.title,t.customer_id,t.site_id,t.equipment_id,t.assignee_id," +
            "t.status,t.priority,t.submitted_at,t.response_due_at,t.resolution_due_at," +
            "c.status as cost_status,c.grand_total " +
            "from tickets t left join ticket_cost_summaries c on c.ticket_id=t.id " +
            "where t.status in ('SUBMITTED','ACCEPTED','ASSIGNED','IN_PROGRESS','WAITING_PARTS','RESOLVED')"
        );
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (filter.customerId() != null) {
            sql.append(" and t.customer_id=:customer");
            parameters.addValue("customer", filter.customerId());
        }
        if (filter.siteId() != null) {
            sql.append(" and t.site_id=:site");
            parameters.addValue("site", filter.siteId());
        }
        if (filter.equipmentId() != null) {
            sql.append(" and t.equipment_id=:equipment");
            parameters.addValue("equipment", filter.equipmentId());
        }
        if (filter.warrantyStatus() != null) {
            sql.append(" and t.warranty_status_at_creation=:warranty");
            parameters.addValue("warranty", filter.warrantyStatus().name());
        }
        if (filter.priority() != null) {
            sql.append(" and t.priority=:priority");
            parameters.addValue("priority", filter.priority().name());
        }
        if (filter.technicianId() != null) {
            sql.append(" and t.assignee_id=:technician");
            parameters.addValue("technician", filter.technicianId());
        }
        sql.append(" order by t.submitted_at desc");
        return jdbc.query(sql.toString(), parameters, (rs, index) -> new RawTicket(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("title"),
            rs.getObject("customer_id", UUID.class),
            rs.getObject("site_id", UUID.class),
            rs.getObject("equipment_id", UUID.class),
            rs.getObject("assignee_id", UUID.class),
            TicketStatus.valueOf(rs.getString("status")),
            TicketPriority.valueOf(rs.getString("priority")),
            rs.getTimestamp("submitted_at").toInstant(),
            rs.getTimestamp("response_due_at").toInstant(),
            rs.getTimestamp("resolution_due_at").toInstant(),
            rs.getString("cost_status") == null ? null : CostStatus.valueOf(rs.getString("cost_status")),
            rs.getBigDecimal("grand_total")
        ));
    }

    static EnumSet<TicketWorkQueue> matchingQueues(RawTicket ticket, Instant now) {
        EnumSet<TicketWorkQueue> result = EnumSet.noneOf(TicketWorkQueue.class);
        switch (ticket.status()) {
            case SUBMITTED -> result.add(TicketWorkQueue.AWAITING_ACCEPTANCE);
            case ACCEPTED -> result.add(TicketWorkQueue.AWAITING_ASSIGNMENT);
            case ASSIGNED -> result.add(TicketWorkQueue.AWAITING_START);
            case IN_PROGRESS -> result.add(TicketWorkQueue.IN_PROGRESS);
            case WAITING_PARTS -> result.add(TicketWorkQueue.WAITING_PARTS);
            case RESOLVED -> result.add(TicketWorkQueue.AWAITING_CLOSURE);
            default -> { }
        }
        if (ticket.status() == TicketStatus.SUBMITTED && !now.isBefore(ticket.responseDueAt())) {
            result.add(TicketWorkQueue.RESPONSE_OVERDUE);
        }
        if (ACTIVE_SLA.contains(ticket.status())) {
            if (!now.isBefore(ticket.resolutionDueAt())) {
                result.add(TicketWorkQueue.RESOLUTION_OVERDUE);
            } else if (dueSoon(ticket, now)) {
                result.add(TicketWorkQueue.RESOLUTION_DUE_SOON);
            }
        }
        if (ticket.status() == TicketStatus.RESOLVED) {
            if (ticket.costStatus() == CostStatus.QUOTED) result.add(TicketWorkQueue.COST_QUOTED);
            else if (ticket.costStatus() == null || ticket.costStatus() == CostStatus.DRAFT) {
                result.add(TicketWorkQueue.COST_DRAFT);
            }
        }
        return result;
    }

    static boolean dueSoon(RawTicket ticket, Instant now) {
        if (!now.isBefore(ticket.resolutionDueAt())) return false;
        long duration = Duration.between(ticket.submittedAt(), ticket.resolutionDueAt()).toMillis();
        Instant warningAt = ticket.submittedAt().plusMillis(Math.round(duration * 0.8));
        return !now.isBefore(warningAt);
    }

    private <T> Map<UUID, T> resolveBatched(
        List<UUID> ids,
        Function<Collection<UUID>, Map<UUID, T>> resolver
    ) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, T> result = new HashMap<>();
        for (int start = 0; start < ids.size(); start += 500) {
            result.putAll(resolver.apply(ids.subList(start, Math.min(start + 500, ids.size()))));
        }
        return result;
    }

    public record Filter(
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID equipmentCategoryId,
        WarrantyStatus warrantyStatus,
        TicketPriority priority,
        UUID technicianId
    ) {}

    public record StatisticsReport(
        Instant generatedAt,
        long openTotal,
        Map<TicketWorkQueue, Long> queues
    ) {}

    public record Snapshot(
        Instant generatedAt,
        Map<TicketWorkQueue, Long> queues,
        List<OperationalTicket> tickets
    ) {}

    public record OperationalTicket(
        UUID id,
        String code,
        String title,
        UUID customerId,
        String customerName,
        UUID siteId,
        String siteName,
        UUID equipmentId,
        String equipmentCode,
        String equipmentName,
        String equipmentCategoryName,
        TicketPriority priority,
        TicketStatus status,
        UUID assigneeId,
        String assigneeName,
        Instant submittedAt,
        Instant responseDueAt,
        Instant resolutionDueAt,
        CostStatus costStatus,
        BigDecimal grandTotal,
        List<TicketWorkQueue> queues
    ) {}

    record RawTicket(
        UUID id,
        String code,
        String title,
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID assigneeId,
        TicketStatus status,
        TicketPriority priority,
        Instant submittedAt,
        Instant responseDueAt,
        Instant resolutionDueAt,
        CostStatus costStatus,
        BigDecimal grandTotal
    ) {}
}
