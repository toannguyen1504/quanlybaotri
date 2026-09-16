package com.example.quanlybaotri.identity.api;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwkController {
    private final RSAKey key;
    public JwkController(RSAKey key) { this.key = key; }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        return Map.of("keys", List.of(key.toPublicJWK().toJSONObject()));
    }
}
