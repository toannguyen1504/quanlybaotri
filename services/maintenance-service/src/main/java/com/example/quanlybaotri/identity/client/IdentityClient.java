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
        RestClient.Builder builder,
        ServiceTokenProvider tokens,
        @Value("${app.identity-url:http://identity-service:8081}") String base
    ) {
        this.client = builder.baseUrl(base).build();
        this.tokens = tokens;
    }

    public UserRef get(UUID id) {
        try {
            return client
                .get()
                .uri("/internal/v1/users/{id}", id)
                .headers(this::headers)
                .retrieve()
                .body(UserRef.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw ApiException.notFound("Không tìm thấy người dùng");
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public Map<UUID, UserRef> resolve(Collection<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) return Map.of();
        try {
            UserRef[] result = client
                .post()
                .uri("/internal/v1/users/resolve")
                .headers(this::headers)
                .body(new ResolveRequest(List.copyOf(unique)))
                .retrieve()
                .body(UserRef[].class);
            Map<UUID, UserRef> map = new HashMap<>();
            if (result != null) for (UserRef user : result) map.put(user.id(), user);
            return map;
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private void headers(org.springframework.http.HttpHeaders headers) {
        headers.setBearerAuth(tokens.token("identity-service"));
        String correlation = MDC.get("correlationId");
        if (correlation != null) headers.set("X-Correlation-Id", correlation);
    }

    private ApiException unavailable() {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "DEPENDENCY_UNAVAILABLE",
            "Identity service không sẵn sàng"
        );
    }

    public record ResolveRequest(List<UUID> ids) {}

    public record UserRef(
        UUID id,
        String username,
        String fullName,
        boolean enabled,
        Set<RoleName> roles
    ) {}
}
