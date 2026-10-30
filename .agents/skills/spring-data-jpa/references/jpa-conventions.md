# JPA conventions

House rules for entities, repositories, migrations, and auditing.

---

## Entity rules

### IDs

| Scenario | Type | Generator |
|----------|------|-----------|
| Internal, auto-increment | `Long` | `GenerationType.IDENTITY` |
| Public, distributed, exposed to clients | `UUID` | `GenerationType.UUID` |
| Natural key (rare) | the natural type | assigned |

Avoid composite keys unless the domain genuinely has one. Prefer a surrogate key and a unique
constraint on the natural combination.

### Types

- Timestamps: `java.time.Instant` mapped to `TIMESTAMPTZ`.
- Dates without time: `java.time.LocalDate` mapped to `DATE`.
- Enums: `@Enumerated(EnumType.STRING)` to `VARCHAR`.
- Money: `BigDecimal` with explicit `@Column(precision = 19, scale = 4)`.
- Booleans: `Boolean` to `BOOLEAN`. Name fields positively (`active`, not `isActive`).

### Naming

- Table names singular and explicit: `@Table(name = "order")`.
- Columns snake_case in SQL, camelCase in Java. Hibernate handles the mapping.
- Entity class names are singular nouns.
- Avoid SQL reserved words (`order`, `user`) without quoting; prefer `customer_order`, `app_user`.

### Auditing

Enable with `@EnableJpaAuditing` and `@EntityListeners(AuditingEntityListener.class)`. Fields:

```java
@CreatedDate
@Column(nullable = false, updatable = false)
private Instant createdAt;

@LastModifiedDate
@Column(nullable = false)
private Instant updatedAt;
```

Never set these manually in application code.

### Lombok

Allowed on entities:
- `@Getter`
- `@Setter`
- `@NoArgsConstructor`
- `@AllArgsConstructor`
- `@Builder`

Avoid `@Data` on entities — it generates `equals`/`hashCode` over all fields, including lazy-loaded
collections, which causes subtle bugs. If you need `equals`/`hashCode`, base them on the immutable ID
only.

---

## Repository choice

| Use case | Extend |
|----------|--------|
| CRUD only, no paging | `ListCrudRepository<Entity, ID>` |
| Pagination, sorting, scrolling | `JpaRepository<Entity, ID>` |
| Custom query that returns DTOs | `Repository` fragment + `@Query` |
| Complex dynamic queries | `JpaSpecificationExecutor<Entity>` |

Default to `ListCrudRepository` unless you have a concrete need for `Page`/`Window`. It keeps the
interface small and communicates intent.

### Query methods

- Use derived query methods only when the generated SQL is obvious.
- Favour `@Query` with JPQL for anything joins, aggregates, or DTO projections.
- Never return `@Entity` from a DTO projection query — use a record or interface projection.

---

## Migration discipline

Flyway owns the schema. Rules:

1. One DDL change per migration file.
2. Migrations are immutable after applying to a shared environment. Fix bad data with new migrations,
   not by editing old ones.
3. Never use `ddl-auto: create`, `create-drop`, or `update` outside a unit test.
4. Every migration must be backward-compatible with the currently deployed app version where
   possible — additive changes only during deploys.
5. Seed data belongs in repeatable migrations (`R__seed_*.sql`) or test fixtures, not versioned
   migrations.

---

## Transactions

- Service layer methods that mutate data should be `@Transactional`.
- Read-only queries: `@Transactional(readOnly = true)`.
- Never put `@Transactional` on a controller.
- Never put `@Transactional` on a test that exercises HTTP — it runs on the test thread, not the
  request thread, and hides real transaction boundaries.

---

## Pagination

See `skills/spring-scaffold/references/pagination.md` for the house pagination pattern.

Quick rule:
- User-facing lists, feeds, infinite scroll: keyset/cursor via `Window` + `ScrollPosition`.
- Admin tables with page numbers and small datasets: offset `Page`.
