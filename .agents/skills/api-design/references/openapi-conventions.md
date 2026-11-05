# OpenAPI conventions

How to document Spring Boot 4 APIs with SpringDoc.

---

## Annotation style

- `@Tag` on controller classes, not individual methods.
- `@Operation` on every public endpoint, with a clear `summary`.
- `@Schema` on DTO fields only when the generated name or type is ambiguous.
- `@Hidden` on internal or admin endpoints that should not appear in public docs.
- `@ApiResponse` only when the status or body shape differs from the global `ProblemDetailSchema`.

---

## Versioning strategy

Use URL path versioning: `/api/v1/jobs`.

Rules:
- A new major version gets a new path: `/api/v2/jobs`.
- Keep at most two supported versions live.
- Do not version via `Accept` header or query params — those are harder to discover and cache.
- Store the version in a constant (`ApiVersion.V1`) and reuse it.

---

## DTO naming

| Direction | Suffix | Example |
|-----------|--------|---------|
| Request body | `Request` | `JobRequest` |
| Response body | `Response` | `JobResponse` |
| Patch/partial update | `Update` | `JobUpdate` |
| Query params object | `Query` | `JobQuery` |

DTOs are records with validation annotations on the request records only.

---

## ProblemDetail schema

All error responses are `application/problem+json` with this shape:

```json
{
  "type": "about:blank",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Job not found: 42",
  "instance": "/api/v1/jobs/42"
}
```

Do not document custom error POJOs. Use `ProblemDetail` everywhere.

---

## Operation ids

SpringDoc generates operationIds from method names. Keep method names concise and unique across the
API. Avoid overloaded controller methods — SpringDoc will suffix them `_1`, `_2`, which breaks client
generation.

---

## Examples

Add examples to request/response DTOs when the type alone is not enough:

```java
@Schema(example = "Backend Engineer")
private String title;
```

Avoid long multi-line examples in annotations — they reduce readability. Put complex examples in the
DTO's class-level `@Schema` or in the `docs/api-conventions.md` file.
