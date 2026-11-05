# Security conventions

JWT resource-server patterns for Spring Boot 4 / Spring Security 6.

---

## Authority naming

- Spring Security expects roles prefixed with `ROLE_` when using `hasRole("ADMIN")`.
- Use `hasAuthority("SCOPE_jobs:read")` for OAuth2 scopes.
- Keep claims from the token close to the token format; map them to authorities in one place only
  (`JwtAuthConverter`).

| Token claim | Mapped authority | Used with |
|-------------|------------------|-----------|
| `roles: ["admin"]` | `ROLE_ADMIN` | `hasRole("ADMIN")` |
| `permissions: ["jobs:read"]` | `jobs:read` | `hasAuthority("jobs:read")` |
| `scope: "read write"` | `SCOPE_read`, `SCOPE_write` | `hasAuthority("SCOPE_read")` |

---

## Public paths

Default public paths in generated config:

- `/actuator/health`
- `/actuator/info`
- `/api/public/**`

Everything else is authenticated by default. Adjust per project, but never leave `.anyRequest().permitAll()`.

---

## Nested JWT claims

If roles are nested, e.g. Keycloak's `realm_access.roles`:

```java
@SuppressWarnings("unchecked")
private List<String> extractRoles(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaim("realm_access");
    if (realmAccess == null) return List.of();
    return (List<String>) realmAccess.getOrDefault("roles", List.of());
}
```

Always guard against missing claims — never throw from the converter.

---

## Method security

Enable with `@EnableMethodSecurity`, then annotate service methods:

```java
@PreAuthorize("hasRole('ADMIN') or @jobAuthorizationService.owns(#id)")
public JobResponse updateJob(Long id, JobRequest request) { ... }
```

Rules:
- Method security on service layer, not controllers.
- Use `@PreAuthorize` for pre-conditions, `@PostAuthorize` sparingly.
- For ownership checks, delegate to a bean method (`@jobAuthorizationService`) rather than inlining
  repository calls in SpEL.

---

## Testing patterns

| What you're testing | Tool |
|---------------------|------|
| Path security rules | `@AutoConfigureMockMvc` + `MockMvc` |
| Method security | `@WithMockUser` on a service test |
| JWT claim conversion | Unit test the `JwtAuthConverter` with a mocked `Jwt` |
| End-to-end with real token | `RestTestClient` + test `JwtDecoder` bean |

`@WithMockUser` creates a simple `UsernamePasswordAuthenticationToken`. It does not exercise JWT
decoding, so it's good for path/method security, not for claim-converter logic.

---

## Common mistakes

- `csrf().disable()` is correct for stateless JWT APIs; there is no session to forge.
- `SessionCreationPolicy.STATELESS` is required for JWT resource servers.
- Do not mix `permitAll()` with method security on the same endpoint unless you understand the
  interaction — `permitAll()` wins at the filter level.
- Never hardcode secrets, public keys, or issuer URIs in `application.yml`. Use env vars or external
  secret management.
