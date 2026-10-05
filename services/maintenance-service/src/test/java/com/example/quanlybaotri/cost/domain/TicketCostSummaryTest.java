package com.example.quanlybaotri.cost.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketCostSummaryTest {

    @Test
    void approvedSummaryIsImmutableUntilTicketIsReopened() {
        TicketCostSummary summary = new TicketCostSummary(UUID.randomUUID());
        summary.totals(new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("40"), new BigDecimal("50"));
        summary.submit(UUID.randomUUID());
        summary.approve(UUID.randomUUID());

        assertThatThrownBy(() -> summary.totals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
            .isInstanceOf(IllegalStateException.class);

        summary.reopen("Mở lại phiếu");
        summary.totals(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(summary.getStatus()).isEqualTo(CostStatus.DRAFT);
        assertThat(summary.getGrandTotal()).isEqualByComparingTo("1.00");
    }
}
