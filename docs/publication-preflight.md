<!--
SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
SPDX-License-Identifier: MIT OR Apache-2.0
-->

# Signed publication preflight and independent uploads

## Invariants

Build and sign every intended output locally. Complete the native, classifier and Android
checks applicable to those outputs. Before any publication-package upload, collect the exact
repository contract and run one complete local gate. Missing files, invalid signatures,
version mismatches or differing Central/GitHub bytes must fail before either publisher runs.

After the gate, neither publisher compiles, packages nor signs artifacts again. Both use the
same frozen package from GitHub Actions. The Central, GitHub Release and provenance attestation jobs depend only on
that secured handoff, never on each other's success. A Central outage must not hide the GitHub files.
Internal compiler caches, test reports and native-build intermediates are not publication packages.

## Preparation

Maven's Central publishing plugin 0.12.0 supports `-DskipUpload=true deploy`: it stages signed
release files under `target/central-staging` or snapshots under `target/central-deferred`,
without a remote upload. This is different from `skipPublishing`, which omits artifacts.
Gradle publications are similarly written to their local staging repository first.

Preserve the signed Maven files before any later fat-jar rebuild can overwrite `target/`:

```bash
python3 .github/collect-release-assets.py central-assets --maven-only
# Build and sign additional GitHub-only variants.
```

Finish both publication channels with the shared, checksummed composite action:

```yaml
- name: Finalize and secure the signed publication
  id: finalize
  uses: ./.github/actions/finalize-publication
  with:
    channel: release # snapshot for the rolling snapshot
    maven-assets: central-assets # omit when no outputs need preserving
```

The action collects the exact contract, calls `publication.py prepare`, and uploads the frozen
`publication-package/` as `signed-release-assets` or `signed-snapshot-assets`.
The preparation job exports `assets_complete: ${{ steps.finalize.outputs.complete }}`.

The gate checks exact file membership, nonempty artifacts/signatures, cryptographic GPG
verification, and byte equality between Central staging and the corresponding GitHub files.
It freezes `central/` in Maven layout, `github/` as flat release files, and a `manifest.json`
with commit, version, channel, file sizes and SHA-256. The composite's `complete=true` output
is emitted only after both the local gate and the Actions handoff upload succeed. A failed
collection, gate or handoff upload cannot release a consumer. No incomplete publication package
is attached to a release.

## Repository configuration

`.github/release-assets.json` remains the source of truth. File rules can set `central: true`
for additional Central publications/classifiers. The default is false, so runnable fat jars
and source archives do not accidentally become Central artifacts. Maven reactor outputs,
configured standalone Maven projects and enabled reactor BOMs are always Central outputs.

Optional `publication.central_roots` lists repository-relative staging directories. The default
is `["target/{staging}"]`; `{staging}` becomes `central-staging` or `central-deferred` according
to the project version. This supports separate Maven or Gradle staging without repository names
in the shared implementation. Unexpected or missing Central members fail the gate.

## Independent consumers

```bash
python3 .github/publication.py verify publication-package --export-github release-assets
python3 .github/publication.py upload publication-package
```

Each consumer verifies the complete handoff manifest after download. This is a transport
integrity check, not a second build/signing pass. GitHub publishes only the exported `github/`
files; transport manifests and Central repository directories are not release attachments.

All four repositories use identical `central-snapshot`, `central-release`, `github-snapshot`,
`github-release`, `attest-snapshot` and `attest-release` jobs, tracked as shared workflow fragments.
Each needs only the matching preparation job and requires its successful handoff output.

The attestation job verifies the downloaded manifest first, then attests every frozen file in
`publication-package/github/*`, including detached signatures and configured BOMs. It alone among
the finalization/attestation jobs requests `id-token: write` and `attestations: write`; it needs no
signing key. Attestation failure remains visible but cannot prevent either publisher from running.
Existing producer-specific attestations for native build outputs remain applicable independently.

For releases, the Central consumer forms a Portal bundle from the frozen Central files with
checksums and requests automatic publication. It waits only for HTTP upload acceptance and
records the deployment ID. There is no validation/publication polling. The Maven profiles also
use `autoPublish=true` and `waitUntil=uploaded` for manual deployments.

For snapshots, a transfer-only Maven POM invokes pinned `maven-deploy-plugin:3.1.4:deploy-file`
executions during `validate`. Maven maintains snapshot timestamps and metadata; signatures are
transferred in the same component request with their proper extensions. There are no source
build phases. For a POM-only component its signature is the primary transfer artifact and the
signed POM is supplied through `pomFile`, avoiding an empty-classifier parameter edge case.

Local gate failures stop both publication paths. Authentication or transfer failures remain
visible in the affected upload job, but never gate the other publisher. Slow Portal processing
does not fail the build. Upload acceptance is not a claim of successful Central validation;
inspect the recorded deployment in Portal if later processing fails. Re-running a transfer
uses the same frozen package. A network-interrupted GitHub release update is not atomic.
