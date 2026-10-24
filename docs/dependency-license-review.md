<!--
SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>

SPDX-License-Identifier: MIT OR Apache-2.0
-->

# Dependency license review and distribution

## Scope and review rules

The Maven repositories share FOSSA policy 85127 (Standard Bundle Distribution). A flagged
license is a review request. Repository configuration does not grant a license or approve a
FOSSA finding. Decisions below are version-specific evidence, not organization-wide exceptions.

1. Analyze the actual commit and branch. Never label a PR scan as main. FOSSA's default/tracked
   branch is a server setting; `.fossa.yml` does not repair that setting. After changing it,
   analyze current main and verify the recorded commit. Streambuffer's previous May 7 master
   revision is not evidence for its current dependencies.
2. Keep test dependencies visible. Do not hide GPL/LGPL findings by excluding tests globally.
   Internal execution and redistribution of benchmark/test JARs are separate use cases.
3. Inspect original source headers as well as inherited POM licenses. A permissive POM does
   not erase another license inside the same component. Record the exact version and file.
4. Preserve dependency license/notice files under artifact-specific paths in fat JARs, since
   unpacking all dependencies into one directory can overwrite identically named legal files.
5. Include attribution and license texts missing from upstream binaries before distribution.
   Full dependency/source provenance must remain available; bundled notices are not a statement
   that every file of an upstream package has been legally cleared.
6. Enter a documented component/version decision in FOSSA only after its usage and notices
   have been checked. Broad approval of GPL, AGPL, LGPL or proprietary findings is not appropriate.

## Verified evidence (2026-10-24)

| Component | Evidence and decision boundary |
|---|---|
| JMH 1.37, JCStress 0.16 | Parent POMs and binary LICENSE explicitly include GPL v2 with Classpath exception. Review these exact versions for internal test/benchmark use; do not treat two exception aliases as independent obligations or approve arbitrary GPL-only code. |
| JUnit Jupiter 6.1.3, jqwik 1.9.3 | EPL-2.0; test scope. Preserve license notices if distributing these tools. |
| Lincheck 3.7 | POM declares MPL-2.0, but source files `org/jetbrains/kotlinx/lincheck/strategy/managed/MemoryLocation.kt` and `MemoryTracker.kt` explicitly say LGPL-3.0-or-later. The LGPL finding is real; never override the entire component to MPL-only. Internal test use is the reviewed boundary; redistribution requires further review. |
| Logback Classic 1.6.5 | Source headers of `AsyncAppender.java` and `BasicConfigurator.java` explicitly allow EPL-2.0 OR LGPL-2.1. Use the EPL-2.0 alternative for these unchanged components where that grant applies. The extra EPL-1.0 finding still requires its exact matched file; do not silently overwrite it. |
| Jetty EE10 12.1.14 | Parent declares EPL-2.0 and Apache-2.0. Verify the applicable grant and notices of the actual files before recording an alternative-license selection. Used by the separate jllama agent, not the core. |
| JeroMQ 0.6.0 | POM declares MPL-2.0. Runtime in BAF; preserve notices and provide the exact upstream source location for unchanged covered code. |
| jsr305 3.0.2 | POM declares Apache-2.0 but `javax/annotation/concurrent/{GuardedBy,Immutable,NotThreadSafe,ThreadSafe}.java` contain CC-BY-2.5. The attribution finding is valid. |
| jcip-annotations 1.0 | Original sources explicitly contain CC-BY-2.5 and Brian Goetz/Tim Peierls attribution. Source republication must retain the notice. |
| Bouncy Castle provider 1.86 | Source `org/bouncycastle/LICENSE.java` contains the permissive Bouncy Castle copyright/permission/warranty text. Preserve it; a custom FOSSA license name alone does not imply a usage prohibition. |

Source archives are obtainable at `https://repo.maven.apache.org/maven2/` using the component's
Maven coordinates and exact version (`-sources.jar`). Keep the POM and source archive together
as evidence. A new version requires checking again.

## Outstanding service decisions

FOSSA issue approvals/license corrections are SaaS settings, not Git settings. These branches
record evidence and distribution changes; they do not claim that server-side approvals have
been applied. Inspect exact file matches for the historical Git/proprietary findings and the
remaining Logback EPL-1.0 finding. Historical revisions are not changed by deleting current files.

Keep repository-specific assessments in that repository's `THIRD_PARTY.md`, including
affected files, evidence, review status and the current decision. A LicenseRef identifier
alone does not establish permission or scanner approval.

## Releasing

Review `THIRD_PARTY.md` per repository, the resolved runtime graph, and the produced archive.
For fat JARs inspect `META-INF/third-party/dependencies/inventory.json` and compare
copied legal entries with the original JAR bytes. The collector reads the resolved runtime
classpath, preserves classifiers and distinguishes equal filenames by content hash.
It never treats a `LICENSE.class` bytecode file as a text license. BAF's assembly profile and
the srcmorph CLI run it before packaging; Python 3.8+ is needed on that build host.
JARs with no legal entries remain explicitly listed. No network requests or approvals are made.
Missing legal material must be supplied explicitly. This check does not replace
source-availability obligations or the review of native third-party components.
