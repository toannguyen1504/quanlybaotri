# Test conventions — Spring Boot 4 / Spring Framework 7

House rules, plus the Boot 4 API surface. Everything in "Dead and relocated APIs" was verified
against the 4.1.1 / Framework 7 jars.

---

## What gets which kind of test

| The thing under test | Test kind | Why |
|---|---|---|
| `@Repository` — any query, any mapping, any migration | **Integration**, real Postgres via Testcontainers | A mocked repository proves your mock works, not your SQL |
| Service with IO dependencies (repo, HTTP client, broker) | **Integration** | Same reason — the wiring is the risk |
| Service with pure logic, no IO | **Unit**, plain JUnit, no Spring context | A context start costs seconds and proves nothing here |
| Controller request/response contract, validation, status codes | **Slice** — `@WebMvcTest` + `MockMvcTester` | The HTTP edge without a database |
| Full request → DB → response flow | **Integration** on `RANDOM_PORT` + `RestTestClient` | The only test that proves the slices agree |
| Outbound HTTP client (`@HttpExchange`, `RestClient`) | **Slice** — `@RestClientTest` + `MockRestServiceServer` | Asserts the request you send, not the server you don't own |
| Mapper / DTO conversion, `ProblemDetail` shaping | **Unit** | Pure functions |

Naming follows the split: `*IntegrationTest` for anything with a container or a Spring context that
touches IO, `*Test` for unit and slice tests.

---

## Dead and relocated APIs

Boot 4 removed the two annotations most tutorials still lead with, and moved `TestRestTemplate` into
a new module. An import copied from a Boot 3 example will not compile — or worse, will look right in
a diff.

| Boot 3 / pre-4 | Boot 4.1 |
|---|---|
| `@MockBean` | **removed** → `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`) |
| `@SpyBean` | **removed** → `@MockitoSpyBean` (same package) |
| `org.springframework.boot.test.web.client.TestRestTemplate` | **moved** → `org.springframework.boot.resttestclient.TestRestTemplate` |
| `TestRestTemplate` as the default full-server client | `RestTestClient` (`org.springframework.test.web.servlet.client`), auto-configured by `@AutoConfigureRestTestClient` — matches the `RestClient`-over-`RestTemplate` house rule |
| `MockMvc` + `andExpect(...)` matchers | `MockMvcTester` (`org.springframework.test.web.servlet.assertj`) — AssertJ assertions, auto-configured alongside `@AutoConfigureMockMvc` |
| `spring-boot-starter-test` declared directly | per-module slices (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, …) which pull it in transitively — AssertJ, Mockito, JUnit 5, Awaitility all still arrive |

Sweep for the dead ones before writing anything new:

```bash
grep -rn --include=*.java -e '@MockBean' -e '@SpyBean' -e 'boot.test.web.client.TestRestTemplate' src/test/java || true
```

---

## Mocking

**Never mock persistence.** No `@MockitoBean` on a repository, no in-memory H2 standing in for
Postgres. If the database is mocked the test is lying — it asserts the mock's script, and every
mapping, constraint, cascade, and migration error survives it.

`@MockitoBean` is legitimate for exactly one thing: an out-of-process collaborator you do not own and
cannot run locally (a paid third-party API, a partner's service). Everything you own gets a real
container.

Unit tests take their dependencies as constructor arguments — no Spring context, no annotations. If a
service is hard to unit test, it has too many IO dependencies, which is a design signal, not a
testing problem.

---

## Test data

- `@Sql` for setup, per test class or per method. Never `data.sql` / `import.sql` on the classpath —
  classpath data leaks into every test in the run and turns failures into archaeology.
- Flyway migrations run against the container, so tests exercise the real schema. `ddl-auto: validate`
  stays on in tests; Hibernate never owns the schema, not even a test one.
- Build entities through a small factory method in the test class, not a shared `TestData` god-object
  that every test quietly depends on.

---

## Isolation

- **No `@Transactional` on an integration test that goes over HTTP.** The rollback happens on the test
  thread; the server runs on another and sees none of your setup data. It also hides flush-time
  constraint violations that production would hit.
- `@Transactional` on a repository-level test is fine and is the fastest way to keep runs independent.
- Otherwise clean up explicitly — `@Sql` with a truncate script in `executionPhase = AFTER_TEST_METHOD`.
- Static container, static state: nothing else static. A shared mutable field across test methods is
  the most common source of "passes alone, fails in the suite".

---

## `@DataJpaTest` with a container

`@DataJpaTest` replaces the datasource with an embedded one by default, which quietly undoes the
Testcontainers setup:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JobRepositoryIntegrationTest extends BaseIntegrationTest { }
```

If a repository test suddenly runs in milliseconds, it is on an embedded database, not Postgres.

---

## Assertions

- AssertJ (`assertThat`), not JUnit's `assertEquals` and not Hamcrest.
- One assertion *concept* per test — several `assertThat` lines describing one behaviour is fine; two
  unrelated behaviours in one method is not.
- Assert on the failure too: for error paths, assert the `ProblemDetail` `status`, `title`, and
  `detail` — not just that "something threw". (`type` stays `about:blank` unless the project defines
  its own type URIs, so asserting it proves nothing.)
- Test method names read as documentation: `should_<expected>_when_<condition>`.

---

## Flakiness

- Never `Thread.sleep`. Use Awaitility (`await().atMost(...).untilAsserted(...)`) — it is already on
  the test classpath via `spring-boot-starter-test`.
- Inject a fixed `Clock` bean anywhere the code reads time. A test that fails at month boundaries is
  a design bug, not a flake.
- Never assert on the order of an unordered query. Add the `ORDER BY` you actually want, or assert
  with `containsExactlyInAnyOrder`.
- Random ports and container-mapped ports only — never a hardcoded `localhost:8080`.

---

## What not to test

Getters, Lombok output, framework behaviour, and mapping code with no logic. A test that would only
break if Spring itself broke is maintenance cost with no signal.
