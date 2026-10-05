package com.example.quanlybaotri.cost.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketCostItemTest {

    @Test
    void calculatesLaborByMinuteAndRoundsHalfUpToTwoDecimals() {
        TicketCostItem item = TicketCostItem.labor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Kiểm tra thiết bị",
            37,
            new BigDecimal("123456.78"),
            Instant.parse("2026-09-23T01:00:00Z"),
            UUID.randomUUID()
        );

        assertThat(item.getCalculationStatus()).isEqualTo(CalculationStatus.READY);
        assertThat(item.getUnitCost()).isEqualByComparingTo("123456.78");
        assertThat(item.getAmount()).isEqualByComparingTo("76131.68");
    }

    @Test
    void marksLaborMissingWhenNoEffectiveRateExists() {
        TicketCostItem item = TicketCostItem.labor(
            UUID.randomUUID(), UUID.randomUUID(), "Xử lý", 30, null, Instant.now(), UUID.randomUUID()
        );

        assertThat(item.getCalculationStatus()).isEqualTo(CalculationStatus.MISSING_RATE);
        assertThat(item.getAmount()).isNull();
    }
}
