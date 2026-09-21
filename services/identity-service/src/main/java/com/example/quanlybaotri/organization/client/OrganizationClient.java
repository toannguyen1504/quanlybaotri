package com.example.quanlybaotri.organization.client;

import com.example.quanlybaotri.identity.application.JwtService;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class OrganizationClient {

    private final RestClient client;
    private final JwtService jwt;

    public OrganizationClient(
        RestClient.Builder builder,
        JwtService jwt,
        @Value("${app.organization-url:http://organization-service:8086}") String base
    ) {
        this.client = builder.baseUrl(base).build();
        this.jwt = jwt;
    }

    public DepartmentRef find(UUID id) {
        if (id == null) return null;
        try {
            return client
                .get()
                .uri("/internal/v1/departments/{id}", id)
                .headers(h -> headers(h))
                .retrieve()
                .body(DepartmentRef.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Không tìm thấy phòng ban");
        } catch (RestClientException e) {
            throw unavailable(e);
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
            if (result != null) for (DepartmentRef d : result) map.put(d.id(), d);
            return map;
        } catch (RestClientException e) {
            throw unavailable(e);
        }
    }

    private void headers(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(jwt.issueService("identity-service", "organization-service").value());
        String c = MDC.get("correlationId");
        if (c != null) h.set("X-Correlation-Id", c);
    }

    private ApiException unavailable(Exception e) {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "DEPENDENCY_UNAVAILABLE",
            "Organization service không sẵn sàng"
        );
    }

    public record ResolveRequest(List<UUID> ids) {}

    public record DepartmentRef(UUID id, String code, String name, boolean active) {}
}
