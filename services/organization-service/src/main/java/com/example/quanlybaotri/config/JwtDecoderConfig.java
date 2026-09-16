package com.example.quanlybaotri.config;

import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;import org.springframework.data.redis.core.StringRedisTemplate;import org.springframework.security.oauth2.core.*;import org.springframework.security.oauth2.jwt.*;
@Configuration public class JwtDecoderConfig{
 @Bean JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")String uri,@Value("${spring.application.name}")String audience,StringRedisTemplate redis){
  NimbusJwtDecoder d=NimbusJwtDecoder.withJwkSetUri(uri).build();OAuth2TokenValidator<Jwt> custom=jwt->{
   if("service".equals(jwt.getClaimAsString("token_type"))&&!jwt.getAudience().contains(audience))return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_audience"));
   try{return Boolean.TRUE.equals(redis.hasKey("identity:jwt:revoked:"+jwt.getId()))?OAuth2TokenValidatorResult.failure(new OAuth2Error("revoked_token")):OAuth2TokenValidatorResult.success();}catch(RuntimeException e){return OAuth2TokenValidatorResult.failure(new OAuth2Error("token_validation_unavailable"));}};
  d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("quanlybaotri"),custom));return d;}
}
