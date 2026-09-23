package com.example.quanlybaotri.identity.client;

import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class IdentityClient {

    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public IdentityClient(
        RestClient.Builder b,
        ServiceTokenProvider tokens,
        @Value("${app.identity-url:http://identity-service:8081}") String base
    ) {
        client = b.baseUrl(base).build();
        this.tokens = tokens;
    }

    public Set<UUID> enabledByRoles(Collection<String> roles) {
        UserRef[] result = client
            .post()
            .uri("/internal/v1/users/search")
            .headers(this::headers)
            .body(new SearchRequest(new LinkedHashSet<>(roles), true))
            .retrieve()
            .body(UserRef[].class);
        Set<UUID> ids = new LinkedHashSet<>();
        if (result != null) for (UserRef u : result) ids.add(u.id());
        return ids;
    }

    public UserRef get(UUID id) {
        return client
            .get()
            .uri("/internal/v1/users/{id}", id)
            .headers(this::headers)
            .retrieve()
            .body(UserRef.class);
    }

    private void headers(org.springframework.http.HttpHeaders h) {
        h.setBearerAuth(tokens.token("identity-service"));
        String c = MDC.get("correlationId");
        if (c != null) h.set("X-Correlation-Id", c);
    }

    private record SearchRequest(Set<String> roles, boolean enabledOnly) {}

    public record UserRef(
        UUID id,
        String username,
        String fullName,
        String email,
        UUID departmentId,
        boolean enabled,
        Set<String> roles
    ) {
        public boolean isEnabledTechnician() {
            return enabled && roles != null && roles.contains("TECHNICIAN");
        }
    }
}
