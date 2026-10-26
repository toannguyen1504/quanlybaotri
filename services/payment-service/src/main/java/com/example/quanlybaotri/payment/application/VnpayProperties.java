package com.example.quanlybaotri.payment.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.vnpay")
public class VnpayProperties {
    private String tmnCode = "";
    private String hashSecret = "";
    private String paymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    private String queryUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String returnUrl = "http://localhost:8080/api/v1/payments/vnpay/return";
    private String ipnUrl = "http://localhost:8080/api/v1/payments/vnpay/ipn";
    private String frontendResultUrl = "http://localhost:4200/payment-result";
    private Duration expiry = Duration.ofMinutes(15);

    public String getTmnCode() { return tmnCode; }
    public void setTmnCode(String tmnCode) { this.tmnCode = tmnCode; }
    public String getHashSecret() { return hashSecret; }
    public void setHashSecret(String hashSecret) { this.hashSecret = hashSecret; }
    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }
    public String getQueryUrl() { return queryUrl; }
    public void setQueryUrl(String queryUrl) { this.queryUrl = queryUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public String getIpnUrl() { return ipnUrl; }
    public void setIpnUrl(String ipnUrl) { this.ipnUrl = ipnUrl; }
    public String getFrontendResultUrl() { return frontendResultUrl; }
    public void setFrontendResultUrl(String frontendResultUrl) { this.frontendResultUrl = frontendResultUrl; }
    public Duration getExpiry() { return expiry; }
    public void setExpiry(Duration expiry) { this.expiry = expiry; }
}

