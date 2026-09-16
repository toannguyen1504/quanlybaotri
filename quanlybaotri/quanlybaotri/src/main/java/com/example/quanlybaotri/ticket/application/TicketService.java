package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.equipment.domain.Equipment;
import com.example.quanlybaotri.equipment.domain.EquipmentStatus;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.identity.domain.UserAccount;
import com.example.quanlybaotri.identity.persistence.UserRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.api.TicketController.*;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
    private static final Set<TicketStatus> TERMINAL = Set.of(TicketStatus.CLOSED, TicketStatus.REJECTED,
            TicketStatus.CANCELLED);
    private final TicketRepository tickets;
    private final EquipmentRepository equipment;
    private final SlaPolicyRepository sla;
    private final UserRepository users;
    private final TicketEventService eventService;
    private final TicketEventRepository events;
    private final AssignmentRepository assignments;
    private final WorkLogRepository workLogs;
    private final EntityManager em;

    public TicketService(TicketRepository tickets, EquipmentRepository equipment, SlaPolicyRepository sla,
            UserRepository users, TicketEventService eventService, TicketEventRepository events,
            AssignmentRepository assignments, WorkLogRepository workLogs, EntityManager em) {
        this.tickets = tickets;
        this.equipment = equipment;
        this.sla = sla;
        this.users = users;
        this.eventService = eventService;
        this.events = events;
        this.assignments = assignments;
        this.workLogs = workLogs;
        this.em = em;
    }

    @Transactional(readOnly = true)
    public Page<TicketView> list(UserAccount actor, String q, TicketStatus status, TicketPriority priority,
            Pageable pageable) {
        return list(actor, q, status, priority, TicketListView.ALL, pageable);
    }

    @Transactional(readOnly = true)
    public Page<TicketView> list(UserAccount actor, String q, TicketStatus status, TicketPriority priority,
            TicketListView view, Pageable pageable) {
        Specification<MaintenanceTicket> spec = visibleTo(actor).and(filters(q, status, priority))
                .and(viewFilter(view, Instant.now()));
        return tickets.findAll(spec, pageable).map(TicketView::from);
    }

    @Transactional(readOnly = true)
    public TicketSummaryView summary(UserAccount actor) {
        Instant now = Instant.now();
        Specification<MaintenanceTicket> visible = visibleTo(actor);
        return new TicketSummaryView(tickets.count(visible),
                tickets.count(visible.and(viewFilter(TicketListView.OPEN, now))),
                tickets.count(visible.and(viewFilter(TicketListView.DUE_SOON, now))),
                tickets.count(visible.and(viewFilter(TicketListView.OVERDUE, now))));
    }

    private Specification<MaintenanceTicket> filters(String q, TicketStatus status, TicketPriority priority) {
        return (root, query, cb) -> {
            var p = new ArrayList<Predicate>();
            if (q != null && !q.isBlank())
                p.add(cb.or(cb.like(cb.lower(root.get("code")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%"),
                        cb.like(cb.lower(root.get("title")), "%" + q.trim().toLowerCase(Locale.ROOT) + "%")));
            if (status != null)
                p.add(cb.equal(root.get("status"), status));
            if (priority != null)
                p.add(cb.equal(root.get("priority"), priority));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private Specification<MaintenanceTicket> visibleTo(UserAccount actor) {
        return (root, query, cb) -> {
            if (elevated(actor))
                return cb.conjunction();
            if (has(actor, RoleName.TECHNICIAN))
                return cb.equal(root.get("assignee").get("id"), actor.getId());
            return cb.equal(root.get("requester").get("id"), actor.getId());
        };
    }

    private Specification<MaintenanceTicket> viewFilter(TicketListView view, Instant now) {
        TicketListView selected = view == null ? TicketListView.ALL : view;
        return (root, query, cb) -> switch (selected) {
            case ALL -> cb.conjunction();
            case OPEN -> root.get("status").in(TERMINAL).not();
            case DUE_SOON -> cb.and(root.get("status").in(TERMINAL).not(), cb.isNull(root.get("resolvedAt")),
                    cb.lessThanOrEqualTo(root.get("resolutionWarningAt"), now),
                    cb.greaterThan(root.get("resolutionDueAt"), now));
            case OVERDUE -> cb.and(root.get("status").in(TERMINAL).not(), cb.isNull(root.get("resolvedAt")),
                    cb.lessThanOrEqualTo(root.get("resolutionDueAt"), now));
        };
    }

    @Transactional(readOnly = true)
    public TicketView get(UUID id, UserAccount actor) {
        MaintenanceTicket t = find(id);
        ensureVisible(t, actor);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView create(CreateTicketRequest r, UserAccount actor) {
        Equipment e = equipment.findById(r.equipmentId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị"));
        if (!e.isActive() || e.getStatus() == EquipmentStatus.RETIRED)
            throw ApiException.conflict("Thiết bị không còn hoạt động");
        SlaPolicy policy = sla.findByPriorityAndActiveTrue(r.priority())
                .orElseThrow(() -> ApiException.conflict("Chưa cấu hình SLA cho mức ưu tiên"));
        Instant now = Instant.now();
        long seq = ((Number) em.createNativeQuery("select nextval('ticket_number_seq')").getSingleResult()).longValue();
        String code = "BT-" + DateTimeFormatter.ofPattern("yyyy").withZone(ZoneOffset.UTC).format(now) + "-"
                + String.format("%06d", seq);
        MaintenanceTicket t = tickets.save(new MaintenanceTicket(code, e, actor, r.title().trim(),
                r.description().trim(), r.priority(), now.plusSeconds(policy.getResponseMinutes() * 60L),
                now.plusSeconds(policy.getResolutionMinutes() * 60L)));
        eventService.record(t, actor, "SUBMITTED", null, TicketStatus.SUBMITTED, "Phiếu được tạo",
                Map.of("priority", r.priority().name(), "chargeType", t.getChargeType().name()));
        return TicketView.from(t);
    }

    @Transactional
    public TicketView accept(UUID id, UserAccount actor) {
        MaintenanceTicket t = find(id);
        require(t, TicketStatus.SUBMITTED);
        TicketStatus old = t.getStatus();
        t.accept();
        eventService.record(t, actor, "ACCEPTED", old, t.getStatus(), "Quản lý đã tiếp nhận phiếu", null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView reject(UUID id, String reason, UserAccount actor) {
        MaintenanceTicket t = find(id);
        require(t, TicketStatus.SUBMITTED);
        TicketStatus old = t.getStatus();
        t.reject();
        eventService.record(t, actor, "REJECTED", old, t.getStatus(), requiredReason(reason), null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView cancel(UUID id, String reason, UserAccount actor) {
        MaintenanceTicket t = find(id);
        if (!t.getRequester().getId().equals(actor.getId()))
            throw ApiException.forbidden("Chỉ người tạo được hủy phiếu");
        require(t, TicketStatus.SUBMITTED);
        TicketStatus old = t.getStatus();
        t.cancel();
        eventService.record(t, actor, "CANCELLED", old, t.getStatus(), requiredReason(reason), null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView assign(UUID id, UUID technicianId, String reason, UserAccount actor) {
        MaintenanceTicket t = find(id);
        if (!Set.of(TicketStatus.ACCEPTED, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS)
                .contains(t.getStatus()))
            throw invalid(t);
        UserAccount tech = users.findWithRolesById(technicianId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kỹ thuật viên"));
        if (!has(tech, RoleName.TECHNICIAN))
            throw ApiException.conflict("Tài khoản được chọn không phải kỹ thuật viên");
        TicketStatus old = t.getStatus();
        assignments.findFirstByTicketIdAndUnassignedAtIsNullOrderByAssignedAtDesc(id)
                .ifPresent(TicketAssignment::unassign);
        t.assign(tech);
        assignments.save(new TicketAssignment(t, tech, actor, reason));
        eventService.record(t, actor, "ASSIGNED", old, t.getStatus(), "Đã phân công cho " + tech.getFullName(),
                Map.of("technicianId", tech.getId()));
        return TicketView.from(t);
    }

    @Transactional
    public TicketView start(UUID id, UserAccount actor) {
        MaintenanceTicket t = assigned(id, actor);
        require(t, TicketStatus.ASSIGNED);
        TicketStatus old = t.getStatus();
        t.start();
        t.getEquipment().status(EquipmentStatus.UNDER_MAINTENANCE);
        eventService.record(t, actor, "STARTED", old, t.getStatus(), "Kỹ thuật viên bắt đầu xử lý", null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView waitParts(UUID id, String reason, UserAccount actor) {
        MaintenanceTicket t = assigned(id, actor);
        require(t, TicketStatus.IN_PROGRESS);
        TicketStatus old = t.getStatus();
        t.waitParts();
        eventService.record(t, actor, "WAITING_PARTS", old, t.getStatus(), requiredReason(reason), null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView resume(UUID id, UserAccount actor) {
        MaintenanceTicket t = assigned(id, actor);
        require(t, TicketStatus.WAITING_PARTS);
        TicketStatus old = t.getStatus();
        t.start();
        eventService.record(t, actor, "RESUMED", old, t.getStatus(), "Tiếp tục xử lý phiếu", null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView resolve(UUID id, String summary, UserAccount actor) {
        MaintenanceTicket t = assigned(id, actor);
        require(t, TicketStatus.IN_PROGRESS);
        TicketStatus old = t.getStatus();
        t.resolve(requiredReason(summary));
        eventService.record(t, actor, "RESOLVED", old, t.getStatus(), summary, null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView reopen(UUID id, String reason, UserAccount actor) {
        MaintenanceTicket t = find(id);
        if (!t.getRequester().getId().equals(actor.getId()) && !elevated(actor))
            throw ApiException.forbidden("Không có quyền mở lại phiếu");
        require(t, TicketStatus.RESOLVED);
        TicketStatus old = t.getStatus();
        t.reopen();
        t.getEquipment().status(EquipmentStatus.UNDER_MAINTENANCE);
        eventService.record(t, actor, "REOPENED", old, t.getStatus(), requiredReason(reason), null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView close(UUID id, String reason, UserAccount actor) {
        MaintenanceTicket t = find(id);
        if (!t.getRequester().getId().equals(actor.getId()) && !elevated(actor))
            throw ApiException.forbidden("Không có quyền đóng phiếu");
        require(t, TicketStatus.RESOLVED);
        if (t.getChargeType() == TicketChargeType.PENDING)
            throw ApiException.conflict("Vui lòng xác định phiếu miễn phí hay trả phí trước khi đóng");
        if (elevated(actor) && !t.getRequester().getId().equals(actor.getId()))
            requiredReason(reason);
        TicketStatus old = t.getStatus();
        t.close();
        if (tickets.countByEquipmentIdAndIdNotAndStatusNotIn(t.getEquipment().getId(), t.getId(), TERMINAL) == 0)
            t.getEquipment().status(EquipmentStatus.ACTIVE);
        eventService.record(t, actor, "CLOSED", old, t.getStatus(),
                reason == null || reason.isBlank() ? "Người yêu cầu xác nhận hoàn thành" : reason, null);
        return TicketView.from(t);
    }

    @Transactional
    public TicketView changePriority(UUID id, TicketPriority priority, UserAccount actor) {
        MaintenanceTicket t = find(id);
        TicketPriority old = t.getPriority();
        SlaPolicy policy = sla.findByPriorityAndActiveTrue(priority)
                .orElseThrow(() -> ApiException.conflict("Chưa cấu hình SLA"));
        t.priority(priority);
        Instant base = t.getSubmittedAt();
        Instant response = base.plusSeconds(policy.getResponseMinutes() * 60L);
        Instant resolution = base.plusSeconds(policy.getResolutionMinutes() * 60L);
        t.updateSlaDeadlines(response, resolution);
        eventService.record(t, actor, "PRIORITY_CHANGED", t.getStatus(), t.getStatus(),
                "Đổi ưu tiên từ " + old + " sang " + priority,
                Map.of("responseDueAt", response.toString(), "resolutionDueAt", resolution.toString()));
        return TicketView.from(t);
    }

    @Transactional
    public TicketView changeChargeType(UUID id, TicketChargeType chargeType, UserAccount actor) {
        MaintenanceTicket t = find(id);
        require(t, TicketStatus.RESOLVED);
        boolean assignedTechnician = has(actor, RoleName.TECHNICIAN) && t.getAssignee() != null
                && t.getAssignee().getId().equals(actor.getId());
        if (!elevated(actor) && !assignedTechnician)
            throw ApiException.forbidden("Chỉ kỹ thuật viên phụ trách hoặc quản lý được xác định chi phí");
        if (chargeType == null || chargeType == TicketChargeType.PENDING)
            throw ApiException.conflict("Vui lòng chọn miễn phí hoặc trả phí");
        TicketChargeType old = t.getChargeType();
        t.chargeType(chargeType);
        eventService.record(t, actor, "CHARGE_TYPE_CHANGED", t.getStatus(), t.getStatus(),
                "Đổi hình thức chi phí từ " + old + " sang " + chargeType,
                Map.of("from", old.name(), "to", chargeType.name(), "chargeAmount", t.getChargeAmount()));
        return TicketView.from(t);
    }

    @Transactional
    public WorkLogView addWorkLog(UUID id, String content, int minutes, UserAccount actor) {
        MaintenanceTicket t = assigned(id, actor);
        if (!Set.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS).contains(t.getStatus()))
            throw invalid(t);
        WorkLog log = workLogs.save(new WorkLog(t, actor, content.trim(), minutes));
        eventService.record(t, actor, "WORK_LOG_ADDED", t.getStatus(), t.getStatus(), content,
                Map.of("minutesSpent", minutes, "workLogId", log.getId()));
        return WorkLogView.from(log);
    }

    @Transactional(readOnly = true)
    public java.util.List<WorkLogView> workLogs(UUID id, UserAccount actor) {
        MaintenanceTicket t = find(id);
        ensureVisible(t, actor);
        return workLogs.findByTicketIdOrderByCreatedAtDesc(id).stream().map(WorkLogView::from).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<EventView> timeline(UUID id, UserAccount actor) {
        MaintenanceTicket t = find(id);
        ensureVisible(t, actor);
        return events.findByTicketIdOrderByCreatedAtAsc(id).stream().map(EventView::from).toList();
    }

    private MaintenanceTicket assigned(UUID id, UserAccount actor) {
        MaintenanceTicket t = find(id);
        if (t.getAssignee() == null || !t.getAssignee().getId().equals(actor.getId()))
            throw ApiException.forbidden("Phiếu không được phân công cho bạn");
        return t;
    }

    private MaintenanceTicket find(UUID id) {
        return tickets.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
    }

    private void ensureVisible(MaintenanceTicket t, UserAccount u) {
        if (!elevated(u) && !t.getRequester().getId().equals(u.getId())
                && (t.getAssignee() == null || !t.getAssignee().getId().equals(u.getId())))
            throw ApiException.forbidden("Không có quyền xem phiếu");
    }

    private boolean elevated(UserAccount u) {
        return has(u, RoleName.ADMIN) || has(u, RoleName.MANAGER);
    }

    private boolean has(UserAccount u, RoleName r) {
        return u.getRoles().stream().anyMatch(x -> x.getName() == r);
    }

    private void require(MaintenanceTicket t, TicketStatus s) {
        if (t.getStatus() != s)
            throw invalid(t);
    }

    private ApiException invalid(MaintenanceTicket t) {
        return ApiException.conflict("Không thể thực hiện thao tác khi phiếu ở trạng thái " + t.getStatus());
    }

    private String requiredReason(String value) {
        if (value == null || value.isBlank())
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "REASON_REQUIRED",
                    "Vui lòng nhập lý do/nội dung");
        return value.trim();
    }
}
