<!-- SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com> -->
<!-- SPDX-License-Identifier: Apache-2.0 -->

# GitHub Open-Issue Status Review

Snapshot date: **2026-06-09** — covers all **17 open issues** of
[bernardladenthin/BitcoinAddressFinder/issues](https://github.com/bernardladenthin/BitcoinAddressFinder/issues).

This file is a triage worktable. The goal is to drive the open-**issue** count
to zero: GitHub Issues are kept for actual bug reports / questions only.
Anything that is a genuine future work item (the owner's own `enhancement`
issues) should be migrated into the repository's own `TODO.md` **(later — not
in this pass)** and the GitHub issue then closed with a pointer.

## Legend — disposition

| Disposition | Meaning |
|---|---|
| ✅ **Solved** | Already fixed/implemented in current code; close with explanation. |
| 💬 **Answer & close** | Support question; the feature/answer exists today. Reply, then close. |
| ❓ **Needs info** | Cannot act without reporter feedback (config, full crash log). Ask, then close as stale if no reply. |
| 🌱 **Enhancement → TODO** | Valid future work (mostly owner's own). Move to `TODO.md` later, then close. |
| 🗑 **Spam / invalid** | Not an issue; close. |

## Status table

| # | Title | Label(s) | Disposition | Actual status / what to reply |
|---|---|---|---|---|
| 63 | Change key generation method (BIP39) | question | 💬 Answer & close | **User config is wrong, not a bug.** BIP39 is *not* a `randomAlgorithm` value. It is a separate key producer: add a `keyProducerJavaBip39` entry under `finder` with `keyProducerId` + `mnemonic` (+ optional `passphrase`, `bip32Path`, `hardened`, `creationTimeSeconds`) and reference that `keyProducerId` from a producer. The "cmd window closed" is the JVM exiting on the invalid-config exception. **Action:** reply with a correct snippet; consider adding an `examples/config_Find_*Bip39*.json` (none exists today). |
| 57 | OpenCL / Nvidia Segmentation Fault at startup | bug, help wanted | ❓ Needs info (likely external) | Crash is inside `libnvidia-nvvm.so` / `NvCliCompileBitcode` during `clBuildProgram` — i.e. in the **NVIDIA driver's OpenCL→PTX compiler**, not in project code. Stacktrace shows `libJOCL_2_0_5`; repo is now on **JOCL 2.0.6**. CPU path works for the reporter. **Action:** ask reporter to retry on current release, report exact driver/CUDA version; document the known NVIDIA-OpenCL JIT-compile crash as environment-specific. Not directly fixable in Java. |
| 50 | JVM Crash in LMDB Native Code via lmdbjava | bug | ❓ Needs info (CI flake) | Owner's own tracking issue. Sporadic `SIGSEGV` in `mdb_txn_renew0` in **forked Surefire JVMs**, suspected JaCoCo interaction. Related to the JPMS/`--add-opens` lmdbjava handling now documented in `CLAUDE.md`. **Action:** verify whether it still reproduces on current CI; if not seen for N runs, close as not-reproducible. Mitigation idea: JaCoCo offline instrumentation / disable on the LMDB fork. |
| 49 | create log for hits | help wanted | ✅ Solved | Hits **are** logged. `ConsumerJava` logs each hit at INFO with prefix `hit: Found the address: ` plus full key details (incl. WIF) via `keyUtility.createKeyDetails(...)`; the periodic `Statistics` line shows `[Hits: N]`. **Action:** reply explaining the `hit:` log line and that `examples/logbackConfiguration.xml` can route it to a file appender; then close. |
| 41 | Create address DB + private-key range | question | 💬 Answer & close | Both exist. **DB:** run the `AddressFilesToLMDB` command (`examples/config_AddressFilesToLMDB.json`). **Range:** use `keyProducerJavaIncremental` with `startPrivateKey`/`endPrivateKey` (hex), or limit entropy with `privateKeyMaxNumBits` (puzzle-tx style). **Action:** reply with both config pointers; close. |
| 39 | EXCEPTION_ACCESS_VIOLATION (Win11) | help wanted, question | ❓ Needs info | No config, no `hs_err` log, crash after ~1h. Almost certainly the same native OpenCL/driver class as #57 or a GPU memory issue. **Action:** ask for config JSON + the `hs_err_pidXXXX.log`; otherwise close as stale (2024, single comment). |
| 36 | Nothing happens on startup | help wanted, question | ❓ Needs info (likely user setup) | Reporter ran the `.bat`, a `.txt` log was created, then nothing. Typical cause: missing/empty LMDB database, or default command `OpenCLInfo` chosen, or no GPU. **Action:** ask for the generated `.txt` log contents and the config used; otherwise close as stale (2024). |
| 29 | Output settings (logbackConfiguration.xml) | question | 💬 Answer & close | User wants only WIF + Base58 address on a match. The hit line format is produced in code (`createKeyDetails`), so logback can change *routing/format of the line* but the **content fields are fixed**. **Action:** explain logback pattern customization + that hit messages already contain WIF and address; close (2023, stale). |
| 25 | `--illegal-access=permit` error | help wanted, question | ✅ Solved | The `--illegal-access=permit` JVM flag was removed in Java 17; the project is now **Java 21** and the shipped `examples/run_*.bat` files no longer contain that flag (verified: zero matches). Reporter already self-resolved by upgrading the JDK. **Action:** confirm resolved, close. |
| 24 | not generating key | help wanted, question | ❓ Needs info (stale) | Statistics shows 0 keys checked / empty consumer — producer not feeding the queue (likely OpenCL not producing or misconfigured `keyProducerId` linkage). 14-comment thread, last 2024. **Action:** reply that producer↔`keyProducerId` wiring must match; close as stale unless reporter returns. |
| 23 | "work is necessary to change life." | *(none)* | 🗑 Spam / invalid | Collaboration/"make money" solicitation, not an issue. **Action:** close. |
| 22 | Can jogamp be used to improve OpenCL handling? | enhancement | ✅ **Done — migrated & closed** | Refined into a backend-abstraction task: step 1 define a small OpenCL device/lib API over the existing JOCL impl, step 2 wire JogAmp (`com.jogamp.opencl`) behind it as a switchable backend. **Migrated to `TODO.md`** ("OpenCL backend abstraction & multi-device coverage") and the GitHub issue **closed as completed** (2026-06-09). |
| 18 | Statistics: add CPU/GPU batches + running counts | enhancement | ✅ **Done — implemented & closed** | **Implemented** on branch `claude/wonderful-cray-y7e9ua`: statistics line now shows a per-producer batch breakdown (`<keyProducerId> (<Strategy>, <CPU\|GPU>)`) plus `Producers running` / `Consumers running`, via `statistics/RuntimeStatistics` incremented at `AbstractProducer.consumeSecrets`; tests + README added. GitHub issue **closed as completed** (lands on `main` when the branch merges). |
| 13 | Linux | question | ✅ Solved | Linux is fully supported (CI builds/tests on Linux; native OpenCL build documented). **Action:** reply "yes, Linux works" with a pointer to the Linux run instructions; close. (Last touched 2025-10.) |
| 10 | Producer should log its info to allow verification | enhancement | ✅ Solved (largely) | Implemented via `logSecretBase` (logs `secretBase`/hex via `AbstractProducer.createSecretBase`), `logReceivedSecret` on receiver producers, and `runtimePublicKeyCalculationCheck` for full verification. **Action:** confirm coverage is sufficient; close (or fold any remainder into `TODO.md`). |
| 6 | Unit test: 2 OpenCL devices simultaneously | enhancement | ✅ **Done — migrated & closed** | Scope fixed: a test running two `producerOpenCL` on **two physical OpenCL devices** (2 GPUs, or 1 GPU + 1 CPU-OpenCL), self-skipping unless ≥2 distinct devices are enumerated. **Migrated to `TODO.md`** ("OpenCL backend abstraction & multi-device coverage") and the GitHub issue **closed as completed** (2026-06-09). |
| 5 | Print used OpenCL device info on usage | enhancement | ✅ Solved | Implemented: `OpenCLContext` logs `"Selected OpenCL device:\n{}"` via `OpenCLDevice.toStringPretty()`, and the `OpenCLInfo` command prints full device info (`CL_DEVICE_NAME`, etc.). **Action:** reply with the log/command pointer; close. |

## Summary counts

| Disposition | Issues | Count |
|---|---|---|
| ✅ **Closed this pass** (#18 implemented; #22, #6 migrated to `TODO.md`) | #22, #18, #6 | 3 |
| ✅ Solved (close w/ explanation) | #49, #25, #13, #10, #5 | 5 |
| 💬 Answer & close (feature exists) | #63, #41, #29 | 3 |
| ❓ Needs info (then close stale) | #57, #50, #39, #36, #24 | 5 |
| 🗑 Spam / invalid | #23 | 1 |

> **Update 2026-06-09:** #22, #18 and #6 are now **closed on GitHub** (open-issue
> count 17 → 14). The remaining 14 still need replies/closures per the table above.

## Recommended next pass (NOT this pass)

1. ✅ **Done:** #22, #18, #6 migrated to `TODO.md` and closed on GitHub. Residual
   of **#10** still open (its enhancement is largely covered; close or fold into
   `TODO.md` when convenient).
2. ✅ **Done:** `examples/config_Find_1CPUProducerBip39.json` added, making **#63**
   self-serve.
3. Post the prepared replies for the ✅ / 💬 issues and close them.
4. Post info requests for the ❓ issues; close as stale after a grace period.
5. Close **#23** as spam.
