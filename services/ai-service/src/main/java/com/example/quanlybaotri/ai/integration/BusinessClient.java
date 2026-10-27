package com.example.quanlybaotri.ai.integration;

import com.example.quanlybaotri.ai.api.AiDtos.*;
import com.example.quanlybaotri.ai.security.CurrentActor.Actor;
import com.example.quanlybaotri.ai.shared.ApiException;
import java.time.Instant;
import java.util.*;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class BusinessClient {
    private final RestClient maintenance,asset,identity; private final MeterRegistry meters;
    public BusinessClient(RestClient.Builder builder,
        @Value("${app.maintenance-url}") String maintenanceUrl,
        @Value("${app.asset-url}") String assetUrl,
        @Value("${app.identity-url}") String identityUrl,MeterRegistry meters){
        this.meters=meters;
        maintenance=builder.clone().baseUrl(maintenanceUrl).build();asset=builder.clone().baseUrl(assetUrl).build();identity=builder.clone().baseUrl(identityUrl).build();
    }
    public List<TicketRef> searchTickets(String query,String status,String view,String priority,int size,Actor actor){
        call("maintenance","search-tickets");
        try{TicketPage page=maintenance.get().uri(uri->{var b=uri.path("/api/v1/tickets").queryParam("size",size).queryParam("sort","submittedAt,desc");
                if(text(query))b.queryParam("q",query);if(text(status))b.queryParam("status",status);if(text(view))b.queryParam("view",view);if(text(priority))b.queryParam("priority",priority);return b.build();})
            .headers(h->headers(h,actor)).retrieve().body(TicketPage.class);return page==null||page.content()==null?List.of():page.content();}catch(RestClientException e){throw dependency(e,"Maintenance");}
    }
    public TicketRef ticket(UUID id,Actor actor){call("maintenance","get-ticket");try{return required(maintenance.get().uri("/api/v1/tickets/{id}",id).headers(h->headers(h,actor)).retrieve().body(TicketRef.class));}catch(RestClientException e){throw dependency(e,"Maintenance");}}
    public List<EquipmentRef> searchEquipment(String query,int size,Actor actor){
        call("asset","search-equipment");
        try{EquipmentPage page=asset.get().uri(uri->{var b=uri.path("/api/v1/equipment").queryParam("size",size).queryParam("sort","assetCode,asc");if(text(query))b.queryParam("q",query);return b.build();})
            .headers(h->headers(h,actor)).retrieve().body(EquipmentPage.class);return page==null||page.content()==null?List.of():page.content();}catch(RestClientException e){throw dependency(e,"Asset");}
    }
    public EquipmentRef equipment(UUID id,Actor actor){call("asset","get-equipment");try{return required(asset.get().uri("/api/v1/equipment/{id}",id).headers(h->headers(h,actor)).retrieve().body(EquipmentRef.class));}catch(RestClientException e){throw dependency(e,"Asset");}}
    public List<UserRef> requesters(UUID customerId,int size,Actor actor){
        call("identity","search-requesters");
        try{UserPage page=identity.get().uri(uri->uri.path("/api/v1/users").queryParam("customerId",customerId).queryParam("accountType","CUSTOMER").queryParam("size",size).queryParam("sort","fullName,asc").build())
            .headers(h->headers(h,actor)).retrieve().body(UserPage.class);return page==null||page.content()==null?List.of():page.content().stream()
                .filter(u->u.enabled()&&u.roles()!=null&&u.roles().contains("REQUESTER")).toList();}catch(RestClientException e){throw dependency(e,"Identity");}
    }
    public TicketRef create(TicketDraft draft,Actor actor){
        call("maintenance","create-ticket");
        Map<String,Object> body=new LinkedHashMap<>();body.put("equipmentId",draft.equipmentId());body.put("requesterId",actor.elevated()?draft.requesterId():null);
        body.put("title",draft.title());body.put("description",draft.description());body.put("priority",draft.priority());
        try{return required(maintenance.post().uri("/api/v1/tickets").headers(h->{headers(h,actor);h.set("Idempotency-Key","ai:"+draft.id());})
            .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(TicketRef.class));}catch(RestClientException e){throw dependency(e,"Maintenance");}
    }
    public TicketCard card(TicketRef t){return new TicketCard(t.id(),t.code(),t.title(),t.status(),t.priority(),t.equipmentCode(),t.equipmentName(),t.siteName(),t.submittedAt());}
    public EquipmentCard card(EquipmentRef e){return new EquipmentCard(e.id(),e.assetCode(),e.name(),e.serialNumber(),e.location(),e.siteName(),e.customerName(),e.status(),e.warrantyStatus(),e.active());}
    private void headers(HttpHeaders h,Actor actor){h.setBearerAuth(actor.token());String correlation=MDC.get("correlationId");if(correlation!=null)h.set("X-Correlation-Id",correlation);}
    private boolean text(String value){return value!=null&&!value.isBlank();}
    private <T>T required(T value){if(value==null)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_EMPTY_RESPONSE","Dịch vụ nghiệp vụ không trả dữ liệu");return value;}
    private ApiException dependency(RestClientException e,String service){
        meters.counter("ai.downstream.errors","service",service.toLowerCase(Locale.ROOT)).increment();
        if(e instanceof HttpClientErrorException client){HttpStatus status=HttpStatus.resolve(client.getStatusCode().value());if(status!=null&&status.is4xxClientError())return new ApiException(status,"DOWNSTREAM_REJECTED",service+" từ chối yêu cầu");}
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE",service+" service hiện không sẵn sàng");
    }
    private void call(String service,String operation){meters.counter("ai.downstream.calls","service",service,"operation",operation).increment();}
    public record TicketPage(List<TicketRef> content,long totalElements) {}
    public record EquipmentPage(List<EquipmentRef> content,long totalElements) {}
    public record UserPage(List<UserRef> content,long totalElements) {}
    public record TicketRef(UUID id,String code,UUID equipmentId,String equipmentCode,String equipmentName,UUID customerId,String customerName,
        UUID siteId,String siteName,UUID requesterId,String requesterName,String title,String description,String priority,String status,Instant submittedAt) {}
    public record EquipmentRef(UUID id,String assetCode,String name,String serialNumber,String location,UUID siteId,String siteName,UUID customerId,
        String customerName,String warrantyStatus,String status,boolean active) {}
    public record UserRef(UUID id,String email,String fullName,UUID customerId,String siteScope,Set<UUID> siteIds,boolean enabled,Set<String> roles) {
        public boolean canAccess(UUID siteId){return "ALL_SITES".equals(siteScope)||(siteIds!=null&&siteIds.contains(siteId));}
    }
}
