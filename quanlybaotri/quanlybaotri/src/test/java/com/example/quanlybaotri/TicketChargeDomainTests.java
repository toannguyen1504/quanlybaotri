package com.example.quanlybaotri;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.domain.TicketChargeType;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TicketChargeDomainTests {
    @Test
    void chargesAccumulatedPartCostsOnlyForPaidTickets() {
        MaintenanceTicket ticket = new MaintenanceTicket("BT-TEST", null, null, "Test", "Test",
                TicketPriority.MEDIUM, Instant.now(), Instant.now(), TicketChargeType.PAID);

        ticket.addPartCost(new BigDecimal("250000"));
        ticket.addPartCost(new BigDecimal("12500.555"));

        assertThat(ticket.getPartsCost()).isEqualByComparingTo("262500.56");
        assertThat(ticket.getChargeAmount()).isEqualByComparingTo("262500.56");

        ticket.chargeType(TicketChargeType.FREE);

        assertThat(ticket.getPartsCost()).isEqualByComparingTo("262500.56");
        assertThat(ticket.getChargeAmount()).isEqualByComparingTo("0");
    }
}
