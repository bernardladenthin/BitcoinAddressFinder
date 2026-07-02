# Release Process

The maintainer-facing release procedure is **centralized in the workspace repo**:
[`../workspace/workflows/release-process.md`](../workspace/workflows/release-process.md).

BitcoinAddressFinder is a single-module Maven project, so the canonical version/pom and README
steps apply as-is. The one repo-specific addition is the tag-prefix policy below.

## Tag prefix policy

All new release tags use the canonical form `vX.Y.Z[-SUFFIX]`
(e.g. `v1.6.0`, `v1.6.0-SNAPSHOT`).

Four legacy tags were created without the `v` prefix and remain in
the repository for backwards compatibility. Each one is also
reachable under its canonical v-prefixed name, which points at the
same commit:

| Legacy tag         | Canonical alias    | Commit |
|---|---|---|
| `1.4.0`            | `v1.4.0`           | (same) |
| `1.3.0-SNAPSHOT`   | `v1.3.0-SNAPSHOT`  | (same) |
| `1.2.0-SNAPSHOT`   | `v1.2.0-SNAPSHOT`  | (same) |
| `1.1.0-SNAPSHOT`   | `v1.1.0-SNAPSHOT`  | (same) |

New tags MUST use the canonical form. Do not create unprefixed tags.
