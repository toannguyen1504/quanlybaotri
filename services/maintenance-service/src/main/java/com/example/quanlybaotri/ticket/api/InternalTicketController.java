package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/tickets")
public class InternalTicketController {

    private final TicketRepository tickets;
    private final JwtDecoder decoder;

    public InternalTicketController(TicketRepository tickets, JwtDecoder decoder) {
        this.tickets = tickets;
        this.decoder = decoder;
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
        boolean visible =
            elevated ||
            ticket.getRequesterId().equals(actor) ||
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

    public record Authorization(
        UUID ticketId,
        String ticketCode,
        TicketStatus status,
        UUID assigneeId,
        boolean allowed
    ) {}
}
