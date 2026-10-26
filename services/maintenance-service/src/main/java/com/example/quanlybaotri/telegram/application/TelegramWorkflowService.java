package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.cost.api.CostController.CostItemView;
import com.example.quanlybaotri.cost.api.CostController.CostView;
import com.example.quanlybaotri.cost.api.CostController.ManualCostRequest;
import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.integration.CommandSource;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.telegram.domain.TelegramCommandReceipt;
import com.example.quanlybaotri.telegram.persistence.TelegramCommandReceiptRepository;
import com.example.quanlybaotri.ticket.api.AttachmentController.AttachmentView;
import com.example.quanlybaotri.ticket.api.TicketController.TicketView;
import com.example.quanlybaotri.ticket.api.TicketController.WorkLogView;
import com.example.quanlybaotri.ticket.application.AttachmentService;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.AttachmentType;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.api.InternalTicketController.Authorization;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TelegramWorkflowService {
    private static final Set<TicketStatus> ACTIVE = EnumSet.of(
        TicketStatus.ASSIGNED,
        TicketStatus.IN_PROGRESS,
        TicketStatus.WAITING_PARTS,
        TicketStatus.RESOLVED
    );

    private final TicketService tickets;
    private final TicketRepository ticketRepository;
    private final CostService costs;
    private final AttachmentService attachments;
    private final IdentityClient identities;
    private final TelegramCommandReceiptRepository receipts;

    public TelegramWorkflowService(
        TicketService tickets,
        TicketRepository ticketRepository,
        CostService costs,
        AttachmentService attachments,
        IdentityClient identities,
        TelegramCommandReceiptRepository receipts
    ) {
        this.tickets = tickets;
        this.ticketRepository = ticketRepository;
        this.costs = costs;
        this.attachments = attachments;
        this.identities = identities;
        this.receipts = receipts;
    }

    public Page<TicketView> list(UUID technicianId, int page, int size) {
        Actor actor = technician(technicianId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(20, Math.max(1, size)));
        Page<com.example.quanlybaotri.ticket.domain.MaintenanceTicket> found =
            ticketRepository.findByAssigneeIdAndStatusInOrderBySubmittedAtDesc(
                technicianId,
                ACTIVE,
                pageable
            );
        List<TicketView> content = found.getContent().stream()
            .map(ticket -> tickets.get(ticket.getId(), actor))
            .toList();
        return new PageImpl<>(content, pageable, found.getTotalElements());
    }

    public TicketView get(UUID ticketId, UUID technicianId) {
        return tickets.get(ticketId, technician(technicianId));
    }

    public Authorization authorizeInventory(UUID ticketId, UUID technicianId) {
        technician(technicianId);
        var ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
        if (!Objects.equals(ticket.getAssigneeId(), technicianId)) throw ApiException.forbidden(
            "Phiếu không được phân công cho bạn"
        );
        if (!Set.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS).contains(ticket.getStatus()))
            throw ApiException.conflict("Không thể dùng linh kiện ở trạng thái hiện tại");
        return new Authorization(ticket.getId(), ticket.getCode(), ticket.getStatus(), ticket.getAssigneeId(), true);
    }

    public CostView costs(UUID ticketId, UUID technicianId) {
        return costs.get(ticketId, technician(technicianId));
    }

    public List<WorkLogView> workLogs(UUID ticketId, UUID technicianId) {
        return tickets.workLogs(ticketId, technician(technicianId));
    }

    public List<AttachmentView> attachments(UUID ticketId, UUID technicianId) {
        return attachments.list(ticketId, technician(technicianId));
    }

    @Transactional
    public TicketView start(UUID ticketId, UUID technicianId, String key) {
        Actor actor = technician(technicianId);
        return once(key, "START", () -> tickets.get(ticketId, actor),
            () -> tickets.start(ticketId, actor));
    }

    @Transactional
    public TicketView waitParts(UUID ticketId, UUID technicianId, String reason, String key) {
        Actor actor = technician(technicianId);
        return once(key, "WAIT_PARTS", () -> tickets.get(ticketId, actor),
            () -> tickets.waitParts(ticketId, reason, actor));
    }

    @Transactional
    public TicketView resume(UUID ticketId, UUID technicianId, String key) {
        Actor actor = technician(technicianId);
        return once(key, "RESUME", () -> tickets.get(ticketId, actor),
            () -> tickets.resume(ticketId, actor));
    }

    @Transactional
    public TicketView resolve(UUID ticketId, UUID technicianId, String summary, String key) {
        Actor actor = technician(technicianId);
        return once(key, "RESOLVE", () -> tickets.get(ticketId, actor),
            () -> tickets.resolve(ticketId, summary, actor));
    }

    @Transactional
    public WorkLogView addWorkLog(
        UUID ticketId,
        UUID technicianId,
        String content,
        int minutes,
        String key
    ) {
        Actor actor = technician(technicianId);
        TelegramCommandReceipt existing = receipt(key, "WORK_LOG");
        if (existing != null) return tickets.workLogs(ticketId, actor).stream()
            .filter(log -> Objects.equals(log.id(), existing.getResultId()))
            .findFirst()
            .orElseThrow(() -> ApiException.conflict("Nhật ký Telegram đã được xử lý"));
        return CommandSource.telegram(() -> {
            WorkLogView result = tickets.addWorkLog(ticketId, content, minutes, actor);
            receipts.getReferenceById(key).complete(result.id());
            return result;
        });
    }

    @Transactional
    public CostItemView addCost(
        UUID ticketId,
        UUID technicianId,
        ManualCostRequest request,
        String key
    ) {
        Actor actor = technician(technicianId);
        TelegramCommandReceipt existing = receipt(key, "ADD_COST");
        if (existing != null) return findCostItem(ticketId, actor, existing.getResultId());
        return CommandSource.telegram(() -> {
            CostItemView result = costs.add(ticketId, request, actor);
            receipts.getReferenceById(key).complete(result.id());
            return result;
        });
    }

    @Transactional
    public CostItemView updateCost(
        UUID ticketId,
        UUID itemId,
        UUID technicianId,
        ManualCostRequest request,
        String key
    ) {
        Actor actor = technician(technicianId);
        TelegramCommandReceipt existing = receipt(key, "UPDATE_COST");
        if (existing != null) return findCostItem(ticketId, actor, itemId);
        return CommandSource.telegram(() -> {
            CostItemView result = costs.update(ticketId, itemId, request, actor);
            receipts.getReferenceById(key).complete(result.id());
            return result;
        });
    }

    @Transactional
    public CostView deleteCost(UUID ticketId, UUID itemId, UUID technicianId, String key) {
        Actor actor = technician(technicianId);
        return once(key, "DELETE_COST", () -> costs.get(ticketId, actor), () -> {
            costs.delete(ticketId, itemId, actor);
            return costs.get(ticketId, actor);
        });
    }

    @Transactional
    public CostView quoteCosts(UUID ticketId, UUID technicianId, String key) {
        Actor actor = technician(technicianId);
        return once(key, "QUOTE_COSTS", () -> costs.get(ticketId, actor),
            () -> costs.quote(ticketId, actor));
    }

    @Transactional
    public AttachmentView upload(
        UUID ticketId,
        UUID technicianId,
        UUID costItemId,
        AttachmentType type,
        MultipartFile file,
        String key
    ) {
        Actor actor = technician(technicianId);
        TelegramCommandReceipt existing = receipt(key, "UPLOAD");
        if (existing != null) return attachments.list(ticketId, actor).stream()
            .filter(item -> Objects.equals(item.id(), existing.getResultId()))
            .findFirst()
            .orElseThrow(() -> ApiException.conflict("Tệp Telegram đã được xử lý"));
        return CommandSource.telegram(() -> {
            AttachmentView result = attachments.upload(ticketId, null, costItemId, type, file, actor);
            receipts.getReferenceById(key).complete(result.id());
            return result;
        });
    }

    @Transactional
    public void deleteAttachment(UUID ticketId, UUID attachmentId, UUID technicianId, String key) {
        Actor actor = technician(technicianId);
        if (receipt(key, "DELETE_ATTACHMENT") != null) return;
        CommandSource.telegram(() -> attachments.delete(attachmentId, actor));
    }

    private CostItemView findCostItem(UUID ticketId, Actor actor, UUID itemId) {
        return costs.get(ticketId, actor).items().stream()
            .filter(item -> Objects.equals(item.id(), itemId))
            .findFirst()
            .orElseThrow(() -> ApiException.conflict("Khoản chi phí Telegram đã được xử lý"));
    }

    private <T> T once(String key, String operation, Supplier<T> duplicate, Supplier<T> action) {
        if (receipt(key, operation) != null) return duplicate.get();
        return CommandSource.telegram(action);
    }

    private TelegramCommandReceipt receipt(String key, String operation) {
        if (key == null || key.isBlank() || key.length() > 128) throw ApiException.conflict(
            "Idempotency-Key không hợp lệ"
        );
        TelegramCommandReceipt existing = receipts.findById(key).orElse(null);
        if (existing != null) {
            if (!existing.getOperation().equals(operation)) throw ApiException.conflict(
                "Idempotency-Key đã được dùng cho thao tác khác"
            );
            return existing;
        }
        receipts.saveAndFlush(new TelegramCommandReceipt(key, operation));
        return null;
    }

    private Actor technician(UUID technicianId) {
        IdentityClient.UserRef user = identities.get(technicianId);
        if (user == null || !user.enabled() || user.roles() == null ||
            !user.roles().contains(RoleName.TECHNICIAN)) throw ApiException.forbidden(
                "Tài khoản không còn đủ quyền kỹ thuật viên"
            );
        return new Actor(
            user.id(),
            user.username(),
            user.fullName(),
            Set.of(RoleName.TECHNICIAN),
            null,
            user.accountType(),
            user.customerId(),
            user.siteScope(),
            user.siteIds() == null ? Set.of() : user.siteIds()
        );
    }
}
