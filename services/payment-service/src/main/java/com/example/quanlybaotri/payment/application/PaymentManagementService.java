package com.example.quanlybaotri.payment.application;

import com.example.quanlybaotri.maintenance.client.MaintenanceClient;
import com.example.quanlybaotri.maintenance.client.MaintenanceClient.TicketContext;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.payment.domain.*;
import com.example.quanlybaotri.payment.persistence.PaymentAttemptRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentManagementService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    private final MaintenanceClient maintenance; private final OrganizationClient organizations;
    private final PaymentAttemptRepository attempts; private final PaymentService payments;
    public PaymentManagementService(MaintenanceClient maintenance,OrganizationClient organizations,PaymentAttemptRepository attempts,PaymentService payments){this.maintenance=maintenance;this.organizations=organizations;this.attempts=attempts;this.payments=payments;}
    @Transactional(readOnly=true)
    public Page<PaymentManagementItem> list(Actor actor,String q,TicketPaymentStatus status,LocalDate from,LocalDate to,int requestedPage,int requestedSize){
        if(from!=null&&to!=null&&from.isAfter(to))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAYMENT_DATE_RANGE","Ngày bắt đầu không được sau ngày kết thúc");
        int page=Math.max(requestedPage,0),size=Math.min(Math.max(requestedSize,1),100);Instant start=dateStart(from),end=dateEnd(to);
        var catalog=maintenance.catalogPage(actor.token(),q,start,end,page,size);if(catalog==null)return Page.empty(PageRequest.of(page,size));
        List<PaymentManagementItem> content=items(actor,catalog.content()).stream().filter(item->status==null||item.paymentStatus()==status).toList();
        return new PageImpl<>(content,PageRequest.of(page,size),status==null?catalog.totalElements():content.size());
    }

    @Transactional(readOnly=true)
    public CursorPage<PaymentManagementItem> obligations(Actor actor,String q,TicketPaymentStatus status,LocalDate from,LocalDate to,String cursor,int requestedSize){
        validateRange(from,to);int size=Math.min(Math.max(requestedSize,1),100),page=decodeCursor(cursor);
        var catalog=maintenance.catalogPage(actor.token(),q,dateStart(from),dateEnd(to),page,size);
        if(catalog==null)return new CursorPage<>(List.of(),null,false);
        List<PaymentManagementItem> content=items(actor,catalog.content()).stream().filter(item->status==null||item.paymentStatus()==status).limit(size).toList();
        boolean more=page+1<catalog.totalPages();return new CursorPage<>(content,more?encodeCursor(page+1):null,more);
    }

    @Transactional(readOnly=true)
    public PaymentSummary summary(Actor actor,LocalDate from,LocalDate to){
        validateRange(from,to);long outstandingCount=0,pendingCount=0;BigDecimal outstandingAmount=BigDecimal.ZERO;
        for(int page=0;;page++){var catalog=maintenance.catalogPage(actor.token(),null,null,null,page,200);if(catalog==null)break;for(PaymentManagementItem item:items(actor,catalog.content()))if(Set.of(TicketPaymentStatus.UNPAID,TicketPaymentStatus.PENDING,TicketPaymentStatus.FAILED).contains(item.paymentStatus())&&item.amountDue()!=null&&item.amountDue().signum()>0){outstandingCount++;outstandingAmount=outstandingAmount.add(item.amountDue());if(item.paymentStatus()==TicketPaymentStatus.PENDING)pendingCount++;}if(page+1>=catalog.totalPages())break;}
        List<PaymentAttemptRepository.StatusAggregate> period=elevated(actor)
            ? attempts.summarize(dateStart(from),dateEnd(to))
            : attempts.summarizeForInitiator(actor.id(),dateStart(from),dateEnd(to));
        long success=aggregateCount(period,PaymentStatus.SUCCEEDED),failed=aggregateCount(period,PaymentStatus.FAILED);
        BigDecimal collected=aggregateAmount(period,PaymentStatus.SUCCEEDED);
        return new PaymentSummary(outstandingCount,outstandingAmount,pendingCount,success,collected,failed);
    }

    @Transactional(readOnly=true)
    public Page<PaymentTransactionItem> transactions(Actor actor,String q,PaymentStatus status,LocalDate from,LocalDate to,boolean reconciliationRequired,Pageable pageable){
        validateRange(from,to);if(reconciliationRequired&&!elevated(actor))throw ApiException.forbidden("KhÃ´ng cÃ³ quyá»n xem hÃ ng chá» Ä‘á»‘i soÃ¡t");Pageable bounded=PageRequest.of(Math.max(pageable.getPageNumber(),0),Math.min(Math.max(pageable.getPageSize(),1),100),Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by(Sort.Direction.DESC,"id")));
        UUID initiatedBy=elevated(actor)?null:actor.id();Page<PaymentAttempt> page=attempts.findAll(attemptFilters(q,status,dateStart(from),dateEnd(to),reconciliationRequired,initiatedBy),bounded);
        Map<UUID,TicketContext> contexts=new HashMap<>();maintenance.resolve(actor.token(),page.getContent().stream().map(PaymentAttempt::getTicketId).toList()).forEach(t->contexts.put(t.ticketId(),t));
        String term=q==null?"":q.trim().toLowerCase(Locale.ROOT);
        List<PaymentTransactionItem> content=page.getContent().stream().filter(a->contexts.containsKey(a.getTicketId())).map(a->transaction(a,contexts.get(a.getTicketId()))).filter(item->term.isBlank()||transactionMatches(item,term)).toList();
        return new PageImpl<>(content,bounded,page.getTotalElements());
    }

    private List<PaymentManagementItem> items(Actor actor,List<TicketContext> tickets){
        if(tickets==null||tickets.isEmpty())return List.of();Set<UUID> ids=tickets.stream().map(TicketContext::ticketId).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));Map<UUID,List<PaymentAttempt>> grouped=group(attempts.findByTicketIdInOrderByCreatedAtDesc(ids));List<PaymentManagementItem> result=new ArrayList<>();
        for(TicketContext ticket:tickets){List<PaymentAttempt> history=grouped.getOrDefault(ticket.ticketId(),List.of());PaymentService.PaymentSnapshot snapshot=payments.snapshot(ticket.ticketId(),ticket.amountDue(),ticket.costStatus(),history);PaymentAttempt latest=history.isEmpty()?null:history.get(0);boolean active=history.stream().anyMatch(a->a.getStatus()==PaymentStatus.PENDING&&a.getExpiresAt().isAfter(Instant.now()));boolean canPay=actor.customer()&&"RESOLVED".equals(ticket.ticketStatus())&&(snapshot.status()==TicketPaymentStatus.UNPAID||snapshot.status()==TicketPaymentStatus.FAILED||(snapshot.status()==TicketPaymentStatus.PENDING&&active));Instant activity=latest==null?(ticket.costAcceptedAt()==null?ticket.submittedAt():ticket.costAcceptedAt()):latest.getCreatedAt();result.add(new PaymentManagementItem(ticket.ticketId(),ticket.ticketCode(),ticket.ticketTitle(),ticket.customerId(),ticket.customerName(),ticket.siteId(),ticket.siteName(),ticket.ticketStatus(),snapshot.status(),snapshot.amountDue(),snapshot.paidAt(),latest==null?null:latest.getCreatedAt(),activity,history.size(),canPay));}
        return result;
    }
    private Specification<PaymentAttempt> attemptFilters(String q,PaymentStatus status,Instant from,Instant to,boolean reconciliation,UUID initiatedBy){return (root,ignored,cb)->{List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();if(q!=null&&!q.isBlank()){String pattern="%"+q.trim().toLowerCase(Locale.ROOT)+"%";p.add(cb.or(cb.like(cb.lower(root.get("txnRef")),pattern),cb.like(cb.lower(root.get("providerTransactionNo")),pattern)));}if(status!=null)p.add(cb.equal(root.get("status"),status));if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("createdAt"),from));if(to!=null)p.add(cb.lessThan(root.get("createdAt"),to));if(initiatedBy!=null)p.add(cb.equal(root.get("initiatedById"),initiatedBy));if(reconciliation)p.add(cb.or(cb.equal(root.get("status"),PaymentStatus.FAILED),cb.and(cb.equal(root.get("status"),PaymentStatus.PENDING),cb.lessThan(root.get("expiresAt"),Instant.now()))));return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));};}
    private PaymentTransactionItem transaction(PaymentAttempt a,TicketContext t){return new PaymentTransactionItem(a.getId(),a.getTicketId(),t.ticketCode(),t.ticketTitle(),t.customerName(),t.siteName(),a.getTxnRef(),a.getStatus(),a.getAmount(),a.getCreatedAt(),a.getExpiresAt(),a.getCompletedAt(),a.getProviderTransactionNo(),a.getBankCode(),a.getCardType(),a.getResponseCode(),a.getLastReconciledAt());}
    private boolean transactionMatches(PaymentTransactionItem i,String term){return lower(i.txnRef()).contains(term)||lower(i.providerTransactionNo()).contains(term)||lower(i.ticketCode()).contains(term)||lower(i.ticketTitle()).contains(term);}
    private boolean elevated(Actor actor){return actor.has(RoleName.ADMIN)||actor.has(RoleName.MANAGER);}
    private long aggregateCount(List<PaymentAttemptRepository.StatusAggregate> values,PaymentStatus status){return values.stream().filter(v->v.getStatus()==status).mapToLong(PaymentAttemptRepository.StatusAggregate::getTotal).sum();}
    private BigDecimal aggregateAmount(List<PaymentAttemptRepository.StatusAggregate> values,PaymentStatus status){return values.stream().filter(v->v.getStatus()==status).map(PaymentAttemptRepository.StatusAggregate::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private void validateRange(LocalDate from,LocalDate to){if(from!=null&&to!=null&&from.isAfter(to))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAYMENT_DATE_RANGE","Ngày bắt đầu không được sau ngày kết thúc");}
    private Instant dateStart(LocalDate value){return value==null?null:value.atStartOfDay(BUSINESS_ZONE).toInstant();}
    private Instant dateEnd(LocalDate value){return value==null?null:value.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();}
    private String encodeCursor(int page){return Base64.getUrlEncoder().withoutPadding().encodeToString(String.valueOf(page).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private int decodeCursor(String cursor){if(cursor==null||cursor.isBlank())return 0;try{return Math.max(0,Integer.parseInt(new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8)));}catch(RuntimeException error){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAYMENT_CURSOR","Cursor thanh toán không hợp lệ");}}
    private Map<UUID,List<PaymentAttempt>> group(List<PaymentAttempt> values){Map<UUID,List<PaymentAttempt>> result=new HashMap<>();for(PaymentAttempt a:values)result.computeIfAbsent(a.getTicketId(),ignored->new ArrayList<>()).add(a);return result;}
    private boolean matches(TicketContext t,List<PaymentAttempt> history,String term){if(lower(t.ticketCode()).contains(term)||lower(t.ticketTitle()).contains(term))return true;return history.stream().anyMatch(a->lower(a.getTxnRef()).contains(term)||lower(a.getProviderTransactionNo()).contains(term));}
    private String lower(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
    public record CursorPage<T>(List<T> content,String nextCursor,boolean hasMore){}
    public record PaymentSummary(long outstandingCount,BigDecimal outstandingAmount,long pendingCount,long succeededCount,BigDecimal collectedAmount,long failedCount){}
    public record PaymentTransactionItem(UUID attemptId,UUID ticketId,String ticketCode,String ticketTitle,String customerName,String siteName,String txnRef,PaymentStatus status,BigDecimal amount,Instant createdAt,Instant expiresAt,Instant completedAt,String providerTransactionNo,String bankCode,String cardType,String responseCode,Instant lastReconciledAt){}
    public record PaymentManagementItem(UUID ticketId,String ticketCode,String ticketTitle,UUID customerId,String customerName,UUID siteId,String siteName,String ticketStatus,TicketPaymentStatus paymentStatus,BigDecimal amountDue,Instant paidAt,Instant latestAttemptAt,Instant activityAt,long attemptCount,boolean canPay){}
}
