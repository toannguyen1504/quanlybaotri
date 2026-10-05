package com.example.quanlybaotri.equipment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EquipmentWarrantyTest {

    @Test
    void calculatesWarrantyAtTheRequestedPointInTime() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-12-31T23:59:59Z");
        var equipment = new Equipment("EQ-001", "Thiết bị", new EquipmentCategory("CAT", "Loại", null));
        equipment.update("EQ-001", "Thiết bị", null, null, null, null,
            equipment.getCategory(), UUID.randomUUID(), null, null, start, end,
            EquipmentStatus.ACTIVE, true);

        assertThat(equipment.warrantyStatus(start.minusSeconds(1))).isEqualTo(WarrantyStatus.UNKNOWN);
        assertThat(equipment.warrantyStatus(start)).isEqualTo(WarrantyStatus.IN_WARRANTY);
        assertThat(equipment.warrantyStatus(end)).isEqualTo(WarrantyStatus.IN_WARRANTY);
        assertThat(equipment.warrantyStatus(end.plusSeconds(1))).isEqualTo(WarrantyStatus.OUT_OF_WARRANTY);
    }

    @Test
    void reportsUnknownWhenLegacyEquipmentHasNoWarrantyPeriod() {
        var equipment = new Equipment("EQ-002", "Thiết bị cũ", new EquipmentCategory("CAT", "Loại", null));
        assertThat(equipment.warrantyStatus(Instant.now())).isEqualTo(WarrantyStatus.UNKNOWN);
    }
}
