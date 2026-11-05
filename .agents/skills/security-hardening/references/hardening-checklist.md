# Security hardening checklist

DevSecOps configuration and triage guide.

---

## Dependency Check

### When it fails

A failing build means at least one dependency has a CVE with CVSS >= the configured threshold
(default 7). Do not lower the threshold to make the build green.

### Triage steps

1. Open `target/dependency-check-report.html`.
2. Find the CVE and the affected library.
3. Check if a patched version exists. If yes, upgrade.
4. If it's a false positive or not exploitable in your context, suppress it with a clear `notes`
   explanation.

### Suppression template

```xml
<suppress>
    <notes>
        CVE-20XX-XXXXX affects the FTP client in example-lib. This service only uses the HTTP
        client, so the vulnerable code path is unreachable.
    </notes>
    <cve>CVE-20XX-XXXXX</cve>
</suppress>
```

Suppression must state *why*, not just *what*.

---

## Secrets scanning

### TruffleHog rules

- `--only-verified` means the secret was confirmed against the provider (e.g. AWS, GitHub).
- Unverified findings may still be real; review them.
- Never commit API keys, passwords, or private keys, even in tests.

### If a secret is committed

1. Rotate the secret immediately.
2. Remove it from history or accept that it may remain in Git history.
3. Add a pattern to `.gitignore` or scanner config only if it prevents future false positives.

---

## Container scanning

### Trivy severity

Trivy reports UNKNOWN, LOW, MEDIUM, HIGH, CRITICAL. Configure the action to fail on HIGH and CRITICAL
in CI once the image is clean:

```yaml
with:
  image-ref: app:${{ github.sha }}
  severity: HIGH,CRITICAL
  exit-code: 1
```

Start with `exit-code: 0` and upload SARIF to GitHub Security tab while you fix the baseline.

### Common image fixes

- Use the latest JRE base image.
- Remove build tools from the runtime stage.
- Run as non-root.
- Pin image digests for reproducible builds in production.

---

## SBOM

CycloneDX generates `target/bom.json`. Use it to:

- Track dependencies in production.
- Respond to supply-chain incidents.
- Feed into vulnerability scanners that accept CycloneDX.

Regenerate the SBOM on every release and attach it to the release artifacts.

---

## CI job ordering

Run security scans on every PR and on a weekly schedule. The weekly scan catches newly disclosed
CVEs in pinned dependencies.

Order:
1. `dependency-check` — fast feedback on known vulnerabilities.
2. `secrets-scan` — cheap, runs in parallel.
3. `build-image` — produces the image for Trivy.
4. `container-scan` — scans the produced image.
5. `sbom` — generates artifacts for the release.
