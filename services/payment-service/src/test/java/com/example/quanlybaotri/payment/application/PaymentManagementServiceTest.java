package com.example.quanlybaotri.payment.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.maintenance.client.MaintenanceClient;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.payment.domain.PaymentStatus;
import com.example.quanlybaotri.payment.persistence.PaymentAttemptRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentManagementServiceTest {
    private MaintenanceClient maintenance;
    private PaymentAttemptRepository attempts;
    private PaymentManagementService service;
    private Actor actor;

    @BeforeEach
    void setUp() {
        maintenance = mock(MaintenanceClient.class);
        attempts = mock(PaymentAttemptRepository.class);
        service = new PaymentManagementService(
            maintenance,
            mock(OrganizationClient.class),
            attempts,
            mock(PaymentService.class)
        );
        actor = new Actor(
            UUID.randomUUID(), "manager", "Manager", Set.of(RoleName.MANAGER), "token",
            "INTERNAL", null, null, Set.of()
        );
    }

    @Test
    void returnsEmptyCatalogAndValidatesDateRange() {
        when(maintenance.catalogPage("token", null, null, null, 0, 20))
            .thenReturn(new MaintenanceClient.TicketContextPage(List.of(), 0, 0, 0, 20));

        assertThat(service.list(actor, null, null, null, null, 0, 20)).isEmpty();
        assertThatThrownBy(() -> service.list(
            actor, null, null, LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 8), 0, 20
        )).isInstanceOf(ApiException.class);
    }

    @Test
    void summarizesCashFlowWithDatabaseAggregatesInsteadOfLoadingAllAttempts() {
        when(maintenance.catalogPage("token", null, null, null, 0, 200))
            .thenReturn(new MaintenanceClient.TicketContextPage(List.of(), 0, 0, 0, 200));
        PaymentAttemptRepository.StatusAggregate succeeded = aggregate(PaymentStatus.SUCCEEDED, 3, "150000");
        PaymentAttemptRepository.StatusAggregate failed = aggregate(PaymentStatus.FAILED, 2, "50000");
        when(attempts.summarize(any(), any())).thenReturn(List.of(succeeded, failed));

        var summary = service.summary(
            actor,
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 30)
        );

        assertThat(summary.succeededCount()).isEqualTo(3);
        assertThat(summary.collectedAmount()).isEqualByComparingTo("150000");
        assertThat(summary.failedCount()).isEqualTo(2);
        verify(attempts, never()).findAll();
    }

    private PaymentAttemptRepository.StatusAggregate aggregate(
        PaymentStatus status,
        long total,
        String amount
    ) {
        PaymentAttemptRepository.StatusAggregate value = mock(PaymentAttemptRepository.StatusAggregate.class);
        when(value.getStatus()).thenReturn(status);
        when(value.getTotal()).thenReturn(total);
        when(value.getAmount()).thenReturn(new BigDecimal(amount));
        return value;
    }
}
