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

    public OrganizationClient(
        RestClient.Builder b,
        ServiceTokenProvider tokens,
        @Value("${app.organization-url:http://organization-service:8086}") String base
    ) {
        client = b.baseUrl(base).build();
        this.tokens = tokens;
    }

    public DepartmentRef find(UUID id) {
        try {
            return client
                .get()
                .uri("/internal/v1/departments/{id}", id)
                .headers(this::headers)
                .retrieve()
                .body(DepartmentRef.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Không tìm thấy phòng ban");
        } catch (RestClientException e) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE",
                "Organization service không sẵn sàng"
            );
        }
    }

    public Map<UUID, DepartmentRef> resolve(Collection<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) return Map.of();
        try {
            DepartmentRef[] result = client
                .post()
                .uri("/internal/v1/departments/resolve")
                .headers(this::headers)
                .body(new ResolveRequest(List.copyOf(unique)))
                .retrieve()
                .body(DepartmentRef[].class);
            Map<UUID, DepartmentRef> map = new HashMap<>();
            if (result != null) for (var d : result) map.put(d.id(), d);
            return map;
        } catch (RestClientException e) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE",
                "Organization service không sẵn sàng"
            );
        }
    }

    private void headers(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokens.token("organization-service"));
        String c = MDC.get("correlationId");
        if (c != null) h.set("X-Correlation-Id", c);
    }

    private record ResolveRequest(List<UUID> ids) {}

    public record DepartmentRef(UUID id, String code, String name, boolean active) {}
}
