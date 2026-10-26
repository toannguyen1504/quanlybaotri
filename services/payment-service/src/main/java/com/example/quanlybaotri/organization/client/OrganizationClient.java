package com.example.quanlybaotri.organization.client;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class OrganizationClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public OrganizationClient(RestClient.Builder builder, ServiceTokenProvider tokens,
        @Value("${app.organization-url:http://organization-service:8086}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.tokens = tokens;
    }

    public Map<UUID, DepartmentRef> resolve(Collection<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) return Map.of();
        try {
            DepartmentRef[] result = client.post().uri("/internal/v1/customer-sites/resolve")
                .headers(headers -> {
                    headers.setBearerAuth(tokens.token("organization-service"));
                    String correlation = MDC.get("correlationId");
                    if (correlation != null) headers.set("X-Correlation-Id", correlation);
                })
                .body(new ResolveRequest(List.copyOf(unique))).retrieve().body(DepartmentRef[].class);
            Map<UUID, DepartmentRef> departments = new HashMap<>();
            if (result != null) for (DepartmentRef item : result) departments.put(item.id(), item);
            return departments;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE", "Organization service khÃ´ng sáºµn sÃ ng");
        }
    }

    private record ResolveRequest(List<UUID> ids) {}
    public record DepartmentRef(UUID id, String code, String name, UUID customerId, String customerName,
                                UUID regionId, String regionName, boolean customerActive, boolean active) {}
}

