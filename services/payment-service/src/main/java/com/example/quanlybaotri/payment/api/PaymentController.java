package com.example.quanlybaotri.payment.api;

import com.example.quanlybaotri.payment.application.*;
import com.example.quanlybaotri.payment.application.PaymentService.*;
import com.example.quanlybaotri.payment.domain.TicketPaymentStatus;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService payments;
    private final PaymentManagementService management;
    private final CurrentUser current;
    private final VnpayProperties properties;

    public PaymentController(
        PaymentService payments,
        PaymentManagementService management,
        CurrentUser current,
        VnpayProperties properties
    ) {
        this.payments = payments;
        this.management = management;
        this.current = current;
        this.properties = properties;
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','REQUESTER')")
    public Page<PaymentManagementService.PaymentManagementItem> list(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) TicketPaymentStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return management.list(current.require(), q, status, from, to, page, size);
    }

    @GetMapping("/payments/summary")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','REQUESTER')")
    public PaymentManagementService.PaymentSummary summary(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return management.summary(current.require(), start, end);
    }

    @GetMapping("/payments/obligations")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','REQUESTER')")
    public PaymentManagementService.CursorPage<PaymentManagementService.PaymentManagementItem> obligations(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) TicketPaymentStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int size
    ) {
        return management.obligations(current.require(), q, status, from, to, cursor, size);
    }

    @GetMapping("/payments/transactions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','REQUESTER')")
    public Page<PaymentManagementService.PaymentTransactionItem> transactions(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) com.example.quanlybaotri.payment.domain.PaymentStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "false") boolean reconciliationRequired,
        Pageable pageable
    ) {
        return management.transactions(current.require(), q, status, from, to, reconciliationRequired, pageable);
    }

    @GetMapping("/tickets/{ticketId}/payments")
    public PaymentSummaryView get(@PathVariable UUID ticketId) {
        return payments.get(ticketId, current.require());
    }

    @PostMapping("/tickets/{ticketId}/payments")
    public AttemptView create(@PathVariable UUID ticketId, HttpServletRequest request) {
        return payments.create(ticketId, current.require(), clientIp(request));
    }

    @PostMapping("/payments/{attemptId}/reconcile")
    public AttemptView reconcile(@PathVariable UUID attemptId, HttpServletRequest request) {
        return payments.reconcile(attemptId, current.require(), request.getLocalAddr());
    }

    @GetMapping("/payments/vnpay/ipn")
    public IpnResponse ipn(@RequestParam Map<String, String> fields) {
        return payments.ipn(fields);
    }

    @GetMapping("/payments/vnpay/return")
    public ResponseEntity<Void> paymentReturn(@RequestParam Map<String, String> fields) {
        ReturnTarget result = payments.paymentReturn(fields);
        UriComponentsBuilder redirect = UriComponentsBuilder.fromUriString(properties.getFrontendResultUrl())
            .queryParam("valid", result.valid());
        if (result.valid()) redirect.queryParam("ticketId", result.ticketId()).queryParam("attemptId", result.attemptId());
        URI location = redirect.build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded;
    }
}

