package com.example.quanlybaotri.maintenance.client;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class MaintenanceClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;
    public MaintenanceClient(RestClient.Builder builder, ServiceTokenProvider tokens,
        @Value("${app.maintenance-url:http://maintenance-service:8083}") String baseUrl) {
        client=builder.baseUrl(baseUrl).build(); this.tokens=tokens;
    }
    public TicketContext ticket(UUID id,String userToken){try{return client.get().uri("/internal/v1/payments/tickets/{id}",id).headers(h->userHeaders(h,userToken)).retrieve().body(TicketContext.class);}catch(HttpClientErrorException.Forbidden ex){throw ApiException.forbidden("Không có quyền xem thanh toán của phiếu");}catch(HttpClientErrorException.NotFound ex){throw ApiException.notFound("Không tìm thấy phiếu");}catch(RestClientException ex){throw unavailable();}}
    public TicketContextPage catalogPage(String userToken,String q,Instant from,Instant to,int page,int size){try{return client.get().uri(builder->builder.path("/internal/v1/payments/tickets").queryParamIfPresent("q",optional(q)).queryParamIfPresent("from",Optional.ofNullable(from)).queryParamIfPresent("to",Optional.ofNullable(to)).queryParam("page",page).queryParam("size",size).build()).headers(h->userHeaders(h,userToken)).retrieve().body(TicketContextPage.class);}catch(RestClientException ex){throw unavailable();}}
    public List<TicketContext> resolve(String userToken,Collection<UUID> ids){if(ids==null||ids.isEmpty())return List.of();try{TicketContext[] result=client.post().uri("/internal/v1/payments/tickets/resolve").headers(h->userHeaders(h,userToken)).body(new ResolveRequest(ids)).retrieve().body(TicketContext[].class);return result==null?List.of():List.of(result);}catch(RestClientException ex){throw unavailable();}}
    public void record(UUID id,String action,String description,Map<String,Object> metadata){try{client.post().uri("/internal/v1/payments/tickets/{id}/events",id).headers(this::serviceHeaders).body(new PaymentEvent(action,description,metadata)).retrieve().toBodilessEntity();}catch(RestClientException ex){throw unavailable();}}
    private void userHeaders(HttpHeaders h,String token){serviceHeaders(h);h.set("X-User-Token",token);}
    private void serviceHeaders(HttpHeaders h){h.setBearerAuth(tokens.token("maintenance-service"));String c=MDC.get("correlationId");if(c!=null)h.set("X-Correlation-Id",c);}
    private ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE","Maintenance service không sẵn sàng");}
    public record TicketContext(UUID ticketId,String ticketCode,String ticketTitle,UUID customerId,String customerName,UUID siteId,String siteName,UUID requesterId,UUID assigneeId,String ticketStatus,Instant submittedAt,String costStatus,BigDecimal amountDue,Instant costAcceptedAt){}
    public record TicketContextPage(List<TicketContext> content,long totalElements,int totalPages,int number,int size){}
    public record ResolveRequest(Collection<UUID> ids){}
    public record PaymentEvent(String action,String description,Map<String,Object> metadata){}
    private Optional<String> optional(String value){return value==null||value.isBlank()?Optional.empty():Optional.of(value);}
}
