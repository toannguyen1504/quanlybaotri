package com.example.quanlybaotri.payment.client;

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
public class PaymentClient {
    private final RestClient client; private final ServiceTokenProvider tokens;
    public PaymentClient(RestClient.Builder builder,ServiceTokenProvider tokens,@Value("${app.payment-url:http://payment-service:8087}") String base){client=builder.baseUrl(base).build();this.tokens=tokens;}
    public void ensureSettled(UUID id,BigDecimal amount){try{client.get().uri(u->u.path("/internal/v1/payments/tickets/{id}/settled").queryParam("amountDue",amount).build(id)).headers(this::headers).retrieve().toBodilessEntity();}catch(HttpClientErrorException.Conflict ex){throw ApiException.conflict("Phiếu phải được thanh toán trước khi đóng");}catch(RestClientException ex){throw unavailable();}}
    public void ensureReopenAllowed(UUID id){try{client.get().uri("/internal/v1/payments/tickets/{id}/reopen-allowed",id).headers(this::headers).retrieve().toBodilessEntity();}catch(HttpClientErrorException.Conflict ex){throw ApiException.conflict("Không thể mở lại phiếu khi giao dịch đã hoặc đang được thanh toán");}catch(RestClientException ex){throw unavailable();}}
    public Map<UUID,PaymentSnapshot> snapshots(Collection<CostRef> costs){if(costs.isEmpty())return Map.of();try{PaymentSnapshot[] response=client.post().uri("/internal/v1/payments/snapshots").headers(this::headers).body(new SnapshotRequest(List.copyOf(costs))).retrieve().body(PaymentSnapshot[].class);Map<UUID,PaymentSnapshot> result=new HashMap<>();if(response!=null)for(PaymentSnapshot item:response)result.put(item.ticketId(),item);return result;}catch(RestClientException ex){throw unavailable();}}
    private void headers(HttpHeaders h){h.setBearerAuth(tokens.token("payment-service"));String c=MDC.get("correlationId");if(c!=null)h.set("X-Correlation-Id",c);}
    private ApiException unavailable(){return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE","Payment service không sẵn sàng");}
    private record SnapshotRequest(List<CostRef> costs){}
    public record CostRef(UUID ticketId,String costStatus,BigDecimal amountDue){}
    public record PaymentSnapshot(UUID ticketId,TicketPaymentStatus status,BigDecimal amountDue,Instant paidAt){}
}
