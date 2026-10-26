package com.example.quanlybaotri.payment.api;

import com.example.quanlybaotri.payment.application.PaymentService;
import com.example.quanlybaotri.payment.application.PaymentService.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/payments")
public class InternalPaymentController {
    private final PaymentService payments;
    public InternalPaymentController(PaymentService payments){this.payments=payments;}
    @PostMapping("/snapshots") public Collection<PaymentSnapshot> snapshots(@RequestBody SnapshotRequest request){return payments.snapshots(request.costs()).values();}
    @GetMapping("/tickets/{ticketId}/settled") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void settled(@PathVariable UUID ticketId,@RequestParam BigDecimal amountDue){payments.ensureSettled(ticketId,amountDue);}
    @GetMapping("/tickets/{ticketId}/reopen-allowed") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reopenAllowed(@PathVariable UUID ticketId){payments.ensureReopenAllowed(ticketId);}
    public record SnapshotRequest(List<CostRef> costs){public SnapshotRequest{costs=costs==null?List.of():List.copyOf(costs);}}
}
