package com.example.quanlybaotri.payment.application;

import static org.assertj.core.api.Assertions.*;

import com.example.quanlybaotri.shared.api.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VnpayClientTest {
    private final VnpayClient client = new VnpayClient(
        RestClient.builder(), new VnpayProperties(), new VnpaySigner()
    );

    @Test
    void convertsVndToVnpayMinorAmountExactly() {
        assertThat(client.providerAmount(new BigDecimal("125000.50"))).isEqualTo("12500050");
        assertThat(client.merchantAmount("12500050")).isEqualByComparingTo("125000.50");
    }

    @Test
    void rejectsUnsupportedAmounts() {
        assertThatThrownBy(() -> client.providerAmount(BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> client.providerAmount(new BigDecimal("10000000000"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> client.providerAmount(new BigDecimal("1.001"))).isInstanceOf(ApiException.class);
    }
}

