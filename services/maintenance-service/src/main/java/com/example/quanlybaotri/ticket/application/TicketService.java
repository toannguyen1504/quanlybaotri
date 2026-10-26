package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.equipment.domain.EquipmentStatus;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.integration.CommandSource;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.api.TicketController.*;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.domain.CostStatus;
import com.example.quanlybaotri.cost.domain.TicketCostSummary;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.payment.client.PaymentClient.PaymentSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

    public enum TelegramStartOutcome {
        STARTED,
        ALREADY_HANDLED,
        NO_LONGER_ASSIGNED,
        NOT_STARTABLE,
        TECHNICIAN_INELIGIBLE,
    }

    public record TelegramStartResult(TelegramStartOutcome outcome, TicketStatus status) {}

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
    private final CostService costs;
    private final TicketCostSummaryRepository costSummaries;
    private final OrganizationClient organizations;

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
        AssetClient assets,
        CostService costs,
        TicketCostSummaryRepository costSummaries,
        OrganizationClient organizations
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
        this.costs = costs;
        this.costSummaries = costSummaries;
        this.organizations = organizations;
    }

    public Page<TicketView> list(
        Actor actor,
        String q,
        TicketStatus status,
        TicketPriority priority,
        Pageable pageable
    ) {
        return list(actor, q, status, priority, null, null, null, null, null,
            null, null, TicketListView.ALL, pageable);
    }

    public Page<TicketView> list(
        Actor actor,
        String q,
        TicketStatus status,
        TicketPriority priority,
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID equipmentCategoryId,
        UUID technicianId,
        WarrantyStatus warrantyStatus,
        TicketWorkQueue queue,
        TicketListView view,
        Pageable pageable
    ) {
        Set<UUID> categoryEquipmentIds = equipmentCategoryId == null
            ? null
            : assets.idsByCategory(equipmentCategoryId);
        Instant now = Instant.now();
        Page<MaintenanceTicket> page = transactions.execute(tx ->
            tickets.findAll(
                visibleTo(actor)
                    .and(filters(q, status, priority, customerId, siteId, equipmentId,
                        categoryEquipmentIds, technicianId, warrantyStatus))
                    .and(viewFilter(view, now))
                    .and(queueFilter(queue, now)),
                pageable
            )
        );
        Map<UUID, AssetClient.EquipmentRef> equipment = assets.resolve(
            page.getContent().stream().map(MaintenanceTicket::getEquipmentId).toList()
        );
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(userIds(page.getContent()));
        Map<UUID, OrganizationClient.DepartmentRef> departments = departmentMap(page.getContent());
        Map<UUID, TicketCostSummary> costMap = costMap(page.getContent());
        Map<UUID, PaymentSnapshot> paymentMap = costs.paymentSnapshots(page.getContent().stream().map(MaintenanceTicket::getId).toList());
        return page.map(t -> TicketView.from(t, equipment.get(t.getEquipmentId()), users, departments.get(t.getSiteId()), costMap.get(t.getId()), canSeeCosts(t,actor,costMap.get(t.getId())),paymentMap.get(t.getId())));
    }

    public List<TicketView> export(
        Actor actor,
        String q,
        TicketStatus status,
        TicketPriority priority,
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        UUID equipmentCategoryId,
        UUID technicianId,
        WarrantyStatus warrantyStatus,
        TicketWorkQueue queue,
        TicketListView view
    ) {
        Set<UUID> categoryEquipmentIds = equipmentCategoryId == null
            ? null
            : assets.idsByCategory(equipmentCategoryId);
        Instant now = Instant.now();
        List<MaintenanceTicket> result = transactions.execute(tx ->
            tickets.findAll(
                visibleTo(actor)
                    .and(filters(q, status, priority, customerId, siteId, equipmentId,
                        categoryEquipmentIds, technicianId, warrantyStatus))
                    .and(viewFilter(view, now))
                    .and(queueFilter(queue, now)),
                Sort.by(Sort.Direction.DESC, "submittedAt")
            )
        );
        Map<UUID, AssetClient.EquipmentRef> equipment = assets.resolve(
            result.stream().map(MaintenanceTicket::getEquipmentId).toList()
        );
        Map<UUID, IdentityClient.UserRef> users = identities.resolve(userIds(result));
        Map<UUID, OrganizationClient.DepartmentRef> departments = departmentMap(result);
        Map<UUID, TicketCostSummary> costMap = costMap(result);
        Map<UUID, PaymentSnapshot> paymentMap = costs.paymentSnapshots(result.stream().map(MaintenanceTicket::getId).toList());
        return result
            .stream()
            .map(t -> TicketView.from(t, equipment.get(t.getEquipmentId()), users, departments.get(t.getSiteId()), costMap.get(t.getId()), canSeeCosts(t,actor,costMap.get(t.getId())),paymentMap.get(t.getId())))
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
        return enrich(t, actor);
    }

    public TicketView create(CreateTicketRequest request, Actor actor) {
        AssetClient.EquipmentRef equipment = assets.get(request.equipmentId());
        if (
            equipment == null ||
            !equipment.active() ||
            equipment.status() == EquipmentStatus.RETIRED
        ) throw ApiException.conflict("Thiết bị không còn hoạt động");
        if (equipment.customerId() == null || equipment.siteId() == null) throw ApiException.conflict("Thiết bị chưa được gắn khách hàng và địa điểm");
        IdentityClient.UserRef requester;
        if (actor.customer()) {
            if (!actor.canAccess(equipment.customerId(), equipment.siteId())) throw ApiException.forbidden("Không có quyền tạo phiếu cho thiết bị này");
            requester = identities.get(actor.id());
        } else {
            if (!elevated(actor)) throw ApiException.forbidden("Chỉ quản lý được tạo phiếu thay khách hàng");
            if (request.requesterId() == null) throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,"REQUESTER_REQUIRED","Phải chọn người liên hệ khách hàng");
            requester = identities.get(request.requesterId());
        }
        if (requester == null || !requester.enabled() || !"CUSTOMER".equals(requester.accountType()) || !requester.roles().contains(RoleName.REQUESTER)) throw ApiException.conflict("Người yêu cầu không phải tài khoản khách hàng đang hoạt động");
        if (!requester.canAccess(equipment.customerId(), equipment.siteId())) throw ApiException.conflict("Người yêu cầu không có quyền tại địa điểm của thiết bị");
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
                    requester.id(),
                    actor.id(),
                    equipment.customerId(),
                    equipment.siteId(),
                    request.title().trim(),
                    request.description().trim(),
                    request.priority(),
                    now.plusSeconds(policy.getResponseMinutes() * 60L),
                    now.plusSeconds(policy.getResolutionMinutes() * 60L),
                    WarrantyStatus.valueOf(equipment.warrantyStatus()),
                    equipment.warrantyEndAt()
                )
            );
            eventService.record(
                created,
                actor.id(),
                "SUBMITTED",
                null,
                TicketStatus.SUBMITTED,
                "Phiếu được tạo",
                Map.of("priority", request.priority().name(),"customerId",equipment.customerId(),"siteId",equipment.siteId(),"warrantyStatus",equipment.warrantyStatus())
            );
            costs.createForTicket(created.getId());
            return created;
        });
        return enrich(ticket,equipment,identities.resolve(List.of(requester.id(),actor.id())),actor);
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

    public TicketView assign(UUID id, UUID technicianId, String reason, boolean outsideRegionConfirmed, Actor actor) {
        IdentityClient.UserRef technician = identities.get(technicianId);
        if (
            technician == null ||
            !technician.enabled() ||
            !technician.roles().contains(RoleName.TECHNICIAN)
        ) throw ApiException.conflict(
            "Tài khoản được chọn không phải kỹ thuật viên đang hoạt động"
        );
        MaintenanceTicket snapshot = transactions.execute(tx -> find(id));
        OrganizationClient.DepartmentRef site = organizations.resolve(List.of(snapshot.getSiteId())).get(snapshot.getSiteId());
        boolean outsideRegion = !technician.matchesRegion(site == null ? null : site.regionId());
        if (outsideRegion && !outsideRegionConfirmed) throw new ApiException(
            org.springframework.http.HttpStatus.CONFLICT,
            "OUTSIDE_TECHNICIAN_REGION",
            "Kỹ thuật viên không phụ trách khu vực của phiếu; cần xác nhận phân công ngoại lệ"
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
            Map<String,Object> metadata=new LinkedHashMap<>();
            metadata.put("technicianId",technician.id());
            metadata.put("outsideRegion",outsideRegion);
            metadata.put("technicianRegionScope",technician.regionScope());
            metadata.put("technicianRegionIds",technician.regionIds()==null?Set.of():technician.regionIds());
            if(site!=null&&site.regionId()!=null){
                metadata.put("ticketRegionId",site.regionId());
                metadata.put("ticketRegionName",site.regionName());
            }
            record(
                t,
                actor,
                "ASSIGNED",
                old,
                "Đã phân công cho " + technician.fullName(),
                metadata
            );
            return t;
        });
        return enrich(ticket, actor);
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

    public TelegramStartResult startFromTelegram(UUID id, UUID technicianId) {
        IdentityClient.UserRef technician;
        try {
            technician = identities.get(technicianId);
        } catch (ApiException failure) {
            if (failure.getStatus() == org.springframework.http.HttpStatus.NOT_FOUND) return new TelegramStartResult(
                TelegramStartOutcome.TECHNICIAN_INELIGIBLE,
                null
            );
            throw failure;
        }
        if (
            technician == null ||
            !technician.enabled() ||
            technician.roles() == null ||
            !technician.roles().contains(RoleName.TECHNICIAN)
        ) return new TelegramStartResult(TelegramStartOutcome.TECHNICIAN_INELIGIBLE, null);

        return transactions.execute(tx -> {
            MaintenanceTicket ticket = find(id);
            if (!Objects.equals(ticket.getAssigneeId(), technicianId)) return new TelegramStartResult(
                TelegramStartOutcome.NO_LONGER_ASSIGNED,
                ticket.getStatus()
            );
            if (
                Set.of(
                    TicketStatus.IN_PROGRESS,
                    TicketStatus.WAITING_PARTS,
                    TicketStatus.RESOLVED,
                    TicketStatus.CLOSED
                ).contains(ticket.getStatus())
            ) return new TelegramStartResult(
                TelegramStartOutcome.ALREADY_HANDLED,
                ticket.getStatus()
            );
            if (ticket.getStatus() != TicketStatus.ASSIGNED) return new TelegramStartResult(
                TelegramStartOutcome.NOT_STARTABLE,
                ticket.getStatus()
            );

            TicketStatus old = ticket.getStatus();
            ticket.start();
            eventService.record(
                ticket,
                technicianId,
                "STARTED",
                old,
                ticket.getStatus(),
                "Kỹ thuật viên bắt đầu xử lý từ Telegram",
                Map.of("equipmentUnderMaintenance", true, "source", "TELEGRAM")
            );
            return new TelegramStartResult(TelegramStartOutcome.STARTED, ticket.getStatus());
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
            costs.ensureReopenAllowed(t.getId());
            TicketStatus old = t.getStatus();
            t.reopen();
            costs.reopen(t.getId());
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

    public TicketView close(UUID id, String reason, FeedbackRequest feedback, Actor actor) {
        return mutate(id, actor, t -> {
            if (
                !t.getRequesterId().equals(actor.id()) && !elevated(actor)
            ) throw ApiException.forbidden("Không có quyền đóng phiếu");
            require(t, TicketStatus.RESOLVED);
            costs.ensureSettled(t.getId());
            boolean requester = t.getRequesterId().equals(actor.id());
            boolean feedbackProvided = feedback != null && feedback.provided();
            if (!requester) {
                requiredReason(reason);
                if (feedbackProvided) throw ApiException.forbidden(
                    "Chỉ người yêu cầu của phiếu được gửi phản hồi"
                );
            }
            TicketStatus old = t.getStatus();
            if (feedbackProvided) t.submitFeedback(feedback.rating(), feedback.comment());
            t.close();
            boolean under =
                tickets.countByEquipmentIdAndIdNotAndStatusNotIn(
                    t.getEquipmentId(),
                    t.getId(),
                    TERMINAL
                ) > 0;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("equipmentUnderMaintenance", under);
            metadata.put("feedbackProvided", feedbackProvided);
            if (feedbackProvided && feedback.rating() != null) metadata.put(
                "feedbackRating",
                feedback.rating()
            );
            record(
                t,
                actor,
                "CLOSED",
                old,
                reason == null || reason.isBlank() ? "Người yêu cầu xác nhận hoàn thành" : reason,
                metadata
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
            workLogs.flush();
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
            identities.get(actor.id())
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
        return enrich(t, actor);
    }

    private TicketView enrich(MaintenanceTicket t, Actor actor) {
        return enrich(t, assets.get(t.getEquipmentId()), identities.resolve(userIds(List.of(t))), actor);
    }

    private TicketView enrich(
        MaintenanceTicket t,
        AssetClient.EquipmentRef e,
        Map<UUID, IdentityClient.UserRef> users,
        Actor actor
    ) {
        var department = organizations.resolve(List.of(t.getSiteId())).get(t.getSiteId());
        TicketCostSummary cost = costSummaries.findById(t.getId()).orElse(null);
        PaymentSnapshot payment = costs.paymentSnapshots(List.of(t.getId())).get(t.getId());
        return TicketView.from(t, e, users, department, cost, canSeeCosts(t,actor,cost), payment);
    }

    private Map<UUID, TicketCostSummary> costMap(Collection<MaintenanceTicket> source) {
        Map<UUID, TicketCostSummary> result = new HashMap<>();
        costSummaries.findAllById(source.stream().map(MaintenanceTicket::getId).toList())
            .forEach(summary -> result.put(summary.getTicketId(), summary));
        return result;
    }

    private Map<UUID, OrganizationClient.DepartmentRef> departmentMap(Collection<MaintenanceTicket> source) {
        return organizations.resolve(source.stream().map(MaintenanceTicket::getSiteId).toList());
    }

    private Set<UUID> userIds(Collection<MaintenanceTicket> source) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (MaintenanceTicket t : source) {
            ids.add(t.getRequesterId());
            ids.add(t.getCreatedById());
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
        eventService.record(t, actor.id(), action, from, t.getStatus(), text, CommandSource.metadata(metadata));
    }

    private Specification<MaintenanceTicket> filters(
        String q,
        TicketStatus status,
        TicketPriority priority,
        UUID customerId,
        UUID siteId,
        UUID equipmentId,
        Set<UUID> categoryEquipmentIds,
        UUID technicianId,
        WarrantyStatus warrantyStatus
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
            if (customerId != null) p.add(cb.equal(root.get("customerId"), customerId));
            if (siteId != null) p.add(cb.equal(root.get("siteId"), siteId));
            if (equipmentId != null) p.add(cb.equal(root.get("equipmentId"), equipmentId));
            if (categoryEquipmentIds != null) {
                p.add(categoryEquipmentIds.isEmpty()
                    ? cb.disjunction()
                    : root.get("equipmentId").in(categoryEquipmentIds));
            }
            if (technicianId != null) p.add(cb.equal(root.get("assigneeId"), technicianId));
            if (warrantyStatus != null) p.add(cb.equal(root.get("warrantyStatusAtCreation"), warrantyStatus));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private Specification<MaintenanceTicket> queueFilter(TicketWorkQueue queue, Instant now) {
        if (queue == null) return (root, query, cb) -> cb.conjunction();
        return (root, query, cb) -> switch (queue) {
            case RESPONSE_OVERDUE -> cb.and(
                cb.equal(root.get("status"), TicketStatus.SUBMITTED),
                cb.lessThanOrEqualTo(root.get("responseDueAt"), now)
            );
            case RESOLUTION_DUE_SOON -> cb.and(
                root.get("status").in(
                    TicketStatus.SUBMITTED, TicketStatus.ACCEPTED, TicketStatus.ASSIGNED,
                    TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS
                ),
                cb.lessThanOrEqualTo(root.get("resolutionWarningAt"), now),
                cb.greaterThan(root.get("resolutionDueAt"), now)
            );
            case RESOLUTION_OVERDUE -> cb.and(
                root.get("status").in(
                    TicketStatus.SUBMITTED, TicketStatus.ACCEPTED, TicketStatus.ASSIGNED,
                    TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS
                ),
                cb.lessThanOrEqualTo(root.get("resolutionDueAt"), now)
            );
            case AWAITING_ACCEPTANCE -> cb.equal(root.get("status"), TicketStatus.SUBMITTED);
            case AWAITING_ASSIGNMENT -> cb.equal(root.get("status"), TicketStatus.ACCEPTED);
            case AWAITING_START -> cb.equal(root.get("status"), TicketStatus.ASSIGNED);
            case IN_PROGRESS -> cb.equal(root.get("status"), TicketStatus.IN_PROGRESS);
            case WAITING_PARTS -> cb.equal(root.get("status"), TicketStatus.WAITING_PARTS);
            case AWAITING_CLOSURE -> cb.equal(root.get("status"), TicketStatus.RESOLVED);
            case COST_DRAFT -> cb.and(
                cb.equal(root.get("status"), TicketStatus.RESOLVED),
                costStatus(root, query, cb, CostStatus.DRAFT, true)
            );
            case COST_QUOTED -> cb.and(
                cb.equal(root.get("status"), TicketStatus.RESOLVED),
                costStatus(root, query, cb, CostStatus.QUOTED, false)
            );
        };
    }

    private Predicate costStatus(
        Root<MaintenanceTicket> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        CostStatus status,
        boolean includeMissing
    ) {
        Subquery<UUID> matching = query.subquery(UUID.class);
        Root<TicketCostSummary> cost = matching.from(TicketCostSummary.class);
        matching.select(cost.get("ticketId")).where(
            cb.equal(cost.get("ticketId"), root.get("id")),
            cb.equal(cost.get("status"), status)
        );
        if (!includeMissing) return cb.exists(matching);
        Subquery<UUID> any = query.subquery(UUID.class);
        Root<TicketCostSummary> anyCost = any.from(TicketCostSummary.class);
        any.select(anyCost.get("ticketId")).where(cb.equal(anyCost.get("ticketId"), root.get("id")));
        return cb.or(cb.exists(matching), cb.not(cb.exists(any)));
    }

    private Specification<MaintenanceTicket> visibleTo(Actor actor) {
        return (root, query, cb) -> {
            if (elevated(actor)) return cb.conjunction();
            if (actor.has(RoleName.TECHNICIAN)) return cb.equal(root.get("assigneeId"), actor.id());
            if (actor.customer()) {
                Predicate customer=cb.equal(root.get("customerId"),actor.customerId());
                if ("ALL_SITES".equals(actor.siteScope())) return customer;
                return cb.and(customer,actor.siteIds().isEmpty()?cb.disjunction():root.get("siteId").in(actor.siteIds()));
            }
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
            !(actor.customer() && actor.canAccess(t.getCustomerId(),t.getSiteId())) &&
            !actor.id().equals(t.getAssigneeId())
        ) throw ApiException.forbidden("Không có quyền xem phiếu");
    }

    private boolean canSeeCosts(MaintenanceTicket ticket,Actor actor,TicketCostSummary cost){
        if(actor.customer()) return actor.id().equals(ticket.getRequesterId())&&actor.canAccess(ticket.getCustomerId(),ticket.getSiteId())&&cost!=null&&(cost.getStatus()==CostStatus.QUOTED||cost.getStatus()==CostStatus.ACCEPTED);
        return elevated(actor)||actor.id().equals(ticket.getAssigneeId());
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
