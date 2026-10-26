package com.example.quanlybaotri.ticket.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketStatus;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class InternalTicketControllerChatTest {
    private TicketRepository tickets;
    private JwtDecoder decoder;
    private InternalTicketController controller;
    private MaintenanceTicket ticket;
    private UUID ticketId;
    private UUID requesterId;
    private UUID assigneeId;

    @BeforeEach
    void setUp() {
        tickets = mock(TicketRepository.class);
        decoder = mock(JwtDecoder.class);
        controller = new InternalTicketController(tickets, decoder, mock(TicketService.class));
        ticket = mock(MaintenanceTicket.class);
        ticketId = UUID.randomUUID();
        requesterId = UUID.randomUUID();
        assigneeId = UUID.randomUUID();
        when(ticket.getId()).thenReturn(ticketId);
        when(ticket.getCode()).thenReturn("TK-1");
        when(ticket.getTitle()).thenReturn("Printer");
        when(ticket.getStatus()).thenReturn(TicketStatus.IN_PROGRESS);
        when(ticket.getRequesterId()).thenReturn(requesterId);
        when(ticket.getAssigneeId()).thenReturn(assigneeId);
        when(tickets.findById(ticketId)).thenReturn(Optional.of(ticket));
    }

    @Test
    void exactRequesterCanAccessChat() {
        authenticate(requesterId, "ROLE_REQUESTER");

        assertThat(controller.chatContext(ticketId, "token").writable()).isTrue();
    }

    @Test
    void anotherRequesterIsDeniedEvenWhenApplicationVisibilityCouldMatchTheSameSite() {
        authenticate(UUID.randomUUID(), "ROLE_REQUESTER");

        assertThatThrownBy(() -> controller.chatContext(ticketId, "token"))
            .isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void currentAssigneeCanAccessChat() {
        authenticate(assigneeId, "ROLE_TECHNICIAN");

        assertThat(controller.chatContext(ticketId, "token").assigneeId()).isEqualTo(assigneeId);
    }

    @Test
    void formerAssigneeIsDeniedAfterReassignment() {
        UUID formerAssignee = UUID.randomUUID();
        authenticate(formerAssignee, "ROLE_TECHNICIAN");

        assertThatThrownBy(() -> controller.chatContext(ticketId, "token"))
            .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_MANAGER"})
    void elevatedRolesCanAccessEveryTicket(String role) {
        authenticate(UUID.randomUUID(), role);

        assertThat(controller.chatContext(ticketId, "token").ticketId()).isEqualTo(ticketId);
    }

    @Test
    void terminalTicketIsReadOnly() {
        authenticate(requesterId, "ROLE_REQUESTER");
        when(ticket.getStatus()).thenReturn(TicketStatus.CLOSED);

        assertThat(controller.chatContext(ticketId, "token").writable()).isFalse();
    }

    private void authenticate(UUID actorId, String... roles) {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user")
            .claim("uid", actorId.toString())
            .claim("token_type", "user")
            .claim("roles", List.of(roles))
            .build();
        when(decoder.decode("token")).thenReturn(jwt);
    }
}
