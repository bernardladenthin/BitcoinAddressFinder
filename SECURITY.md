# Security Policy

## Supported versions

Only the latest release of the **1.x** series receives security fixes.

| Version       | Supported          |
|---------------|--------------------|
| 1.x (latest)  | :white_check_mark: |
| < 1.x         | :x:                |

Users running a `-SNAPSHOT` build are expected to upgrade to the next stable release when it ships. Security fixes are applied to the **next release**; they are not back-ported to SNAPSHOT tags.

## Reporting a vulnerability — primary channel

**Please do not report security vulnerabilities through public GitHub Issues.**

Use GitHub's built-in Private Vulnerability Reporting to submit a report confidentially to the maintainer:

**<https://github.com/bernardladenthin/BitcoinAddressFinder/security/advisories/new>**

GitHub will keep the report private and notify the maintainer directly.

## Reporting a vulnerability — fallback

If GitHub Private Vulnerability Reporting is unavailable, contact the maintainer by e-mail:

**Bernard Ladenthin — <bernard.ladenthin@gmail.com>**

Please include "BitcoinAddressFinder security" in the subject line so the report is triaged promptly.

## Response SLA

The maintainer aims to:

- **Acknowledge** vulnerability reports within **14 days** of receipt.
- Provide a **remediation timeline** within **30 days** of acknowledgement.

## Coordinated disclosure

BitcoinAddressFinder follows **coordinated disclosure** with a **90-day embargo** by default:

1. The reporter submits a vulnerability report privately (see channels above).
2. The maintainer acknowledges receipt within the SLA above.
3. The maintainer investigates, develops a fix, and agrees on a disclosure date with the reporter — defaulting to **90 days** from the acknowledgement, sooner by mutual agreement if a fix is ready.
4. A new release containing the fix is published.
5. A GitHub Security Advisory is made public simultaneously with (or shortly after) the release.

We ask that reporters respect the embargo period and not disclose the vulnerability publicly until a fix has been released.

## Scope

**In scope:**

- The BitcoinAddressFinder source tree under `src/` (CLI, producers, consumers, persistence, OpenCL kernels).
- Example configurations and helper scripts under `examples/` and `helper/`.
- Release artefacts published to Maven Central under the `net.ladenthin:bitcoinaddressfinder` coordinates.

**Out of scope:**

- Vulnerabilities in upstream dependencies (e.g., `bitcoinj-core`, `lmdbjava`, `jocl`, `jackson-*`, `guava`). These are tracked automatically through GitHub Dependabot and CodeQL alerts; please report them upstream.
- Issues that require an attacker to already control the host running the tool (the tool is a local key-search utility; it deliberately processes private-key material).
- Findings against forks or third-party redistributions.

## Security update notifications

To be notified of security advisories for this project:

- **Watch** the repository on GitHub and enable notifications for "Security alerts" and "Releases":
  <https://github.com/bernardladenthin/BitcoinAddressFinder>
- Subscribe to the GitHub Security Advisories feed:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/security/advisories>
- Maven Central artefact coordinates: `net.ladenthin:bitcoinaddressfinder`. Watch your dependency-scanning tool (Dependabot, Renovate, Snyk, etc.) for advisories tagged against these coordinates.
