package com.example.quanlybaotri.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {
    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String uri,
                          @Value("${spring.application.name}") String audience,
                          StringRedisTemplate redis) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(uri).build();
        OAuth2TokenValidator<Jwt> revocation = jwt -> {
            if ("service".equals(jwt.getClaimAsString("token_type")) && !jwt.getAudience().contains(audience))
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
            try {
                return Boolean.TRUE.equals(redis.hasKey("identity:jwt:revoked:" + jwt.getId()))
                        ? OAuth2TokenValidatorResult.failure(new OAuth2Error("revoked_token"))
                        : OAuth2TokenValidatorResult.success();
            } catch (RuntimeException unavailable) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("token_validation_unavailable"));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("quanlybaotri"), revocation));
        return decoder;
    }
}
