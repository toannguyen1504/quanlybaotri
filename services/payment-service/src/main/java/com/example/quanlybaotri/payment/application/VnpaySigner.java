package com.example.quanlybaotri.payment.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class VnpaySigner {

    public String signedUrl(String baseUrl, Map<String, String> parameters, String secret) {
        String query = canonicalQuery(parameters);
        return baseUrl + (baseUrl.contains("?") ? "&" : "?") + query +
            "&vnp_SecureHash=" + hmacSha512(secret, query);
    }

    public boolean verifyCallback(Map<String, String> parameters, String secret) {
        String supplied = parameters.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) return false;
        Map<String, String> signed = new HashMap<>(parameters);
        signed.remove("vnp_SecureHash");
        signed.remove("vnp_SecureHashType");
        return constantTimeEquals(hmacSha512(secret, canonicalQuery(signed)), supplied);
    }

    public String queryRequestHash(Map<String, String> fields, String secret) {
        String data = join(fields,
            "vnp_RequestId", "vnp_Version", "vnp_Command", "vnp_TmnCode",
            "vnp_TxnRef", "vnp_TransactionDate", "vnp_CreateDate", "vnp_IpAddr",
            "vnp_OrderInfo");
        return hmacSha512(secret, data);
    }

    public boolean verifyQueryResponse(Map<String, String> fields, String secret) {
        String supplied = fields.get("vnp_SecureHash");
        if (supplied == null || supplied.isBlank()) return false;
        String data = join(fields,
            "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message",
            "vnp_TmnCode", "vnp_TxnRef", "vnp_Amount", "vnp_BankCode",
            "vnp_PayDate", "vnp_TransactionNo", "vnp_TransactionType",
            "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode",
            "vnp_PromotionAmount");
        return constantTimeEquals(hmacSha512(secret, data), supplied);
    }

    String canonicalQuery(Map<String, String> parameters) {
        StringJoiner result = new StringJoiner("&");
        new TreeMap<>(parameters).forEach((key, value) -> {
            if (value != null && !value.isBlank()) result.add(encode(key) + "=" + encode(value));
        });
        return result.toString();
    }

    String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("KhÃ´ng thá»ƒ táº¡o chá»¯ kÃ½ VNPay", ex);
        }
    }

    private String join(Map<String, String> fields, String... names) {
        StringJoiner data = new StringJoiner("|");
        for (String name : names) data.add(Objects.toString(fields.get(name), ""));
        return data.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
            expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
            actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)
        );
    }
}

