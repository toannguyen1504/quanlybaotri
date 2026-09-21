package com.example.quanlybaotri.identity.client;

import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class IdentityClient {

    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public IdentityClient(
        RestClient.Builder b,
        ServiceTokenProvider t,
        @Value("${app.identity-url:http://identity-service:8081}") String base
    ) {
        client = b.baseUrl(base).build();
        tokens = t;
    }

    public Map<UUID, UserRef> resolve(Collection<UUID> ids) {
        Set<UUID> u = new LinkedHashSet<>(ids);
        u.remove(null);
        if (u.isEmpty()) return Map.of();
        try {
            UserRef[] r = client
                .post()
                .uri("/internal/v1/users/resolve")
                .headers(this::headers)
                .body(new Request(List.copyOf(u)))
                .retrieve()
                .body(UserRef[].class);
            Map<UUID, UserRef> out = new HashMap<>();
            if (r != null) for (UserRef v : r) out.put(v.id(), v);
            return out;
        } catch (RestClientException e) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "DEPENDENCY_UNAVAILABLE",
                "Identity service không sẵn sàng"
            );
        }
    }

    private void headers(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokens.token("identity-service"));
        String c = MDC.get("correlationId");
        if (c != null) h.set("X-Correlation-Id", c);
    }

    public record Request(List<UUID> ids) {}

    public record UserRef(
        UUID id,
        String username,
        String fullName,
        boolean enabled,
        Set<RoleName> roles
    ) {}
}
