package com.example.quanlybaotri.payment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.payment.application.*;
import com.example.quanlybaotri.payment.domain.TicketPaymentStatus;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class PaymentControllerTest {
    @Test
    void forwardsManagementFiltersForTheAuthenticatedActor() {
        PaymentService payments = mock(PaymentService.class);
        PaymentManagementService management = mock(PaymentManagementService.class);
        CurrentUser current = mock(CurrentUser.class);
        Actor actor = new Actor(
            UUID.randomUUID(), "manager", "Manager", Set.of(RoleName.MANAGER), "token",
            "INTERNAL", null, null, Set.of()
        );
        when(current.require()).thenReturn(actor);
        when(management.list(
            actor, "TXN-001", TicketPaymentStatus.PENDING,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), 2, 50
        )).thenReturn(Page.empty());
        PaymentController controller = new PaymentController(
            payments, management, current, new VnpayProperties()
        );

        Page<PaymentManagementService.PaymentManagementItem> result = controller.list(
            "TXN-001", TicketPaymentStatus.PENDING,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), 2, 50
        );

        assertThat(result).isEmpty();
        verify(management).list(
            actor, "TXN-001", TicketPaymentStatus.PENDING,
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 8), 2, 50
        );
    }
}

