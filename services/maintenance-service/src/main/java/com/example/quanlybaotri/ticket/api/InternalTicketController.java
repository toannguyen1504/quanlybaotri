package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/tickets")
public class InternalTicketController {

    private final TicketRepository tickets;
    private final JwtDecoder decoder;
    private final TicketService service;

    public InternalTicketController(
        TicketRepository tickets,
        JwtDecoder decoder,
        TicketService service
    ) {
        this.tickets = tickets;
        this.decoder = decoder;
        this.service = service;
    }

    @PostMapping("/{id}/telegram-start")
    @PreAuthorize("authentication.name == 'notification-service'")
    public TicketService.TelegramStartResult startFromTelegram(
        @PathVariable UUID id,
        @Valid @RequestBody TelegramStartRequest request
    ) {
        return service.startFromTelegram(id, request.technicianId());
    }

    @GetMapping("/{id}/access")
    @Transactional(readOnly = true)
    public Authorization authorize(
        @PathVariable UUID id,
        @RequestParam Operation operation,
        @RequestHeader("X-User-Token") String userToken
    ) {
        var ticket = tickets
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu"));
        var user = decoder.decode(userToken);
        String claim = user.getClaimAsString("uid");
        if (
            claim == null || !"user".equals(user.getClaimAsString("token_type"))
        ) throw ApiException.forbidden("Yêu cầu user token được chuyển tiếp");
        UUID actor = UUID.fromString(claim);
        java.util.List<String> roles = user.getClaimAsStringList("roles");
        boolean elevated =
            roles != null &&
            roles.stream().anyMatch(Set.of("ROLE_ADMIN", "ROLE_MANAGER")::contains);
        String accountType=user.getClaimAsString("account_type"),customer=user.getClaimAsString("customer_id"),siteScope=user.getClaimAsString("site_scope");
        java.util.List<String> sites=user.getClaimAsStringList("site_ids");
        boolean customerVisible="CUSTOMER".equals(accountType)&&customer!=null&&customer.equals(ticket.getCustomerId().toString())&&("ALL_SITES".equals(siteScope)||(sites!=null&&sites.contains(ticket.getSiteId().toString())));
        boolean visible =
            elevated ||
            customerVisible ||
            actor.equals(ticket.getAssigneeId());
        if (!visible) throw ApiException.forbidden("Không có quyền truy cập phiếu");
        if (
            operation == Operation.USE_PART &&
            (!actor.equals(ticket.getAssigneeId()) ||
                !Set.of(TicketStatus.IN_PROGRESS, TicketStatus.WAITING_PARTS).contains(
                    ticket.getStatus()
                ))
        ) throw ApiException.conflict("Không thể dùng linh kiện ở trạng thái hiện tại");
        return new Authorization(
            ticket.getId(),
            ticket.getCode(),
            ticket.getStatus(),
            ticket.getAssigneeId(),
            true
        );
    }

    @GetMapping("/{id}/chat-context")
    @Transactional(readOnly = true)
    public ChatContext chatContext(
        @PathVariable UUID id,
        @RequestHeader("X-User-Token") String userToken
    ) {
        return chatContext(
            tickets.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu")),
            userToken
        );
    }

    @PostMapping("/chat-contexts")
    @Transactional(readOnly = true)
    public java.util.List<ChatContext> chatContexts(
        @Valid @RequestBody ResolveRequest request,
        @RequestHeader("X-User-Token") String userToken
    ) {
        return tickets.findAllById(request.ids()).stream()
            .map(ticket -> {
                try {
                    return chatContext(ticket, userToken);
                } catch (ApiException denied) {
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private ChatContext chatContext(
        com.example.quanlybaotri.ticket.domain.MaintenanceTicket ticket,
        String userToken
    ) {
        var user = decoder.decode(userToken);
        String claim = user.getClaimAsString("uid");
        if (claim == null || !"user".equals(user.getClaimAsString("token_type"))) {
            throw ApiException.forbidden("Yêu cầu user token được chuyển tiếp");
        }
        UUID actor = UUID.fromString(claim);
        java.util.List<String> roles = user.getClaimAsStringList("roles");
        boolean elevated = roles != null && roles.stream()
            .anyMatch(Set.of("ROLE_ADMIN", "ROLE_MANAGER")::contains);
        boolean allowed = elevated || actor.equals(ticket.getRequesterId()) || actor.equals(ticket.getAssigneeId());
        if (!allowed) throw ApiException.forbidden("Không có quyền truy cập trao đổi của phiếu");
        boolean writable = !Set.of(TicketStatus.CLOSED, TicketStatus.REJECTED, TicketStatus.CANCELLED)
            .contains(ticket.getStatus());
        return new ChatContext(
            ticket.getId(),
            ticket.getCode(),
            ticket.getTitle(),
            ticket.getStatus(),
            ticket.getRequesterId(),
            ticket.getAssigneeId(),
            writable
        );
    }

    @PostMapping("/resolve")
    @Transactional(readOnly = true)
    public java.util.List<Authorization> resolve(@Valid @RequestBody ResolveRequest request) {
        var found = tickets.findAllById(request.ids());
        java.util.Map<UUID, com.example.quanlybaotri.ticket.domain.MaintenanceTicket> byId =
            new java.util.HashMap<>();
        found.forEach(ticket -> byId.put(ticket.getId(), ticket));
        return request
            .ids()
            .stream()
            .distinct()
            .map(byId::get)
            .filter(java.util.Objects::nonNull)
            .map(ticket ->
                new Authorization(
                    ticket.getId(),
                    ticket.getCode(),
                    ticket.getStatus(),
                    ticket.getAssigneeId(),
                    true
                )
            )
            .toList();
    }

    public enum Operation {
        VIEW_PARTS,
        USE_PART,
    }

    public record ResolveRequest(@Size(max = 500) java.util.List<UUID> ids) {
        public ResolveRequest {
            ids = ids == null ? java.util.List.of() : java.util.List.copyOf(ids);
        }
    }

    public record TelegramStartRequest(@NotNull UUID technicianId) {}

    public record Authorization(
        UUID ticketId,
        String ticketCode,
        TicketStatus status,
        UUID assigneeId,
        boolean allowed
    ) {}

    public record ChatContext(
        UUID ticketId,
        String ticketCode,
        String ticketTitle,
        TicketStatus status,
        UUID requesterId,
        UUID assigneeId,
        boolean writable
    ) {}
}
