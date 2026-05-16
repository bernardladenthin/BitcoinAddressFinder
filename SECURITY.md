# Security Policy

## Supported Versions

Only the latest release of the **1.x** series receives security fixes.

| Version | Supported |
|---------|-----------|
| 1.x (latest) | :white_check_mark: |
| < 1.x | :x: |

---

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub Issues.**

### Primary channel — GitHub Private Vulnerability Reporting

Use GitHub's built-in private vulnerability reporting to submit a report
confidentially to the maintainers:

**<https://github.com/bernardladenthin/BitcoinAddressFinder/security/advisories/new>**

GitHub will keep the report private and notify the maintainers directly.

### Secondary channel — maintainer email

If GitHub Private Vulnerability Reporting is unavailable, you may contact the
maintainer by e-mail. The address listed in the project's `pom.xml` is
`bernard.ladenthin@gmail.com`. Note that this address is taken from the source
code and has not been independently confirmed as a monitored security mailbox.

Please encrypt sensitive details with the maintainer's public PGP key if one
is published on their GitHub profile.

---

## Response SLA

We aim to acknowledge vulnerability reports within 14 days of receipt and to
provide a remediation timeline within 30 days.

---

## Disclosure Policy

BitcoinAddressFinder follows **coordinated disclosure**:

1. The reporter submits a vulnerability report privately (see above).
2. The maintainer acknowledges receipt within the SLA above.
3. The maintainer investigates, develops a fix, and agrees on a disclosure
   date with the reporter.
4. A new release containing the fix is published.
5. A GitHub Security Advisory is made public simultaneously with (or shortly
   after) the release.

We ask that reporters respect the embargo period and not disclose the
vulnerability publicly until a fix has been released.
