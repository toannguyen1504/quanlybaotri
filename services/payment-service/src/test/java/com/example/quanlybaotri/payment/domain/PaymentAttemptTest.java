package com.example.quanlybaotri.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentAttemptTest {
    @Test
    void terminalUpdatesAreIdempotentAndSuccessCanCorrectARecordedFailure() {
        PaymentAttempt attempt = new PaymentAttempt(
            UUID.randomUUID(), "TXN1", new BigDecimal("50000"), UUID.randomUUID(),
            "127.0.0.1", Instant.now().plusSeconds(900)
        );

        assertThat(attempt.fail(null, "NCB", "ATM", "24", "02", null)).isTrue();
        assertThat(attempt.fail(null, "NCB", "ATM", "24", "02", null)).isFalse();
        assertThat(attempt.succeed("123", "NCB", "ATM", "00", "00", Instant.now())).isTrue();
        assertThat(attempt.succeed("123", "NCB", "ATM", "00", "00", Instant.now())).isFalse();
        assertThat(attempt.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }
}

