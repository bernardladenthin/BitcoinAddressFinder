<!--
SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>

SPDX-License-Identifier: MIT OR Apache-2.0
-->

# Third-party license and attribution notes

The project is Apache-2.0. Runtime dependencies include Logback Classic 1.6.5,
JeroMQ 0.6.0, Bouncy Castle 1.86, jsr305 3.0.2 and jcip-annotations 1.0.
The annotation sources contain CC-BY-2.5 despite the Apache declaration in jsr305's POM.
See the bundled annotation attribution and CC-BY-2.5 text under META-INF.
The obsolete AGPL kernels were removed on this branch; historical releases are unaffected.

## Review evidence

See [the dependency license review](docs/dependency-license-review.md) in the source repository
for exact-version evidence and remaining decisions. This inventory is not an approval of all
licenses found by a scanner. Test-tool redistribution requires a separate review.

For unchanged Maven components, source is available from
`https://repo.maven.apache.org/maven2/<groupId with slashes>/<artifactId>/<version>/`
as `<artifactId>-<version>-sources.jar`; POMs and upstream source repositories provide provenance.
Preserve copyright, license and NOTICE files when redistributing dependencies.

Runnable BAF/srcmorph CLI packaging uses Python 3.8+ to collect original dependency
legal files before the project JAR and fat JAR are assembled. Override `-Dlicense.python=python`
if that is the Python command on the build host. Legal files are under
`META-INF/third-party/dependencies/<original-jar-name>-<content-hash>/`.
`inventory.json` maps exact JAR hashes/classifiers to preserved files and also lists JARs
without embedded legal material, which require review or supplemental notices.
This source-location statement does not clear unknown proprietary or historical Git findings.
