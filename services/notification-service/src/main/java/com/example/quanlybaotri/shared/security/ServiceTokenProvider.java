package com.example.quanlybaotri.shared.security;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ServiceTokenProvider {

    private final RestClient client;
    private final String secret;
    private final Map<String, Cached> cache = new HashMap<>();

    public ServiceTokenProvider(
        RestClient.Builder b,
        @Value("${app.identity-url:http://identity-service:8081}") String base,
        @Value("${app.service-client.secret}") String secret
    ) {
        this.client = b.baseUrl(base).build();
        this.secret = secret;
    }

    public synchronized String token(String audience) {
        Instant now = Instant.now();
        Cached cached = cache.get(audience);
        if (
            cached != null &&
            cached.expiresAt.isAfter(now.plusSeconds(30))
        ) return cached.value;
        TokenResponse r = client
            .post()
            .uri(u ->
                u
                    .path("/internal/v1/auth/token")
                    .queryParam("grant_type", "client_credentials")
                    .queryParam("audience", audience)
                    .build()
            )
            .headers(h -> h.setBasicAuth("notification-service", secret))
            .retrieve()
            .body(TokenResponse.class);
        if (r == null) throw new IllegalStateException("Identity returned no service token");
        Cached issued = new Cached(r.accessToken(), now.plusSeconds(r.expiresIn()));
        cache.put(audience, issued);
        return issued.value;
    }

    private record Cached(String value, Instant expiresAt) {}

    private record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
}
