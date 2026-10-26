package com.example.quanlybaotri.payment.api;

import com.example.quanlybaotri.cost.domain.*;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.application.TicketEventService;
import com.example.quanlybaotri.ticket.domain.MaintenanceTicket;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/payments/tickets")
public class InternalPaymentContextController {
    private final TicketRepository tickets;private final TicketCostSummaryRepository costs;private final OrganizationClient organizations;private final JwtDecoder decoder;private final TicketEventService events;
    public InternalPaymentContextController(TicketRepository tickets,TicketCostSummaryRepository costs,OrganizationClient organizations,JwtDecoder decoder,TicketEventService events){this.tickets=tickets;this.costs=costs;this.organizations=organizations;this.decoder=decoder;this.events=events;}
    @GetMapping("/{id}") @Transactional(readOnly=true)
    public TicketContext get(@PathVariable UUID id,@RequestHeader("X-User-Token") String token){MaintenanceTicket ticket=tickets.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy phiếu"));Actor actor=actor(token);visible(ticket,actor);TicketCostSummary cost=costs.findById(id).orElse(null);Map<UUID,OrganizationClient.DepartmentRef> sites=organizations.resolve(List.of(ticket.getSiteId()));return view(ticket,cost,sites.get(ticket.getSiteId()));}
    @GetMapping @Transactional(readOnly=true)
    public Page<TicketContext> list(
        @RequestHeader("X-User-Token") String token,
        @RequestParam(required=false) String q,
        @RequestParam(required=false) Instant from,
        @RequestParam(required=false) Instant to,
        @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="50") int size
    ){
        Actor actor=actor(token);
        Pageable pageable=PageRequest.of(Math.max(0,page),Math.min(Math.max(size,1),200),Sort.by(Sort.Direction.DESC,"submittedAt").and(Sort.by(Sort.Direction.DESC,"id")));
        Page<MaintenanceTicket> result=tickets.findAll(paymentFilters(actor,q,from,to),pageable);
        Map<UUID,TicketCostSummary> byId=new HashMap<>();costs.findAllById(result.getContent().stream().map(MaintenanceTicket::getId).toList()).forEach(c->byId.put(c.getTicketId(),c));
        Map<UUID,OrganizationClient.DepartmentRef> sites=organizations.resolve(result.getContent().stream().map(MaintenanceTicket::getSiteId).toList());
        return result.map(t->view(t,byId.get(t.getId()),sites.get(t.getSiteId())));
    }
    @PostMapping("/resolve") @Transactional(readOnly=true)
    public List<TicketContext> resolve(@RequestHeader("X-User-Token") String token,@RequestBody ResolveRequest request){
        Actor actor=actor(token);List<MaintenanceTicket> visible=tickets.findAllById(request.ids()).stream().filter(t->canSee(t,actor)).toList();
        Map<UUID,TicketCostSummary> byId=new HashMap<>();costs.findAllById(visible.stream().map(MaintenanceTicket::getId).toList()).forEach(c->byId.put(c.getTicketId(),c));
        Map<UUID,OrganizationClient.DepartmentRef> sites=organizations.resolve(visible.stream().map(MaintenanceTicket::getSiteId).toList());
        return visible.stream().map(t->view(t,byId.get(t.getId()),sites.get(t.getSiteId()))).toList();
    }
    @PostMapping("/{id}/events") @PreAuthorize("authentication.name == 'payment-service'") @Transactional
    public void event(@PathVariable UUID id,@RequestBody PaymentEvent request){MaintenanceTicket ticket=tickets.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy phiếu"));events.record(ticket,null,request.action(),ticket.getStatus(),ticket.getStatus(),request.description(),request.metadata());}
    private TicketContext view(MaintenanceTicket t,TicketCostSummary c,OrganizationClient.DepartmentRef site){return new TicketContext(t.getId(),t.getCode(),t.getTitle(),t.getCustomerId(),site==null?null:site.customerName(),t.getSiteId(),site==null?null:site.name(),t.getRequesterId(),t.getAssigneeId(),t.getStatus().name(),t.getSubmittedAt(),c==null?null:c.getStatus().name(),c==null?null:c.getGrandTotal(),c==null?null:c.getAcceptedAt());}
    private Actor actor(String token){Jwt jwt=decoder.decode(token);if(!"user".equals(jwt.getClaimAsString("token_type"))||jwt.getClaimAsString("uid")==null)throw ApiException.forbidden("Yêu cầu user token được chuyển tiếp");List<String> roles=jwt.getClaimAsStringList("roles"),sites=jwt.getClaimAsStringList("site_ids");return new Actor(UUID.fromString(jwt.getClaimAsString("uid")),roles==null?Set.of():Set.copyOf(roles),jwt.getClaimAsString("account_type"),jwt.getClaimAsString("customer_id"),jwt.getClaimAsString("site_scope"),sites==null?Set.of():Set.copyOf(sites));}
    private void visible(MaintenanceTicket t,Actor a){if(!canSee(t,a))throw ApiException.forbidden("Không có quyền xem thanh toán của phiếu");}
    private boolean canSee(MaintenanceTicket t,Actor a){if(a.roles().contains("ROLE_ADMIN")||a.roles().contains("ROLE_MANAGER"))return true;if(a.roles().contains("ROLE_TECHNICIAN")&&a.id().equals(t.getAssigneeId()))return true;if("CUSTOMER".equals(a.accountType()))return t.getCustomerId().toString().equals(a.customerId())&&("ALL_SITES".equals(a.siteScope())||a.siteIds().contains(t.getSiteId().toString()));return a.id().equals(t.getRequesterId());}
    private Specification<MaintenanceTicket> paymentFilters(Actor actor,String q,Instant from,Instant to){return (root,query,cb)->{
        List<jakarta.persistence.criteria.Predicate> predicates=new ArrayList<>();
        var accepted=query.subquery(UUID.class);var cost=accepted.from(TicketCostSummary.class);accepted.select(cost.get("ticketId"));
        List<jakarta.persistence.criteria.Predicate> costPredicates=new ArrayList<>();costPredicates.add(cb.equal(cost.get("status"),CostStatus.ACCEPTED));
        if(from!=null)costPredicates.add(cb.greaterThanOrEqualTo(cost.get("acceptedAt"),from));if(to!=null)costPredicates.add(cb.lessThan(cost.get("acceptedAt"),to));
        accepted.where(costPredicates.toArray(jakarta.persistence.criteria.Predicate[]::new));predicates.add(root.get("id").in(accepted));
        if(q!=null&&!q.isBlank()){String pattern="%"+q.trim().toLowerCase(Locale.ROOT)+"%";predicates.add(cb.or(cb.like(cb.lower(root.get("code")),pattern),cb.like(cb.lower(root.get("title")),pattern)));}
        boolean elevated=actor.roles().contains("ROLE_ADMIN")||actor.roles().contains("ROLE_MANAGER");
        if(!elevated&&actor.roles().contains("ROLE_TECHNICIAN"))predicates.add(cb.equal(root.get("assigneeId"),actor.id()));
        else if(!elevated&&"CUSTOMER".equals(actor.accountType())){predicates.add(cb.equal(root.get("customerId"),UUID.fromString(actor.customerId())));if(!"ALL_SITES".equals(actor.siteScope()))predicates.add(root.get("siteId").in(actor.siteIds().stream().map(UUID::fromString).toList()));}
        else if(!elevated)predicates.add(cb.equal(root.get("requesterId"),actor.id()));
        return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };}
    private record Actor(UUID id,Set<String> roles,String accountType,String customerId,String siteScope,Set<String> siteIds){}
    public record TicketContext(UUID ticketId,String ticketCode,String ticketTitle,UUID customerId,String customerName,UUID siteId,String siteName,UUID requesterId,UUID assigneeId,String ticketStatus,Instant submittedAt,String costStatus,BigDecimal amountDue,Instant costAcceptedAt){}
    public record PaymentEvent(String action,String description,Map<String,Object> metadata){}
    public record ResolveRequest(List<UUID> ids){public ResolveRequest{ids=ids==null?List.of():List.copyOf(ids);}}
}
