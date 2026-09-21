package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.equipment.domain.EquipmentStatus;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.api.TicketController.*;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TicketService {

    private static final Set<TicketStatus> TERMINAL = Set.of(
        TicketStatus.CLOSED,
        TicketStatus.REJECTED,
        TicketStatus.CANCELLED
    );
    private final TicketRepository tickets;
    private final SlaPolicyRepository sla;
    private final TicketEventService eventService;
    private final TicketEventRepository events;
    private final AssignmentRepository assignments;
    private final WorkLogRepository workLogs;
    private final EntityManager em;
    private final TransactionTemplate transactions;
    private final IdentityClient identities;
    private final AssetClient assets;

    public TicketService(
        TicketRepository tickets,
        SlaPolicyRepository sla,
        TicketEventService eventService,
        TicketEventRepository events,
        AssignmentRepository assignments,
        WorkLogRepository workLogs,
        EntityManager em,
        TransactionTemplate transactions,
        IdentityClient identities,
        AssetClient assets
    ) {
        this.tickets = tickets;
        this.sla = sla;
        this.eventService = eventService;
        this.events = events;
        this.assignments = assignments;
        this.workLogs = workLogs;
        this.em = em;
        this.transactions = transactions;
        this.identities = identities;
        this.assets = assets;
    }

    public Page<TicketView> list(
        Actor actor,
        String q,
        TicketStatus status,
        TicketPriority priority,
        Pageable pageable
    ) {
        return list(actor, q, status, priority, TicketListView.ALL, pageable);
    }

    public Page<TicketView> list(
        Actor actor,
        String q,
        TicketStatus status,
        TicketPriority priority,
        TicketListView view,
        Pageable pageable
    ) {
        Page<MaintenanceTicket> page = transactions.execute(tx ->
            tickets.findAll(
                visibleTo(actor)
                    .and(filters(q, status, priority))
                    .and(viewFilter(view, Instant.now())),
                pageable
            )
        );
        Map<UUID, AssetClient.EquipmentRef> equipment = assets.resolve(
            page.getContent().stream().map(MaintenanceTicket::getEquipmentId).toList()
        );
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(userIds(page.getContent()));
        return page.map(t -> TicketView.from(t, equipment.get(t.getEquipmentId()), users));
    }

    public List<TicketView> export(
        Actor actor,
        String q,
        TicketStatus status,
        TicketPriority priority,
        TicketListView view
    ) {
        List<MaintenanceTicket> result = transactions.execute(tx ->
            tickets.findAll(
                visibleTo(actor)
                    .and(filters(q, status, priority))
                    .and(viewFilter(view, Instant.now())),
                Sort.by(Sort.Direction.DESC, "submittedAt")
            )
        );
        Map<UUID, AssetClient.EquipmentRef> equipment = assets.resolve(
            result.stream().map(MaintenanceTicket::getEquipmentId).toList()
        );
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(userIds(result));
        return result
            .stream()
            .map(t -> TicketView.from(t, equipment.get(t.getEquipmentId()), users))
            .toList();
    }

    public TicketSummaryView summary(Actor actor) {
        Instant now = Instant.now();
        Specification<MaintenanceTicket> v = visibleTo(actor);
        return transactions.execute(tx ->
            new TicketSummaryView(
                tickets.count(v),
                tickets.count(v.and(viewFilter(TicketListView.OPEN, now))),
                tickets.count(v.and(viewFilter(TicketListView.DUE_SOON, now))),
                tickets.count(v.and(viewFilter(TicketListView.OVERDUE, now)))
            )
        );
    }

    public TicketView get(UUID id, Actor actor) {
        MaintenanceTicket t = transactions.execute(tx -> {
            MaintenanceTicket found = find(id);
            ensureVisible(found, actor);
            return found;
        });
        return enrich(t);
    }

    public TicketView create(CreateTicketRequest request, Actor actor) {
        AssetClient.EquipmentRef equipment = assets.get(request.equipmentId());
        if (
            equipment == null ||
            !equipment.active() ||
            equipment.status() == EquipmentStatus.RETIRED
        ) throw ApiException.conflict("Thiết bị không còn hoạt động");
        MaintenanceTicket ticket = transactions.execute(tx -> {
            SlaPolicy policy = sla
                .findByPriorityAndActiveTrue(request.priority())
                .orElseThrow(() -> ApiException.conflict("Chưa cấu hình SLA cho mức ưu tiên"));
            Instant now = Instant.now();
            long seq = (
                (Number) em
                    .createNativeQuery("select nextval('ticket_number_seq')")
                    .getSingleResult()
            ).longValue();
            String code =
                "BT-" +
                DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC).format(now) +
                "-" +
                String.format("%06d", seq);
            MaintenanceTicket created = tickets.save(
                new MaintenanceTicket(
                    code,
                    equipment.id(),
                    actor.id(),
                    request.title().trim(),
                    request.description().trim(),
                    request.priority(),
                    now.plusSeconds(policy.getResponseMinutes() * 60L),
                    now.plusSeconds(policy.getResolutionMinutes() * 60L)
                )
            );
            eventService.record(
                created,
                actor.id(),
                "SUBMITTED",
                null,
                TicketStatus.SUBMITTED,
                "Phiếu được tạo",
                Map.of(
                    "priority",
                    request.priority().name(),
                    "chargeType",
                    created.getChargeType().name()
                )
            );
            return created;
        });
        return enrich(
            ticket,
            equipment,
            Map.of(
                actor.id(),
                new IdentityClient.UserRef(
                    actor.id(),
                    actor.username(),
                    actor.fullName(),
                    true,
                    actor.roles()
                )
            )
        );
    }

    public TicketView accept(UUID id, Actor actor) {
        return mutate(id, actor, t -> {
            require(t, TicketStatus.SUBMITTED);
            TicketStatus old = t.getStatus();
            t.accept();
            record(t, actor, "ACCEPTED", old, "Quản lý đã tiếp nhận phiếu", null);
        });
    }

    public TicketView reject(UUID id, String reason, Actor actor) {
        return mutate(id, actor, t -> {
            require(t, TicketStatus.SUBMITTED);
            TicketStatus old = t.getStatus();
            t.reject();
            record(t, actor, "REJECTED", old, requiredReason(reason), null);
        });
    }

    public TicketView cancel(UUID id, String reason, Actor actor) {
        return mutate(id, actor, t -> {
            if (!t.getRequesterId().equals(actor.id())) throw ApiException.forbidden(
                "Chỉ người tạo được hủy phiếu"
            );
            require(t, TicketStatus.SUBMITTED);
            TicketStatus old = t.getStatus();
            t.cancel();
            record(t, actor, "CANCELLED", old, requiredReason(reason), null);
        });
    }

    public TicketView assign(UUID id, UUID technicianId, String reason, Actor actor) {
        IdentityClient.UserRef technician = identities.get(technicianId);
        if (
            technician == null ||
            !technician.enabled() ||
            !technician.roles().contains(RoleName.TECHNICIAN)
        ) throw ApiException.conflict(
            "Tài khoản được chọn không phải kỹ thuật viên đang hoạt động"
        );
        MaintenanceTicket ticket = transactions.execute(tx -> {
            MaintenanceTicket t = find(id);
            if (
                !Set.of(
                    TicketStatus.ACCEPTED,
                    TicketStatus.ASSIGNED,
                    TicketStatus.IN_PROGRESS,
                    TicketStatus.WAITING_PARTS
                ).contains(t.getStatus())
            ) throw invalid(t);
            TicketStatus old = t.getStatus();
            assignments
                .findFirstByTicketIdAndUnassignedAtIsNullOrderByAssignedAtDesc(id)
                .ifPresent(TicketAssignment::unassign);
            t.assign(technician.id());
            assignments.save(new TicketAssignment(t, technician.id(), actor.id(), reason));
            record(
                t,
                actor,
                "ASSIGNED",
                old,
                "Đã phân công cho " + technician.fullName(),
                Map.of("technicianId", technician.id())
            );
            return t;
        });
        return enrich(ticket);
    }

    public TicketView start(UUID id, Actor actor) {
        return mutate(id, actor, t -> {
            assigned(t, actor);
            require(t, TicketStatus.ASSIGNED);
            TicketStatus old = t.getStatus();
            t.start();
            record(
                t,
                actor,
                "STARTED",
                old,
                "Kỹ thuật viên bắt đầu xử lý",
                Map.of("equipmentUnderMaintenance", true)
            );
        });
    }

    public TicketView waitParts(UUID id, String reason, Actor actor) {
        return mutate(id, actor, t -> {
            assigned(t, actor);
            require(t, TicketStatus.IN_PROGRESS);
            TicketStatus old = t.getStatus();
            t.waitParts();
            record(t, actor, "WAITING_PARTS", old, requiredReason(reason), null);
        });
    }

    public TicketView resume(UUID id, Actor actor) {
        return mutate(id, actor, t -> {
            assigned(t, actor);
            require(t, TicketStatus.WAITING_PARTS);
            TicketStatus old = t.getStatus();
            t.start();
            record(
                t,
                actor,
                "RESUMED",
                old,
                "Tiếp tục xử lý phiếu",
                Map.of("equipmentUnderMaintenance", true)
            );
        });
    }

    public TicketView resolve(UUID id, String summary, Actor actor) {
        return mutate(id, actor, t -> {
            assigned(t, actor);
            require(t, TicketStatus.IN_PROGRESS);
            TicketStatus old = t.getStatus();
            String text = requiredReason(summary);
            t.resolve(text);
            record(t, actor, "RESOLVED", old, text, null);
        });
    }

    public TicketView reopen(UUID id, String reason, Actor actor) {
        return mutate(id, actor, t -> {
            if (
                !t.getRequesterId().equals(actor.id()) && !elevated(actor)
            ) throw ApiException.forbidden("Không có quyền mở lại phiếu");
            require(t, TicketStatus.RESOLVED);
            TicketStatus old = t.getStatus();
            t.reopen();
            record(
                t,
                actor,
                "REOPENED",
                old,
                requiredReason(reason),
                Map.of("equipmentUnderMaintenance", true)
            );
        });
    }

    public TicketView close(UUID id, String reason, Actor actor) {
        return mutate(id, actor, t -> {
            if (
                !t.getRequesterId().equals(actor.id()) && !elevated(actor)
            ) throw ApiException.forbidden("Không có quyền đóng phiếu");
            require(t, TicketStatus.RESOLVED);
            if (t.getChargeType() == TicketChargeType.PENDING) throw ApiException.conflict(
                "Vui lòng xác định phiếu miễn phí hay trả phí trước khi đóng"
            );
            if (elevated(actor) && !t.getRequesterId().equals(actor.id())) requiredReason(reason);
            TicketStatus old = t.getStatus();
            t.close();
            boolean under =
                tickets.countByEquipmentIdAndIdNotAndStatusNotIn(
                    t.getEquipmentId(),
                    t.getId(),
                    TERMINAL
                ) > 0;
            record(
                t,
                actor,
                "CLOSED",
                old,
                reason == null || reason.isBlank() ? "Người yêu cầu xác nhận hoàn thành" : reason,
                Map.of("equipmentUnderMaintenance", under)
            );
        });
    }

    public TicketView changePriority(UUID id, TicketPriority priority, Actor actor) {
        return mutate(id, actor, t -> {
            TicketPriority old = t.getPriority();
            SlaPolicy policy = sla
                .findByPriorityAndActiveTrue(priority)
                .orElseThrow(() -> ApiException.conflict("Chưa cấu hình SLA"));
            t.priority(priority);
            Instant response = t.getSubmittedAt().plusSeconds(policy.getResponseMinutes() * 60L),
                resolution = t.getSubmittedAt().plusSeconds(policy.getResolutionMinutes() * 60L);
            t.updateSlaDeadlines(response, resolution);
            record(
                t,
                actor,
                "PRIORITY_CHANGED",
                t.getStatus(),
                "Đổi ưu tiên từ " + old + " sang " + priority,
                Map.of(
                    "responseDueAt",
                    response.toString(),
                    "resolutionDueAt",
                    resolution.toString()
                )
            );
        });
    }

    public TicketView changeChargeType(UUID id, TicketChargeType chargeType, Actor actor) {
        return mutate(id, actor, t -> {
            require(t, TicketStatus.RESOLVED);
            boolean assigned =
                actor.has(RoleName.TECHNICIAN) && actor.id().equals(t.getAssigneeId());
            if (!elevated(actor) && !assigned) throw ApiException.forbidden(
                "Chỉ kỹ thuật viên phụ trách hoặc quản lý được xác định chi phí"
            );
            if (
                chargeType == null || chargeType == TicketChargeType.PENDING
            ) throw ApiException.conflict("Vui lòng chọn miễn phí hoặc trả phí");
            TicketChargeType old = t.getChargeType();
            t.chargeType(chargeType);
            record(
                t,
                actor,
                "CHARGE_TYPE_CHANGED",
                t.getStatus(),
                "Đổi hình thức chi phí từ " + old + " sang " + chargeType,
                Map.of(
                    "from",
                    old.name(),
                    "to",
                    chargeType.name(),
                    "chargeAmount",
                    t.getChargeAmount()
                )
            );
        });
    }

    public WorkLogView addWorkLog(UUID id, String content, int minutes, Actor actor) {
        WorkLog log = transactions.execute(tx -> {
            MaintenanceTicket t = find(id);
            assigned(t, actor);
            if (
                !Set.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS).contains(
                    t.getStatus()
                )
            ) throw invalid(t);
            WorkLog saved = workLogs.save(new WorkLog(t, actor.id(), content.trim(), minutes));
            record(
                t,
                actor,
                "WORK_LOG_ADDED",
                t.getStatus(),
                content,
                Map.of("minutesSpent", minutes, "workLogId", saved.getId())
            );
            return saved;
        });
        return WorkLogView.from(
            log,
            new IdentityClient.UserRef(
                actor.id(),
                actor.username(),
                actor.fullName(),
                true,
                actor.roles()
            )
        );
    }

    public List<WorkLogView> workLogs(UUID id, Actor actor) {
        List<WorkLog> list = transactions.execute(tx -> {
            MaintenanceTicket t = find(id);
            ensureVisible(t, actor);
            return workLogs.findByTicketIdOrderByCreatedAtDesc(id);
        });
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(
            list.stream().map(WorkLog::getTechnicianId).toList()
        );
        return list
            .stream()
            .map(w -> WorkLogView.from(w, users.get(w.getTechnicianId())))
            .toList();
    }

    public List<EventView> timeline(UUID id, Actor actor) {
        List<TicketEvent> list = transactions.execute(tx -> {
            MaintenanceTicket t = find(id);
            ensureVisible(t, actor);
            return events.findByTicketIdOrderByCreatedAtAsc(id);
        });
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(
            list.stream().map(TicketEvent::getActorId).filter(Objects::nonNull).toList()
        );
        return list
            .stream()
            .map(e -> EventView.from(e, users.get(e.getActorId())))
            .toList();
    }

    private TicketView mutate(
        UUID id,
        Actor actor,
        java.util.function.Consumer<MaintenanceTicket> command
    ) {
        MaintenanceTicket t = transactions.execute(tx -> {
            MaintenanceTicket found = find(id);
            command.accept(found);
            return found;
        });
        return enrich(t);
    }

    private TicketView enrich(MaintenanceTicket t) {
        return enrich(t, assets.get(t.getEquipmentId()), identities.resolve(userIds(List.of(t))));
    }

    private TicketView enrich(
        MaintenanceTicket t,
        AssetClient.EquipmentRef e,
        Map<UUID, IdentityClient.UserRef> users
    ) {
        return TicketView.from(t, e, users);
    }

    private Set<UUID> userIds(Collection<MaintenanceTicket> source) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (MaintenanceTicket t : source) {
            ids.add(t.getRequesterId());
            if (t.getAssigneeId() != null) ids.add(t.getAssigneeId());
        }
        return ids;
    }

    private void record(
        MaintenanceTicket t,
        Actor actor,
        String action,
        TicketStatus from,
        String text,
        Map<String, Object> metadata
    ) {
        eventService.record(t, actor.id(), action, from, t.getStatus(), text, metadata);
    }

    private Specification<MaintenanceTicket> filters(
        String q,
        TicketStatus status,
        TicketPriority priority
    ) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (q != null && !q.isBlank()) p.add(
                cb.or(
                    cb.like(
                        cb.lower(root.get("code")),
                        "%" + q.trim().toLowerCase(Locale.ROOT) + "%"
                    ),
                    cb.like(
                        cb.lower(root.get("title")),
                        "%" + q.trim().toLowerCase(Locale.ROOT) + "%"
                    )
                )
            );
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (priority != null) p.add(cb.equal(root.get("priority"), priority));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private Specification<MaintenanceTicket> visibleTo(Actor actor) {
        return (root, query, cb) -> {
            if (elevated(actor)) return cb.conjunction();
            if (actor.has(RoleName.TECHNICIAN)) return cb.equal(root.get("assigneeId"), actor.id());
            return cb.equal(root.get("requesterId"), actor.id());
        };
    }

    private Specification<MaintenanceTicket> viewFilter(TicketListView view, Instant now) {
        TicketListView selected = view == null ? TicketListView.ALL : view;
        return (root, query, cb) ->
            switch (selected) {
                case ALL -> cb.conjunction();
                case OPEN -> root.get("status").in(TERMINAL).not();
                case DUE_SOON -> cb.and(
                    root.get("status").in(TERMINAL).not(),
                    cb.isNull(root.get("resolvedAt")),
                    cb.lessThanOrEqualTo(root.get("resolutionWarningAt"), now),
                    cb.greaterThan(root.get("resolutionDueAt"), now)
                );
                case OVERDUE -> cb.and(
                    root.get("status").in(TERMINAL).not(),
                    cb.isNull(root.get("resolvedAt")),
                    cb.lessThanOrEqualTo(root.get("resolutionDueAt"), now)
                );
            };
    }

    private MaintenanceTicket find(UUID id) {
        return tickets
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
    }

    private void assigned(MaintenanceTicket t, Actor actor) {
        if (
            t.getAssigneeId() == null || !t.getAssigneeId().equals(actor.id())
        ) throw ApiException.forbidden("Phiếu không được phân công cho bạn");
    }

    private void ensureVisible(MaintenanceTicket t, Actor actor) {
        if (
            !elevated(actor) &&
            !t.getRequesterId().equals(actor.id()) &&
            !actor.id().equals(t.getAssigneeId())
        ) throw ApiException.forbidden("Không có quyền xem phiếu");
    }

    private boolean elevated(Actor actor) {
        return actor.has(RoleName.ADMIN) || actor.has(RoleName.MANAGER);
    }

    private void require(MaintenanceTicket t, TicketStatus status) {
        if (t.getStatus() != status) throw invalid(t);
    }

    private ApiException invalid(MaintenanceTicket t) {
        return ApiException.conflict(
            "Không thể thực hiện thao tác khi phiếu ở trạng thái " + t.getStatus()
        );
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank()) throw new ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "REASON_REQUIRED",
            "Vui lòng nhập lý do/nội dung"
        );
        return value.trim();
    }
}
