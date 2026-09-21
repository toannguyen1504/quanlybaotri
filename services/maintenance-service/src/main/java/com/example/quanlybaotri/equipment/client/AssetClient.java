package com.example.quanlybaotri.equipment.client;

import com.example.quanlybaotri.equipment.domain.EquipmentStatus;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.ServiceTokenProvider;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

@Component
public class AssetClient {

    private final RestClient client;
    private final ServiceTokenProvider tokens;

    public AssetClient(
        RestClient.Builder builder,
        ServiceTokenProvider tokens,
        @Value("${app.asset-url:http://asset-service:8082}") String base
    ) {
        this.client = builder.baseUrl(base).build();
        this.tokens = tokens;
    }

    public EquipmentRef get(UUID id) {
        try {
            return client
                .get()
                .uri("/internal/v1/equipment/{id}", id)
                .headers(this::headers)
                .retrieve()
                .body(EquipmentRef.class);
        } catch (HttpClientErrorException.NotFound ex) {
            throw ApiException.notFound("Không tìm thấy thiết bị");
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public Map<UUID, EquipmentRef> resolve(Collection<UUID> ids) {
        Set<UUID> unique = new LinkedHashSet<>(ids);
        unique.remove(null);
        if (unique.isEmpty()) return Map.of();
        try {
            EquipmentRef[] result = client
                .post()
                .uri("/internal/v1/equipment/resolve")
                .headers(this::headers)
                .body(new ResolveRequest(List.copyOf(unique)))
                .retrieve()
                .body(EquipmentRef[].class);
            Map<UUID, EquipmentRef> map = new HashMap<>();
            if (result != null) for (EquipmentRef item : result) map.put(item.id(), item);
            return map;
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    private void headers(org.springframework.http.HttpHeaders headers) {
        headers.setBearerAuth(tokens.token("asset-service"));
        String correlation = MDC.get("correlationId");
        if (correlation != null) headers.set("X-Correlation-Id", correlation);
    }

    private ApiException unavailable() {
        return new ApiException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "DEPENDENCY_UNAVAILABLE",
            "Asset service không sẵn sàng"
        );
    }

    public record ResolveRequest(List<UUID> ids) {}

    public record EquipmentRef(
        UUID id,
        String assetCode,
        String name,
        UUID categoryId,
        String categoryName,
        UUID departmentId,
        EquipmentStatus status,
        boolean active
    ) {}
}
