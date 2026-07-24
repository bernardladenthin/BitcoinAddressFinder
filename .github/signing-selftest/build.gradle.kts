// SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: MIT OR Apache-2.0

// Throwaway signing project used ONLY by the `verify-signing-key-gradle`
// preflight job in .github/workflows/publish.yml. It signs a tiny throwaway Zip
// through Gradle's `signing` plugin + `useInMemoryPgpKeys` (BouncyCastle) — the
// exact path any Gradle-based publish (e.g. an Android AAR) uses to sign release
// artifacts, and a stricter parser of the armored key than the `gpg` CLI. The
// job asserts only that the detached .asc was produced; no secret is ever
// printed (the key/passphrase arrive via env). Kept identical across the sibling
// repos so a future Gradle publish is pre-validated ("prepared for Gradle").
plugins {
    base
    signing
}

val signingKey = System.getenv("MAVEN_GPG_PRIVATE_KEY")
val signingPassphrase = System.getenv("MAVEN_GPG_PASSPHRASE")
// Optional signing (sub)key id (e.g. 07D2D767). When set, Gradle selects that
// key instead of the primary — required when the key's signing capability lives
// on a subkey (gpg auto-selects it, but the 2-arg useInMemoryPgpKeys picks the
// primary, whose secret BouncyCastle may not be able to unlock -> null
// PGPPrivateKey). Driven by the GPG_KEY_ID env secret.
val signingKeyId = System.getenv("MAVEN_GPG_KEY_ID")
require(!signingKey.isNullOrBlank()) { "MAVEN_GPG_PRIVATE_KEY is empty" }

val makeArtifact = tasks.register<Zip>("makeArtifact") {
    archiveBaseName.set("signing-selftest")
    destinationDirectory.set(layout.buildDirectory)
    from(layout.projectDirectory.file("settings.gradle.kts"))
}

signing {
    if (!signingKeyId.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKeyId, signingKey, signingPassphrase ?: "")
    } else {
        useInMemoryPgpKeys(signingKey, signingPassphrase ?: "")
    }
    sign(makeArtifact.get())
}
