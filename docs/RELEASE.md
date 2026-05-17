# Release Process

The following prompt template drives the release of BitcoinAddressFinder to Maven Central. Paste it into a new Claude Code session, fill in the three placeholders, and send.

```
Release `{PROJECT}` to Maven Central.

**Step 1 — Prepare the release (do immediately):**
1. Read the current version from `pom.xml` on `main` — it will be `{VERSION}-SNAPSHOT`
2. Strip `-SNAPSHOT` from `pom.xml` (→ `{VERSION}`)
3. In `README.md`, update **both**:
   - The release dependency example to `{VERSION}`
   - The snapshot dependency example to `{VERSION}-SNAPSHOT` (it should already match, but verify)
4. Commit both files directly to `main` (no pull request)

**Step 2 — Wait for manual confirmation:**
I will create the `v{VERSION}` tag and GitHub release manually — wait for me to confirm
the release is published on Maven Central before proceeding.

**Step 3 — Post-release snapshot bump (after my confirmation):**
Bump **both** files on `main`:
- `pom.xml` → `{NEXT_VERSION}-SNAPSHOT`
- `README.md` snapshot dependency example → `{NEXT_VERSION}-SNAPSHOT`

Commit both changes together directly to `main`.

**Placeholders:**

| Placeholder      | Value                                        |
|------------------|----------------------------------------------|
| `{PROJECT}`      | *(project name)*                             |
| `{VERSION}`      | *(release version, e.g. `1.3.0`)*           |
| `{NEXT_VERSION}` | *(next snapshot base, e.g. `1.3.1`)*        |
```

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
