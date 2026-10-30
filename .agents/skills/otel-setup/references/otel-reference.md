# OpenTelemetry reference — Spring Boot 4

Property map, version matrix, and troubleshooting for `spring-boot-starter-opentelemetry`.

Recipe source: [danvega/ot](https://github.com/danvega/ot) and the
[Spring blog post](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot), with the
version-matching rule and the `@Observed` gotcha below added from the 4.1.1 metadata.

---

## Which OpenTelemetry approach

| Approach | What it costs | When it wins |
|---|---|---|
| **`spring-boot-starter-opentelemetry`** (house default) | Only exports what Micrometer already instruments | Official Spring support, stable deps, no agent to ship. Correct for any normal Spring app |
| **OpenTelemetry Java agent** | A `-javaagent` flag to ship and version-match | Non-Spring libraries do your IO, or you need the 150+ bytecode instrumentations. **Does not work on GraalVM native image** |
| Community `opentelemetry-spring-boot-starter` | Pulls alpha dependencies | Rarely — the Spring starter covers the same ground with stable ones |

The protocol is what matters, not the library: Boot instruments with Micrometer and exports over OTLP,
so any OTLP backend works.

**Actuator is separate.** The OTel starter does not bring health endpoints, and Actuator is not
required for telemetry. Most production apps run both — Actuator for `/actuator/health` liveness and
readiness probes, the starter for telemetry.

---

## What the starter actually brings

`spring-boot-starter-opentelemetry` pulls `spring-boot-starter-micrometer-metrics`,
`spring-boot-micrometer-tracing-opentelemetry`, `spring-boot-opentelemetry`,
`micrometer-registry-otlp`, `micrometer-tracing-bridge-otel`, and `opentelemetry-exporter-otlp`.

Automatic instrumentation covers HTTP server requests, HTTP clients (`RestClient`, `RestTemplate`,
`WebClient`), JDBC calls, and trace/span IDs in the console log pattern.

It brings **no Logback appender** — that is the gap Step 3 of the skill closes.

---

## Property map — three prefixes, three export paths

| Signal | Property | Path |
|---|---|---|
| Metrics | `management.otlp.metrics.export.url` | Micrometer `OtlpMeterRegistry` |
| Traces | `management.opentelemetry.tracing.export.otlp.endpoint` | OTel SDK |
| Logs | `management.opentelemetry.logging.export.otlp.endpoint` | OTel SDK |

Metrics go through Micrometer's registry; traces and logs through the OTel SDK. Same collector, two
different integrations, hence two prefixes. The flat `management.otlp.tracing.*` and
`management.otlp.logging.*` keys are the deprecated pre-4.1 spellings — never generate them.

Port 4318 is OTLP/HTTP, 4317 is OTLP/gRPC.

**Switches:** `management.otlp.metrics.export.enabled`, `management.tracing.export.enabled`,
`management.logging.export.otlp.enabled`.

**Sampling:** `management.tracing.sampling.probability` defaults to **0.1** — 90% of traces vanish
silently. Always set it explicitly: `1.0` in dev, a deliberate value in production.

**Production knobs** (all real 4.1 properties, all unset by default):

```yaml
management:
  opentelemetry:
    resource-attributes:                    # service.name comes from spring.application.name
      deployment.environment: production    # without this, every env looks identical in Tempo
      service.version: ${APP_VERSION:dev}
    logging:
      export:
        otlp:
          headers:
            Authorization: Basic ${GRAFANA_CLOUD_TOKEN}   # vendor auth
          ssl:
            bundle: otel                    # Boot SSL bundle for mTLS
        max-batch-size: 512
        schedule-delay: 5s
```

---

## Log export version matrix

The Logback appender is **not** managed by Boot's BOM — Boot imports `opentelemetry-bom` (API/SDK),
not `opentelemetry-instrumentation-bom-alpha`. You pin the version yourself, and the version matters:
each instrumentation release targets one OTel API version, while Boot's BOM pins another. Pick the
appender release whose API target **matches** what Boot pins.

| Boot | `opentelemetry.version` (BOM) | Appender to pin |
|---|---|---|
| 4.1.1 | 1.62.0 | `2.28.1-alpha` |

Recompute for any other Boot version:

```bash
# 1. what OTel API does this Boot pin?
curl -s "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/<BOOT>/spring-boot-dependencies-<BOOT>.pom" \
  | grep -o '<opentelemetry.version>[^<]*'

# 2. which appender release targets that API? (walk back from the newest)
for v in $(curl -s "https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-logback-appender-1.0/maven-metadata.xml" \
    | grep -o '<version>[^<]*' | sed 's/<version>//' | tail -6); do
  printf '%-16s api=' "$v"
  curl -s "https://repo1.maven.org/maven2/io/opentelemetry/instrumentation/opentelemetry-logback-appender-1.0/$v/opentelemetry-logback-appender-1.0-$v.pom" \
    | python3 -c "import sys,re; t=sys.stdin.read(); m=re.search(r'<artifactId>opentelemetry-api</artifactId>\s*<version>(.*?)</version>', t); print(m.group(1) if m else '?')"
done
```

Every release of this artifact carries `-alpha`; there is no stable line, so `-alpha` here is not a
reason to reach for an older version. Taking the newest instead of the matched one leaves an appender
compiled against a newer API than the SDK on the classpath — it usually works and fails at runtime
with `NoSuchMethodError` when it doesn't.

**Boot 4.0.1 is the floor for log export.** In 4.0.0 the OTLP logging auto-configuration lived in the
actuator module, so exporting logs meant adding `spring-boot-starter-actuator`
([spring-boot#48488](https://github.com/spring-projects/spring-boot/issues/48488)). It moved into the
core OpenTelemetry module in 4.0.1.

---

## Custom instrumentation

`@Observed` needs **two** things, and Boot ships neither by default:

```yaml
management:
  observations:
    annotations:
      enabled: true    # defaults to false — without this @Observed is silently inert
```

plus `spring-boot-starter-aop` on the classpath (`ObservedAspectConfiguration` is
`@ConditionalOnClass(org.aspectj.weaver.Advice)`). With both, Boot registers the `ObservedAspect`
bean itself — do not declare one.

```java
@Observed(name = "invoice.settlement")
public void settle(Invoice invoice) { }
```

For business metrics, inject `MeterRegistry` and register counters and timers directly. Prefer a few
well-named metrics over annotating everything.

---

## Backends

**Local dev via Docker Compose** — with `spring-boot-docker-compose` on the classpath and a
`grafana/otel-lgtm` service in `compose.yaml`, Boot discovers the container and configures all three
OTLP endpoints. Do not also hardcode endpoint properties: the connection details are what should win.

**Local dev via Testcontainers** — `LgtmStackContainer` with `@ServiceConnection` in
`TestcontainersConfiguration`, run through `./mvnw spring-boot:test-run`. Same rule about not
hardcoding endpoints. See the `spring-testing` skill for the container details.

**Anywhere else** — set `OTEL_EXPORTER_OTLP_ENDPOINT` and let `application.yml` read it.

The LGTM image is Loki (logs), Grafana (dashboards), Tempo (traces), and Prometheus standing in for
Mimir (metrics), behind an OTel Collector on 4317/4318. ~1 GB on first pull.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Traces appear, logs never do | No Logback appender — the endpoint is configured but nothing feeds the SDK | The three pieces in Step 3 |
| Logs export but carry no trace ID | Appender installed before the `OpenTelemetry` bean existed | Install from an `InitializingBean`, not a static block |
| Only ~10% of traces arrive | `management.tracing.sampling.probability` left at its 0.1 default | Set it explicitly |
| Service shows as `unknown_service:java` | `spring.application.name` unset | Set it; it feeds `service.name` |
| Every environment looks identical in Tempo | No `deployment.environment` resource attribute | `management.opentelemetry.resource-attributes` |
| `@Observed` produces no span | `management.observations.annotations.enabled` is false by default, or AOP is missing | Set the property, add `spring-boot-starter-aop` |
| Endpoint properties ignored under compose or `test-run` | Connection details outrank properties — working as intended | Delete the hardcoded endpoints |
| Connection-refused warnings on startup | Nothing listening on 4318 | Start the backend, or disable export for that run |
| Nothing instrumented despite the starter | A non-Spring library does the IO — the starter only exports what Micrometer instruments | The Java agent, unless on GraalVM native |
