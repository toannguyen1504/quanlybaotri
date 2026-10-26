package com.example.quanlybaotri.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.junit.jupiter.api.Test;

class VnpaySignerTest {
    private final VnpaySigner signer = new VnpaySigner();

    @Test
    void usesHmacSha512AndCanonicalUtf8Query() {
        String key = "\u000b".repeat(20);
        assertThat(signer.hmacSha512(key, "Hi There")).isEqualTo(
            "87aa7cdea5ef619d4ff0b4241a1d6cb02379f4e2ce4ec2787ad0b30545e17cde" +
            "daa833b7d6b8a702038b274eaea3f4e4be9d914eeb61f1702e696c203a126854"
        );
        assertThat(signer.canonicalQuery(Map.of("vnp_TxnRef", "ABC 1", "vnp_Amount", "10000")))
            .isEqualTo("vnp_Amount=10000&vnp_TxnRef=ABC+1");
    }

    @Test
    void verifiesCallbackWithoutHashFieldsAndRejectsTampering() {
        String secret = "sandbox-secret";
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Amount", "12500000");
        fields.put("vnp_TmnCode", "TESTCODE");
        fields.put("vnp_TxnRef", "ABC123");
        fields.put("vnp_SecureHashType", "HmacSHA512");
        Map<String, String> signedFields = new HashMap<>(fields);
        signedFields.remove("vnp_SecureHashType");
        fields.put("vnp_SecureHash", signer.hmacSha512(secret, signer.canonicalQuery(signedFields)));

        assertThat(signer.verifyCallback(fields, secret)).isTrue();
        fields.put("vnp_Amount", "12500001");
        assertThat(signer.verifyCallback(fields, secret)).isFalse();
    }
}

