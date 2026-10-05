package com.example.quanlybaotri.ticket.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaintenanceTicketTest {

    @Test
    void storesOnlyCrossServiceIds() {
        UUID equipment = UUID.randomUUID(),
            requester = UUID.randomUUID(),
            technician = UUID.randomUUID();
        var ticket = new MaintenanceTicket(
            "BT-2026-000001",
            equipment,
            requester,
            "title",
            "description",
            TicketPriority.HIGH,
            Instant.now().plusSeconds(60),
            Instant.now().plusSeconds(120)
        );
        ticket.accept();
        ticket.assign(technician);
        assertThat(ticket.getEquipmentId()).isEqualTo(equipment);
        assertThat(ticket.getRequesterId()).isEqualTo(requester);
        assertThat(ticket.getAssigneeId()).isEqualTo(technician);
    }

    @Test
    void keepsCustomerSiteCreatorAndWarrantySnapshotImmutableInTheTicket() {
        UUID equipment = UUID.randomUUID(), requester = UUID.randomUUID(), creator = UUID.randomUUID();
        UUID customer = UUID.randomUUID(), site = UUID.randomUUID();
        Instant warrantyEnd = Instant.parse("2027-01-01T00:00:00Z");
        var ticket = new MaintenanceTicket(
            "BT-2026-000002", equipment, requester, creator, customer, site,
            "title", "description", TicketPriority.MEDIUM,
            Instant.now().plusSeconds(60), Instant.now().plusSeconds(120),
            WarrantyStatus.IN_WARRANTY, warrantyEnd
        );

        assertThat(ticket.getCustomerId()).isEqualTo(customer);
        assertThat(ticket.getSiteId()).isEqualTo(site);
        assertThat(ticket.getCreatedById()).isEqualTo(creator);
        assertThat(ticket.getWarrantyStatusAtCreation()).isEqualTo(WarrantyStatus.IN_WARRANTY);
        assertThat(ticket.getWarrantyEndAtCreation()).isEqualTo(warrantyEnd);
    }
}
