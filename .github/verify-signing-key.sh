#!/usr/bin/env bash

# SPDX-FileCopyrightText: 2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
#
# SPDX-License-Identifier: MIT OR Apache-2.0

# Cross-repo shared script -- kept BYTE-IDENTICAL in java-llama.cpp, BitcoinAddressFinder,
# srcmorph and streambuffer (listed in each repo's .github/shared-files.sha256). The body of the
# `verify-signing-key` job: reproduces what maven-gpg-plugin does at deploy time, so a bad or
# expired key or a wrong passphrase is caught in seconds instead of failing the publish stage.
#
# Input: GPG_PRIVATE_KEY (armored secret key) and GPG_PASSPHRASE in the environment.
#
# SECURITY: prints no secret material. The key is imported into an ephemeral keyring via stdin;
# only PUBLIC key metadata is printed (key id, fingerprint, owner UID, algorithm, created/expiry,
# all of which live on public keyservers); the passphrase is validated by producing and verifying
# a throwaway signature, reaches gpg on fd 3 only (never argv, never a log line), and is
# additionally `::add-mask::`ed. `set -x` must never be enabled here.
set -euo pipefail   # NOTE: deliberately NO `set -x` — it would echo the passphrase.

if [ -z "${GPG_PRIVATE_KEY:-}" ]; then
  echo "::error::GPG_PRIVATE_KEY is empty for this run. Either the secret is not set, or it is scoped to a different environment/branch than 'maven-central' on this ref. Nothing to verify."
  exit 1
fi
# Defensive: even though we never print it, register the passphrase as a
# masked value so any accidental echo downstream is redacted.
if [ -n "${GPG_PASSPHRASE:-}" ]; then echo "::add-mask::${GPG_PASSPHRASE}"; fi

echo "gpg: $(gpg --version | head -n1)"

# Ephemeral, private keyring; removed on exit.
export GNUPGHOME="$(mktemp -d)"
chmod 700 "$GNUPGHOME"
cleanup() { gpgconf --kill gpg-agent >/dev/null 2>&1 || true; rm -rf "$GNUPGHOME"; }
trap cleanup EXIT

echo "== Import private key into an ephemeral keyring (key via stdin, never argv) =="
printf '%s\n' "$GPG_PRIVATE_KEY" | gpg --batch --import

COLONS="$(gpg --list-secret-keys --with-colons --fixed-list-mode)"
SECCOUNT="$(printf '%s\n' "$COLONS" | awk -F: '$1=="sec"{n++} END{print n+0}')"
echo "Secret keys imported: $SECCOUNT"
if [ "$SECCOUNT" -lt 1 ]; then
  echo "::error::No secret key was imported — GPG_PRIVATE_KEY is not a valid armored secret key (check that the secret contains the full -----BEGIN PGP PRIVATE KEY BLOCK----- with intact newlines)."
  exit 1
fi

KEYID="$(printf '%s\n' "$COLONS"  | awk -F: '$1=="sec"{print $5; exit}')"
ALGO="$(printf '%s\n'  "$COLONS"  | awk -F: '$1=="sec"{print $4; exit}')"
CREATED="$(printf '%s\n' "$COLONS"| awk -F: '$1=="sec"{print $6; exit}')"
EXPIRES="$(printf '%s\n' "$COLONS"| awk -F: '$1=="sec"{print $7; exit}')"
FPR="$(printf '%s\n'   "$COLONS"  | awk -F: '$1=="fpr"{print $10; exit}')"

echo "== PUBLIC key metadata =="
echo "  Key ID (long):  $KEYID"
echo "  Fingerprint:    $FPR"
echo "  Pubkey algo id: $ALGO"
echo "  Created (UTC):  $(date -u -d "@$CREATED" 2>/dev/null || echo "$CREATED")"
echo "  Owner UID(s):"
printf '%s\n' "$COLONS" | awk -F: '$1=="uid"{print "    - " $10}'

# --- Expiration gate ---
NOW="$(date -u +%s)"
if [ -n "$EXPIRES" ]; then
  echo "  Expires (UTC):  $(date -u -d "@$EXPIRES" 2>/dev/null || echo "$EXPIRES")"
  if [ "$EXPIRES" -le "$NOW" ]; then
    echo "::error::Signing key is EXPIRED — Maven Central will reject its signatures. Extend the key's expiry and update the GPG_PRIVATE_KEY secret."
    exit 1
  fi
  echo "  Days to expiry: $(( (EXPIRES - NOW) / 86400 ))"
  if [ "$(( (EXPIRES - NOW) / 86400 ))" -lt 30 ]; then
    echo "::warning::Signing key expires in under 30 days — plan to rotate it."
  fi
else
  echo "  Expires (UTC):  never"
fi

# --- Signing-capability gate ---
if printf '%s\n' "$COLONS" | awk -F: '($1=="sec"||$1=="ssb"){print $12}' | grep -q 's'; then
  echo "  Signing capability: present"
else
  echo "::error::No signing-capable (sub)key found — this key cannot produce release signatures."
  exit 1
fi

# --- Passphrase unlock + sign + verify roundtrip (the exact failure mode) ---
# Passphrase on fd 3 only. Payload is a throwaway nonce; only the signature's
# validity (exit codes) matters — no secret is ever emitted.
echo "== Passphrase unlock + detached-sign + verify self-test =="
WORK="$(mktemp -d)"
printf '%s' "ai-index signing-selftest" > "$WORK/payload.txt"
gpg --batch --yes --pinentry-mode loopback --passphrase-fd 3 \
    --local-user "$KEYID" \
    --detach-sign --armor --output "$WORK/payload.txt.asc" "$WORK/payload.txt" \
    3<<<"${GPG_PASSPHRASE:-}"
echo "  Signature produced: $(wc -c < "$WORK/payload.txt.asc") armored bytes"
gpg --batch --verify "$WORK/payload.txt.asc" "$WORK/payload.txt"
rm -rf "$WORK"

echo "RESULT: OK — key imports, is not expired, is signing-capable, and the passphrase successfully unlocked it to produce a VALID signature. maven-gpg-plugin will be able to sign with this key/passphrase."
