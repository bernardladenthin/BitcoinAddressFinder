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
| 63 | Change key generation method (BIP39) | question | ✅ **Done — answered & closed** | Not a bug: BIP39 is a separate key producer (`keyProducerJavaBip39` with `keyProducerId` + `mnemonic`, referenced by a producer), not a value of the random producer; the JVM exited on the invalid config. **Replied with a correct config snippet** and closed as completed (2026-06-09); `examples/config_Find_1CPUProducerBip39.json` ships next release. |
| 57 | OpenCL / Nvidia Segmentation Fault at startup | bug, help wanted | ✅ **Done — closed (not planned)** | Crash is inside `libnvidia-nvvm.so` / `NvCliCompileBitcode` during `clBuildProgram` — the **NVIDIA driver's OpenCL→PTX compiler**, not project code (CPU path works). Not fixable in Java. **Replied** (retry on JOCL 2.0.6 / update driver+CUDA, attach `hs_err`) and **closed as not planned with a reopen-if-still-present note** (2026-06-09). |
| 50 | JVM Crash in LMDB Native Code via lmdbjava | bug | ❓ Needs info (CI flake) | Owner's own tracking issue. Sporadic `SIGSEGV` in `mdb_txn_renew0` in **forked Surefire JVMs**, suspected JaCoCo interaction. Related to the JPMS/`--add-opens` lmdbjava handling now documented in `CLAUDE.md`. **Action:** verify whether it still reproduces on current CI; if not seen for N runs, close as not-reproducible. Mitigation idea: JaCoCo offline instrumentation / disable on the LMDB fork. |
| 49 | create log for hits | help wanted | ✅ **Done — answered & closed** | Hits **are** logged: INFO `hit: Found the address: ` + key details (WIF) via `keyUtility.createKeyDetails(...)`, plus `hit: safe log:` and `[Hits: N]` in the statistics line; `[Hits: 0]` for weeks is expected. **Replied** (incl. routing hits to a file appender) and closed as completed (2026-06-09). |
| 41 | Create address DB + private-key range | question | ✅ **Done — answered & closed** | Both exist. **DB:** `AddressFilesToLMDB` command (`examples/config_AddressFilesToLMDB.json`). **Range:** `keyProducerJavaIncremental` with `startPrivateKey`/`endPrivateKey` (hex), or cap entropy with `privateKeyMaxNumBits`. **Replied with both pointers** and closed as completed (2026-06-09). |
| 39 | EXCEPTION_ACCESS_VIOLATION (Win11) | help wanted, question | ✅ **Done — closed (assume fixed)** | Native crash (GPU/OpenCL driver, same class as #57), not Java. **Replied** (retest on current release + updated drivers; attach config + `hs_err_pid*.log` and reopen if it recurs) and closed as completed (2026-06-09). |
| 36 | Nothing happens on startup | help wanted, question | ✅ **Done — closed (assume fixed)** | Likely still loading (LMDB stats iteration on init looks frozen on a large DB) or too little memory (`-Xmx`), or missing LMDB. **Replied** (retest; share config + `.txt` log and reopen if it persists) and closed as completed (2026-06-09). |
| 29 | Output settings (logbackConfiguration.xml) | question | ✅ **Done — answered & closed** | The hit line is built in code (`createKeyDetails`) and already contains WIF + address; Logback can change the line layout and route hits to their own file, but can't strip the message to *only* WIF+address (that would be a code change). **Replied** accordingly and closed as completed (2026-06-09). |
| 25 | `--illegal-access=permit` error | help wanted, question | ✅ **Done — answered & closed** | `--illegal-access=permit` was removed in Java 17; project is now **Java 21** and the shipped `run_*.bat` no longer contain it (reporter self-resolved by upgrading the JDK). **Replied** (incl. "how to tell it's working") and closed as completed (2026-06-09). |
| 24 | not generating key | help wanted, question | ✅ **Done — closed (assume fixed)** | `Checked 0 M keys` + high empty-consumer count = producer not feeding the queue (producer↔`keyProducerId` mismatch, or OpenCL not producing). **Replied** (retest with corrected wiring; share config and reopen if it persists) and closed as completed (2026-06-09). |
| 23 | "work is necessary to change life." | *(none)* | ✅ **Done — closed (not planned)** | Collaboration/"make money" solicitation, out of scope. **Commented** ("tracker is for bug reports and feature requests; closing as out of scope") and **closed as not planned** (2026-06-09). |
| 22 | Can jogamp be used to improve OpenCL handling? | enhancement | ✅ **Done — migrated & closed** | Refined into a backend-abstraction task: step 1 define a small OpenCL device/lib API over the existing JOCL impl, step 2 wire JogAmp (`com.jogamp.opencl`) behind it as a switchable backend. **Migrated to `TODO.md`** ("OpenCL backend abstraction & multi-device coverage") and the GitHub issue **closed as completed** (2026-06-09). |
| 18 | Statistics: add CPU/GPU batches + running counts | enhancement | ✅ **Done — implemented & closed** | **Implemented** on branch `claude/wonderful-cray-y7e9ua`: statistics line now shows a per-producer batch breakdown (`<keyProducerId> (<Strategy>, <CPU\|GPU>)`) plus `Producers running` / `Consumers running`, via `statistics/RuntimeStatistics` incremented at `AbstractProducer.consumeSecrets`; tests + README added. GitHub issue **closed as completed** (lands on `main` when the branch merges). |
| 13 | Linux | question | ✅ **Done — answered & closed** | Linux is fully supported (CI builds/tests on Linux; native OpenCL build documented). **Replied** ("yes, run the same `java -jar … config.json` as the `.bat` launchers") and closed as completed (2026-06-09). |
| 10 | Producer should log its info to allow verification | enhancement | ✅ **Done — closed** | Implemented via `logSecretBase`, `logReceivedSecret` (socket/websocket/zmq), `runtimePublicKeyCalculationCheck`, plus the new #18 per-producer statistics line. **Replied** and closed as completed (2026-06-09). |
| 6 | Unit test: 2 OpenCL devices simultaneously | enhancement | ✅ **Done — migrated & closed** | Scope fixed: a test running two `producerOpenCL` on **two physical OpenCL devices** (2 GPUs, or 1 GPU + 1 CPU-OpenCL), self-skipping unless ≥2 distinct devices are enumerated. **Migrated to `TODO.md`** ("OpenCL backend abstraction & multi-device coverage") and the GitHub issue **closed as completed** (2026-06-09). |
| 5 | Print used OpenCL device info on usage | enhancement | ✅ **Done — closed** | Implemented: `OpenCLContext` logs `"Selected OpenCL device:\n{}"` via `OpenCLDevice.toStringPretty()`, and the `OpenCLInfo` command prints full device info (`CL_DEVICE_NAME`, etc.). **Replied** and closed as completed (2026-06-09). |

## Summary counts

| Disposition | Issues | Count |
|---|---|---|
| ✅ **Closed on GitHub this pass** | #22, #18, #6, #63, #41, #29, #23, #57, #49, #25, #13, #10, #5, #39, #36, #24 | 16 |
| ❓ Needs info (then close stale) | #50 | 1 |

> **Update 2026-06-09:** **16 of the 17 issues closed.** #22/#6 migrated to
> `TODO.md`; #18 implemented; #63/#41/#29/#49/#25/#13/#10/#5 answered/implemented;
> #23 closed as spam; #57 closed as an external NVIDIA-driver crash; #39/#36/#24
> closed assume-fixed with retest/reopen guidance. Open-issue count **17 → 1**.
> The only remaining issue is **#50** (owner's own LMDB/CI-flake tracking issue),
> left open as a CI judgement call (verify whether it still reproduces).

## Recommended next pass (NOT this pass)

1. ✅ **Done:** #22, #18, #6 migrated to `TODO.md` and closed on GitHub. Residual
   of **#10** still open (its enhancement is largely covered; close or fold into
   `TODO.md` when convenient).
2. ✅ **Done:** `examples/config_Find_1CPUProducerBip39.json` added, making **#63**
   self-serve.
3. Post the prepared replies for the ✅ / 💬 issues and close them.
4. Post info requests for the ❓ issues; close as stale after a grace period.
5. Close **#23** as spam.
