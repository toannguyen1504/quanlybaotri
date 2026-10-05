package com.example.quanlybaotri.cost.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LaborRateTest {

    @Test
    void usesHalfOpenEffectivePeriodAndHonorsInactiveFlag() {
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2027-01-01T00:00:00Z");
        LaborRate rate = new LaborRate(null, new BigDecimal("100000"), from, to);

        assertThat(rate.applies(from)).isTrue();
        assertThat(rate.applies(to.minusMillis(1))).isTrue();
        assertThat(rate.applies(to)).isFalse();

        rate.update(null, new BigDecimal("100000"), from, to, false);
        assertThat(rate.applies(from)).isFalse();
    }
}
