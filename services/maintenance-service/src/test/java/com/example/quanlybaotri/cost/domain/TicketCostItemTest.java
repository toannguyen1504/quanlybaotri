package com.example.quanlybaotri.cost.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketCostItemTest {

    @Test
    void calculatesIncidentalCostFromQuantityAndUnitCost() {
        TicketCostItem item = TicketCostItem.manual(
            UUID.randomUUID(),
            CostCategory.OTHER,
            "Phí vận chuyển linh kiện",
            new BigDecimal("2"),
            "chuyến",
            new BigDecimal("125000"),
            Instant.parse("2026-09-23T01:00:00Z"),
            null,
            null,
            UUID.randomUUID()
        );

        assertThat(item.getCalculationStatus()).isEqualTo(CalculationStatus.READY);
        assertThat(item.getCategory()).isEqualTo(CostCategory.OTHER);
        assertThat(item.getDescription()).isEqualTo("Phí vận chuyển linh kiện");
        assertThat(item.getAmount()).isEqualByComparingTo("250000.00");
    }

    @Test
    void requiresAConcreteDescriptionForIncidentalCost() {
        assertThatThrownBy(() -> TicketCostItem.manual(
            UUID.randomUUID(),
            CostCategory.OTHER,
            "   ",
            BigDecimal.ONE,
            "khoản",
            new BigDecimal("100000"),
            Instant.now(),
            null,
            null,
            UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Cost description is required");
    }
}
