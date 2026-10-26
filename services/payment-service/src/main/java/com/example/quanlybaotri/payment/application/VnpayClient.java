package com.example.quanlybaotri.payment.application;

import com.example.quanlybaotri.payment.domain.PaymentAttempt;
import com.example.quanlybaotri.shared.api.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class VnpayClient {
    private static final Logger log = LoggerFactory.getLogger(VnpayClient.class);
    private static final ZoneId VNPAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestClient client;
    private final VnpayProperties properties;
    private final VnpaySigner signer;

    public VnpayClient(RestClient.Builder builder, VnpayProperties properties, VnpaySigner signer) {
        this.client = builder.build();
        this.properties = properties;
        this.signer = signer;
    }

    public String paymentUrl(PaymentAttempt attempt, String ticketCode) {
        configured();
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_Amount", providerAmount(attempt.getAmount()));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", attempt.getTxnRef());
        fields.put("vnp_OrderInfo", "Thanh toan phieu " + ticketCode);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", properties.getReturnUrl());
        fields.put("vnp_IpAddr", attempt.getClientIp());
        fields.put("vnp_CreateDate", format(attempt.getCreatedAt()));
        fields.put("vnp_ExpireDate", format(attempt.getExpiresAt()));
        return signer.signedUrl(properties.getPaymentUrl(), fields, properties.getHashSecret());
    }

    public QueryResult query(PaymentAttempt attempt, String serverIp) {
        configured();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_RequestId", UUID.randomUUID().toString().replace("-", ""));
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "querydr");
        fields.put("vnp_TmnCode", properties.getTmnCode());
        fields.put("vnp_TxnRef", attempt.getTxnRef());
        fields.put("vnp_TransactionDate", format(attempt.getCreatedAt()));
        fields.put("vnp_CreateDate", format(Instant.now()));
        fields.put("vnp_IpAddr", serverIp);
        fields.put("vnp_OrderInfo", "Query transaction " + attempt.getTxnRef());
        fields.put("vnp_SecureHash", signer.queryRequestHash(fields, properties.getHashSecret()));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.post()
                .uri(properties.getQueryUrl())
                .body(fields)
                .retrieve()
                .body(Map.class);
            if (body == null) throw unavailable("VNPay tráº£ vá» dá»¯ liá»‡u rá»—ng");
            Map<String, String> response = new HashMap<>();
            body.forEach((key, value) -> response.put(key, value == null ? "" : String.valueOf(value)));
            if (!signer.verifyQueryResponse(response, properties.getHashSecret())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "VNPAY_INVALID_SIGNATURE", "Chá»¯ kÃ½ pháº£n há»“i Ä‘á»‘i soÃ¡t VNPay khÃ´ng há»£p lá»‡");
            }
            return new QueryResult(response);
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("VNPay reconciliation request failed txnRef={}: {}", attempt.getTxnRef(), ex.getMessage());
            throw unavailable("KhÃ´ng thá»ƒ káº¿t ná»‘i VNPay Ä‘á»ƒ Ä‘á»‘i soÃ¡t");
        }
    }

    public boolean verifyCallback(Map<String, String> fields) {
        configured();
        return signer.verifyCallback(fields, properties.getHashSecret());
    }

    public boolean correctMerchant(Map<String, String> fields) {
        return Objects.equals(properties.getTmnCode(), fields.get("vnp_TmnCode"));
    }

    public String providerAmount(BigDecimal amount) {
        try {
            String value = amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.UNNECESSARY)
                .toPlainString();
            if (!value.matches("[1-9][0-9]{0,11}")) throw new ArithmeticException();
            return value;
        } catch (ArithmeticException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_AMOUNT", "Sá»‘ tiá»n khÃ´ng náº±m trong giá»›i háº¡n VNPay há»— trá»£");
        }
    }

    public BigDecimal merchantAmount(String providerAmount) {
        try {
            return new BigDecimal(providerAmount).movePointLeft(2).setScale(2);
        } catch (RuntimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYMENT_AMOUNT", "Sá»‘ tiá»n VNPay khÃ´ng há»£p lá»‡");
        }
    }

    public Instant parsePayDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, VNPAY_TIME).atZone(VNPAY_ZONE).toInstant();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String format(Instant instant) {
        return VNPAY_TIME.format(instant.atZone(VNPAY_ZONE));
    }

    private void configured() {
        if (blank(properties.getTmnCode()) || blank(properties.getHashSecret())) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPAY_NOT_CONFIGURED", "VNPay chÆ°a Ä‘Æ°á»£c cáº¥u hÃ¬nh");
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VNPAY_UNAVAILABLE", message);
    }

    public record QueryResult(Map<String, String> fields) {
        public String get(String name) { return fields.get(name); }
    }
}

