package com.example.quanlybaotri;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.quanlybaotri.inventory.domain.Part;
import com.example.quanlybaotri.shared.api.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryDomainTests {
    @Test
    void insufficientStockDoesNotChangeBalance() {
        Part part = new Part("P-TEST", "Test part", "cái", BigDecimal.ZERO, BigDecimal.TEN);
        part.add(BigDecimal.ONE);
        assertThatThrownBy(() -> part.issue(new BigDecimal("2"))).isInstanceOf(ApiException.class);
        assertThat(part.getCurrentStock()).isEqualByComparingTo("1");
    }
}
