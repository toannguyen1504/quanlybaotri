package com.example.quanlybaotri.cost.application;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class InventoryCostClient {
    private final RestClient client; private final ServiceTokenProvider tokens;
    public InventoryCostClient(RestClient.Builder builder,ServiceTokenProvider tokens,@Value("${app.inventory-url:http://inventory-service:8084}") String base){this.client=builder.baseUrl(base).build();this.tokens=tokens;}
    public List<PartCostRef> parts(UUID ticketId){try{PartCostRef[] result=client.get().uri("/internal/v1/tickets/{id}/parts",ticketId).headers(h->{h.setBearerAuth(tokens.token("inventory-service"));String c=MDC.get("correlationId");if(c!=null)h.set("X-Correlation-Id",c);}).retrieve().body(PartCostRef[].class);return result==null?List.of():List.of(result);}catch(RestClientException ex){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE","Inventory service không sẵn sàng để đối soát chi phí");}}
    public record PartCostRef(UUID usageId,UUID partId,String partCode,String partName,String unit,BigDecimal quantity,BigDecimal unitCost,UUID usedById,Instant usedAt){}
}
