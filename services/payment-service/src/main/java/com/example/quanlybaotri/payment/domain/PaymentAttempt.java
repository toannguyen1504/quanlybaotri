package com.example.quanlybaotri.payment.domain;

import com.example.quanlybaotri.shared.domain.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts")
public class PaymentAttempt extends BaseEntity {

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "txn_ref", nullable = false, unique = true, length = 100)
    private String txnRef;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "initiated_by_id", nullable = false)
    private UUID initiatedById;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "client_ip", nullable = false, length = 45)
    private String clientIp;

    @Column(name = "provider_transaction_no", length = 32)
    private String providerTransactionNo;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "response_code", length = 10)
    private String responseCode;

    @Column(name = "transaction_status", length = 10)
    private String transactionStatus;

    @Column(name = "pay_date")
    private Instant payDate;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_reconciled_at")
    private Instant lastReconciledAt;

    @Column(name = "last_query_response_code", length = 10)
    private String lastQueryResponseCode;

    protected PaymentAttempt() {}

    public PaymentAttempt(
        UUID ticketId,
        String txnRef,
        BigDecimal amount,
        UUID initiatedById,
        String clientIp,
        Instant expiresAt
    ) {
        this.ticketId = ticketId;
        this.txnRef = txnRef;
        this.amount = amount.setScale(2);
        this.initiatedById = initiatedById;
        this.clientIp = clientIp;
        this.expiresAt = expiresAt;
        status = PaymentStatus.PENDING;
    }

    public boolean succeed(
        String transactionNo,
        String bank,
        String card,
        String response,
        String transactionState,
        Instant paidAt
    ) {
        if (status == PaymentStatus.SUCCEEDED) return false;
        status = PaymentStatus.SUCCEEDED;
        providerTransactionNo = clean(transactionNo);
        bankCode = clean(bank);
        cardType = clean(card);
        responseCode = clean(response);
        transactionStatus = clean(transactionState);
        payDate = paidAt;
        completedAt = Instant.now();
        return true;
    }

    public boolean fail(
        String transactionNo,
        String bank,
        String card,
        String response,
        String transactionState,
        Instant paidAt
    ) {
        if (status != PaymentStatus.PENDING) return false;
        status = PaymentStatus.FAILED;
        providerTransactionNo = clean(transactionNo);
        bankCode = clean(bank);
        cardType = clean(card);
        responseCode = clean(response);
        transactionStatus = clean(transactionState);
        payDate = paidAt;
        completedAt = Instant.now();
        return true;
    }

    public void reconciled(String queryResponseCode) {
        lastReconciledAt = Instant.now();
        lastQueryResponseCode = clean(queryResponseCode);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public UUID getTicketId() { return ticketId; }
    public String getTxnRef() { return txnRef; }
    public BigDecimal getAmount() { return amount; }
    public UUID getInitiatedById() { return initiatedById; }
    public PaymentStatus getStatus() { return status; }
    public String getClientIp() { return clientIp; }
    public String getProviderTransactionNo() { return providerTransactionNo; }
    public String getBankCode() { return bankCode; }
    public String getCardType() { return cardType; }
    public String getResponseCode() { return responseCode; }
    public String getTransactionStatus() { return transactionStatus; }
    public Instant getPayDate() { return payDate; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getLastReconciledAt() { return lastReconciledAt; }
    public String getLastQueryResponseCode() { return lastQueryResponseCode; }
}

