package com.example.quanlybaotri.payment.application;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.maintenance.client.MaintenanceClient;
import com.example.quanlybaotri.maintenance.client.MaintenanceClient.TicketContext;
import com.example.quanlybaotri.payment.domain.*;
import com.example.quanlybaotri.payment.persistence.PaymentAttemptRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import io.micrometer.core.instrument.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private static final Logger log=LoggerFactory.getLogger(PaymentService.class);
    private static final Duration RECONCILE_COOLDOWN=Duration.ofMinutes(5);
    private final MaintenanceClient maintenance; private final PaymentAttemptRepository attempts;
    private final VnpayClient vnpay; private final VnpayProperties properties;
    private final Counter succeeded,failed,invalidSignature,reconciliationErrors;
    public PaymentService(MaintenanceClient maintenance,PaymentAttemptRepository attempts,VnpayClient vnpay,VnpayProperties properties,MeterRegistry meters){
        this.maintenance=maintenance;this.attempts=attempts;this.vnpay=vnpay;this.properties=properties;
        succeeded=meters.counter("payment.transactions","result","succeeded");failed=meters.counter("payment.transactions","result","failed");
        invalidSignature=meters.counter("payment.callback.invalid_signature");reconciliationErrors=meters.counter("payment.reconciliation.errors");
    }
    @Transactional(readOnly=true)
    public PaymentSummaryView get(UUID ticketId,Actor actor){
        TicketContext ticket=maintenance.ticket(ticketId,actor.token());List<PaymentAttempt> history=attempts.findByTicketIdOrderByCreatedAtDesc(ticketId);
        PaymentSnapshot snapshot=snapshot(ticketId,ticket.amountDue(),ticket.costStatus(),history);
        boolean active=history.stream().anyMatch(a->a.getStatus()==PaymentStatus.PENDING&&a.getExpiresAt().isAfter(Instant.now()));
        boolean canPay=actor.customer()&&actor.id().equals(ticket.requesterId())&&"RESOLVED".equals(ticket.ticketStatus())&&(snapshot.status()==TicketPaymentStatus.UNPAID||snapshot.status()==TicketPaymentStatus.FAILED||(snapshot.status()==TicketPaymentStatus.PENDING&&active));
        return new PaymentSummaryView(ticketId,snapshot.status(),snapshot.amountDue(),snapshot.paidAt(),canPay,history.stream().map(a->AttemptView.from(a,elevated(actor),null)).toList());
    }
    @Transactional
    public AttemptView create(UUID ticketId,Actor actor,String clientIp){
        if(!actor.customer())throw ApiException.forbidden("Chỉ tài khoản khách hàng được khởi tạo thanh toán");
        TicketContext ticket=maintenance.ticket(ticketId,actor.token());
        if(!actor.id().equals(ticket.requesterId()))throw ApiException.forbidden("Chỉ khách hàng tạo phiếu được thanh toán báo giá");
        if(!"RESOLVED".equals(ticket.ticketStatus()))throw ApiException.conflict("Chỉ thanh toán khi phiếu đã xử lý xong");
        BigDecimal amount=acceptedAmount(ticket).setScale(2);if(amount.signum()<=0)throw ApiException.conflict("Phiếu này không phát sinh số tiền cần thanh toán");
        if(attempts.existsByTicketIdAndStatus(ticketId,PaymentStatus.SUCCEEDED))throw ApiException.conflict("Phiếu đã được thanh toán");
        Optional<PaymentAttempt> pending=attempts.findFirstByTicketIdAndStatusOrderByCreatedAtDesc(ticketId,PaymentStatus.PENDING);
        if(pending.isPresent()){if(!pending.get().getExpiresAt().isAfter(Instant.now()))throw ApiException.conflict("Giao dịch đã hết hạn và cần được quản lý đối soát trước khi thử lại");return AttemptView.from(pending.get(),false,vnpay.paymentUrl(pending.get(),ticket.ticketCode()));}
        String txnRef=UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT);PaymentAttempt attempt=attempts.saveAndFlush(new PaymentAttempt(ticketId,txnRef,amount,actor.id(),normalizeIp(clientIp),Instant.now().plus(properties.getExpiry())));
        log.info("Created VNPay payment ticketId={} txnRef={}",ticketId,txnRef);return AttemptView.from(attempt,false,vnpay.paymentUrl(attempt,ticket.ticketCode()));
    }
    @Transactional
    public AttemptView reconcile(UUID attemptId,Actor actor,String serverIp){
        PaymentAttempt attempt=attempts.findById(attemptId).orElseThrow(()->ApiException.notFound("Không tìm thấy giao dịch thanh toán"));
        boolean expiredOwner=actor.customer()&&actor.id().equals(attempt.getInitiatedById())&&!attempt.getExpiresAt().isAfter(Instant.now());
        if(!elevated(actor)&&!expiredOwner)throw ApiException.forbidden("Chỉ quản lý hoặc khách hàng sở hữu giao dịch đã hết hạn được đối soát thanh toán");
        if(attempt.getStatus()!=PaymentStatus.PENDING)return AttemptView.from(attempt,true,null);Instant earliest=Instant.now().minus(RECONCILE_COOLDOWN);
        if(attempt.getLastReconciledAt()!=null&&attempt.getLastReconciledAt().isAfter(earliest))throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"RECONCILIATION_RATE_LIMITED","Chỉ được đối soát lại sau 5 phút");
        try{VnpayClient.QueryResult result=vnpay.query(attempt,normalizeIp(serverIp));Map<String,String> fields=result.fields();
            if(!Objects.equals(attempt.getTxnRef(),result.get("vnp_TxnRef"))||!Objects.equals(properties.getTmnCode(),result.get("vnp_TmnCode")))throw invalidProviderResponse("Thông tin định danh đối soát VNPay không khớp");
            String response=result.get("vnp_ResponseCode");attempt.reconciled(response);if(!"00".equals(response)){if("91".equals(response)&&Instant.now().isAfter(attempt.getExpiresAt()))applyFailure(attempt,fields);return AttemptView.from(attempt,true,null);}
            ensureAmount(attempt,result.get("vnp_Amount"));String state=result.get("vnp_TransactionStatus");if("00".equals(state))applySuccess(attempt,fields);else if(!"01".equals(state)||Instant.now().isAfter(attempt.getExpiresAt()))applyFailure(attempt,fields);return AttemptView.from(attempt,true,null);
        }catch(ApiException ex){reconciliationErrors.increment();throw ex;}
    }
    @Transactional
    public IpnResponse ipn(Map<String,String> fields){
        if(!vnpay.verifyCallback(fields)||!vnpay.correctMerchant(fields)){invalidSignature.increment();return new IpnResponse("97","Invalid Signature");}
        PaymentAttempt attempt=attempts.findByTxnRef(fields.get("vnp_TxnRef")).orElse(null);if(attempt==null)return new IpnResponse("01","Order not found");try{ensureAmount(attempt,fields.get("vnp_Amount"));}catch(ApiException ex){return new IpnResponse("04","Invalid amount");}
        boolean success=providerReportsSuccess(fields);if(attempt.getStatus()==PaymentStatus.SUCCEEDED||(attempt.getStatus()==PaymentStatus.FAILED&&!success))return new IpnResponse("02","Order already confirmed");settle(attempt,fields);return new IpnResponse("00","Confirm Success");
    }
    @Transactional
    public ReturnTarget paymentReturn(Map<String,String> fields){
        if(!vnpay.verifyCallback(fields)||!vnpay.correctMerchant(fields)){invalidSignature.increment();return new ReturnTarget(false,null,null);}
        PaymentAttempt attempt=attempts.findByTxnRef(fields.get("vnp_TxnRef")).orElse(null);
        if(attempt==null)return new ReturnTarget(false,null,null);
        try{ensureAmount(attempt,fields.get("vnp_Amount"));}catch(ApiException ex){return new ReturnTarget(false,null,null);}
        settle(attempt,fields);
        return new ReturnTarget(true,attempt.getTicketId(),attempt.getId());
    }
    @Transactional(readOnly=true)
    public void ensureSettled(UUID id,BigDecimal amount){if(amount==null)throw ApiException.conflict("Phiếu chưa có bảng chi phí được duyệt");if(amount.signum()>0&&!attempts.existsByTicketIdAndStatus(id,PaymentStatus.SUCCEEDED))throw ApiException.conflict("Phiếu phải được thanh toán trước khi đóng");}
    @Transactional(readOnly=true)
    public void ensureReopenAllowed(UUID id){if(attempts.existsByTicketIdAndStatus(id,PaymentStatus.SUCCEEDED))throw ApiException.conflict("Không thể mở lại phiếu đã thanh toán");if(attempts.existsByTicketIdAndStatus(id,PaymentStatus.PENDING))throw ApiException.conflict("Cần đối soát giao dịch đang chờ trước khi mở lại phiếu");}
    @Transactional(readOnly=true)
    public Map<UUID,PaymentSnapshot> snapshots(Collection<CostRef> costs){if(costs.isEmpty())return Map.of();Set<UUID> ids=costs.stream().map(CostRef::ticketId).collect(Collectors.toSet());Map<UUID,List<PaymentAttempt>> grouped=attempts.findByTicketIdInOrderByCreatedAtDesc(ids).stream().collect(Collectors.groupingBy(PaymentAttempt::getTicketId));Map<UUID,PaymentSnapshot> result=new LinkedHashMap<>();for(CostRef cost:costs)result.put(cost.ticketId(),snapshot(cost.ticketId(),cost.amountDue(),cost.costStatus(),grouped.getOrDefault(cost.ticketId(),List.of())));return result;}
    PaymentSnapshot snapshot(UUID id,BigDecimal amount,String status,List<PaymentAttempt> history){if(!"ACCEPTED".equals(status)||amount==null)return new PaymentSnapshot(id,TicketPaymentStatus.NOT_AVAILABLE,null,null);amount=amount.setScale(2);if(amount.signum()<=0)return new PaymentSnapshot(id,TicketPaymentStatus.NOT_REQUIRED,amount,null);Optional<PaymentAttempt> paid=history.stream().filter(a->a.getStatus()==PaymentStatus.SUCCEEDED).findFirst();if(paid.isPresent())return new PaymentSnapshot(id,TicketPaymentStatus.SUCCEEDED,amount,paid.get().getCompletedAt());if(history.stream().anyMatch(a->a.getStatus()==PaymentStatus.PENDING))return new PaymentSnapshot(id,TicketPaymentStatus.PENDING,amount,null);if(history.stream().anyMatch(a->a.getStatus()==PaymentStatus.FAILED))return new PaymentSnapshot(id,TicketPaymentStatus.FAILED,amount,null);return new PaymentSnapshot(id,TicketPaymentStatus.UNPAID,amount,null);}
    private BigDecimal acceptedAmount(TicketContext t){if(!"ACCEPTED".equals(t.costStatus())||t.amountDue()==null)throw ApiException.conflict("Báo giá phải được khách hàng chấp nhận trước khi thanh toán hoặc đóng phiếu");return t.amountDue();}
    private void settle(PaymentAttempt attempt,Map<String,String> fields){if(providerReportsSuccess(fields))applySuccess(attempt,fields);else applyFailure(attempt,fields);}
    private boolean providerReportsSuccess(Map<String,String> fields){return "00".equals(fields.get("vnp_ResponseCode"))&&"00".equals(fields.get("vnp_TransactionStatus"));}
    private void applySuccess(PaymentAttempt a,Map<String,String> f){if(!a.succeed(f.get("vnp_TransactionNo"),f.get("vnp_BankCode"),f.get("vnp_CardType"),f.get("vnp_ResponseCode"),f.get("vnp_TransactionStatus"),vnpay.parsePayDate(f.get("vnp_PayDate"))))return;succeeded.increment();record(a,"PAYMENT_SUCCEEDED","Thanh toán VNPay thành công");}
    private void applyFailure(PaymentAttempt a,Map<String,String> f){if(!a.fail(f.get("vnp_TransactionNo"),f.get("vnp_BankCode"),f.get("vnp_CardType"),f.get("vnp_ResponseCode"),f.get("vnp_TransactionStatus"),vnpay.parsePayDate(f.get("vnp_PayDate"))))return;failed.increment();record(a,"PAYMENT_FAILED","Thanh toán VNPay không thành công");}
    private void record(PaymentAttempt a,String action,String description){Map<String,Object> m=new LinkedHashMap<>();m.put("paymentAttemptId",a.getId());m.put("paymentStatus",a.getStatus().name());m.put("amount",a.getAmount());m.put("txnRef",a.getTxnRef());maintenance.record(a.getTicketId(),action,description,m);}
    private void ensureAmount(PaymentAttempt a,String value){if(value==null||a.getAmount().compareTo(vnpay.merchantAmount(value))!=0)throw invalidProviderResponse("Số tiền VNPay không khớp giao dịch");}
    private boolean elevated(Actor a){return a.has(RoleName.ADMIN)||a.has(RoleName.MANAGER);}private String normalizeIp(String value){if(value==null)return "127.0.0.1";String first=value.split(",",2)[0].trim();return first.length()<=45&&first.matches("[0-9a-fA-F:.]+")?first:"127.0.0.1";}private ApiException invalidProviderResponse(String message){return new ApiException(HttpStatus.BAD_GATEWAY,"VNPAY_INVALID_RESPONSE",message);}
    public record CostRef(UUID ticketId,String costStatus,BigDecimal amountDue){}public record PaymentSnapshot(UUID ticketId,TicketPaymentStatus status,BigDecimal amountDue,Instant paidAt){}public record PaymentSummaryView(UUID ticketId,TicketPaymentStatus status,BigDecimal amountDue,Instant paidAt,boolean canPay,List<AttemptView> attempts){}
    public record AttemptView(UUID attemptId,String txnRef,PaymentStatus status,BigDecimal amount,Instant createdAt,Instant expiresAt,Instant completedAt,String providerTransactionNo,String bankCode,String cardType,String responseCode,Instant lastReconciledAt,String paymentUrl){static AttemptView from(PaymentAttempt a,boolean details,String url){return new AttemptView(a.getId(),a.getTxnRef(),a.getStatus(),a.getAmount(),a.getCreatedAt(),a.getExpiresAt(),a.getCompletedAt(),details?a.getProviderTransactionNo():null,details?a.getBankCode():null,details?a.getCardType():null,details?a.getResponseCode():null,a.getLastReconciledAt(),url);}}
    public record IpnResponse(String RspCode,String Message){}public record ReturnTarget(boolean valid,UUID ticketId,UUID attemptId){}
}
