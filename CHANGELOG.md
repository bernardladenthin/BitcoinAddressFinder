# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Dependencies and build tooling bumped in step with the sibling repositories: Jackson 2.22.3 ->
  2.22.4, LogCaptor 2.12.7 -> 2.13.0 (test; still requests log4j 2.26.1, so the log4j floor guard is
  unchanged), pitest-maven 1.30.0 -> 1.30.1, central-publishing-maven-plugin 0.11.0 -> 0.12.0;
  `anthropics/claude-code-action` v1.0.255 and Gradle 9.8.2 in the shared `verify-signing-key-gradle` job.
- **Behaviour changes from the bug hunt of 2026-10-16 … 20** (details and tests per id in
  `BUGHUNT-FOUND.md`). Visible to a user or a configuration:
  - **Statistics:** "Average contains time" is measured and printed in **ns** (it was `ms` and read 0
    nearly always) (F11).
  - **BIP39:** mnemonic and passphrase are **NFKD-normalised** as the BIP39 specification requires, so a
    non-English mnemonic that was not in NFKD now derives *different* (the correct) keys (F120);
    `privateKeyMaxNumBits` other than 256 is rejected for BIP39 instead of truncating the keys (F51).
  - **Socket key producer:** refuses at start a negative `timeoutMillis` (F100), `port == resultPort`
    in server mode (F155) and a port that is already bound (F157), instead of ending as a regular run
    without a checked key; its final connection failure is logged (F101); `connectRetryCount` counts
    consecutive failures, not failures over the whole run (F22).
  - **Result broadcast:** the grid is announced per key producer, and the **smallest** `batchSizeInBits`
    wins when they differ (F153); a client connecting during the announcement gets it (F119). The socket
    broadcaster has a per-client queue of 4096 messages, drops a client that falls behind and drains for
    up to 2 s on close (F20, F130); the ZeroMQ broadcaster lingers 2 s on close (F163).
  - **Incremental key producer with explicit keys:** the last batch before `endPrivateKey` is shorter
    instead of being skipped (F160).
  - **Address import (`AddressFilesToLMDB`):** an amount column that is not a non-negative whole number
    of satoshi keeps the address with the default amount and is reported once, without
    `useStaticAmount` (F162, F193); the `HexHash` export is importable again (F150); uppercase CashAddr
    and Riecoin scripts are read correctly (F121, F122); a leading space or UTF-8 BOM no longer loses a
    line (F23, F42, F43); a signed Base36 hash is no longer read as two's complement (F58).
  - **`CompactLMDB`** refuses a non-empty target directory and a target that is the source under another
    spelling (F31, F132), and honours an interrupt before it starts (F32).
  - **OpenCL on Linux:** a warning is logged when the kernel is built without the JDK's signal chaining
    (`libjsig`), and the launchers of an OpenCL run (`run_*OpenCL*.sh`, `run_TuneConfiguration.sh`)
    preload it (F148; README "OpenCL on Linux").
  - **Configuration errors fail early:** an unknown `keyProducerId` is reported before the database is
    loaded (F126); a JSON configuration with a UTF-8 BOM loads (F44).
  - **Shutdown:** `Finder.interrupt()` serialises its callers, so a Ctrl-C during the teardown of a
    finished run waits for it (up to `shutdownTimeoutSeconds` per producer) instead of racing it (F34).
  - **Result broadcast after a Ctrl-C:** the broadcasters are closed only after the consumer has
    checked the batches still queued, so a client gets the report for every batch drained during the
    shutdown; a report that arrives after a broadcaster was closed is logged once as a warning instead of
    being dropped without a word.
  - **Ports that cannot be bound fail the start:** the WebSocket key producer's `port` (it started
    "successfully" and, with `timeoutMillis: -1`, waited forever) and the socket key producer's
    `resultPort` (it went on without serving results).
  - **OpenCL on Linux, `libjsig`:** the warning is decided from what is mapped into the process
    (`/proc/self/maps`) instead of the `LD_PRELOAD` string, so a preload entry the dynamic linker ignored
    is warned about and a `libjsig` loaded another way is not; it is logged once per JVM, no longer once
    per kernel build (the tuner logged it once per arm); `run_TuneConfiguration.sh` preloads it too.
  - **pocl's CPU device:** the key kernel runs there with a local work size of at most 256 (logged at
    start-up); left to pocl it overflowed the worker thread's stack and the JVM died with exit code 139
    or 134 at default settings. Other devices keep the driver's choice. The OpenCL launchers also export
    `POCL_MAX_WORK_GROUP_SIZE=256` unless it is set, which the test and benchmark kernels still rely on.
- **The OpenCL kernel refuses an unaligned secret base, and the skipped top grid is a warning.**
  The kernel derives a work item's key by ORing its offset into the lowest word of the base, which is
  the base plus the offset only when the low `batchSizeInBits` bits of the base are zero; the host
  reports a hit as base plus offset. `OpenCLContext.createKeys` now checks that once per grid and
  throws before anything is dispatched, instead of letting the GPU check other keys than the ones it
  reports. The producers always aligned their bases, so a scan behaves as before; only direct callers
  of `createKeys` can notice. The last grid below the group order n, which the GPU skips because it
  contains n, is logged as one warning naming the base instead of an error with a stack trace (it is
  still recorded as the producer's last failure).
- **Build tooling: spotbugs-maven-plugin 4.10.4.1 -> 4.10.5.0**, in step with the sibling repositories, and with it
  **Lombok-generated code carries JSpecify nullness** (`lombok.addNullAnnotations = jspecify` in the
  shared `lombok.config`): under `@NullMarked` a generated `equals(Object)` parameter read as non-null
  while `Object.equals` accepts null, which SpotBugs 4.10.5 -- the first release that reads JSpecify --
  reports as `NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION` per class; Lombok now writes `@Nullable` there
  (and `@NonNull` where a generated member cannot return or take null), so a consumer's nullness checker
  sees the real contract too. The 27 findings here were all of that kind, plus one on the
  importer: `AddressFilesToLMDB.ReaderProgress` is a `static` nested class now that takes the written-address
  count as an argument -- SpotBugs 4.10.5 extends the outer class's multi-threaded verdict (atomic fields,
  `Runnable`) to every inner class carrying an enclosing-instance reference and then reported the
  reader-thread-only `+=` on its byte counter as `AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE`; judged on
  its own, the class is what its Javadoc always said. `humanBytes` / `formatDuration` are static with it.
- Build and test tooling bumped in step with the sibling repositories: Spotless 3.10.4, Checker
  Framework 4.3.1, `commons-codec` 1.23.0; the measurement-tools matrix runs Python 3.10 and 3.15
  (3.15.0 final, from 3.14). The shared `check-versions.py` now also compares the Kotlin Gradle plugins
  of a repository with its POMs' Kotlin (nothing to compare here) and reads java-llama.cpp's
  `examples/server/pom.xml`.
- **The default `endPrivateKey` of the incremental key producer is n − 1**
  (`…D0364140`, `Secp256k1Constants.MAX_VALID_PRIVATE_KEY_HEX`), the last valid key. It was the group
  order n itself, documented as an inclusive end although n is not a key since the bound became
  exclusive; a scan of explicit keys to the default end then checked the replacement key in place of n.
  Grid mode is unaffected: its last grid contains n either way and is skipped on the GPU.
- **No `System.gc()` before the GPU filter upload any more** (`OpenCLContext.reclaimHeapBeforeUpload`,
  `GcTrigger` removed). It was added for `CL_MEM_OBJECT_ALLOCATION_FAILURE` on an RX 7900 XTX while
  the buffer was created with `CL_MEM_COPY_HOST_PTR`; on the chunked LWJGL upload the same card created
  and filled the 3.10 GB Full-DB Fuse-16 buffer at 40.9 GiB live heap without a collection, which cost
  83 ms and changed nothing in the probe throughput (2.253 vs 2.252 ms/op).
- **CI prints the runner's CPU** at the top of `test`, `test-opencl` and `vmlens`
  (`.github/print-host-info.sh`, a shared file of the four repositories): pocl computes on the CPU, so a
  throughput or a timing in those logs can be read against the hardware the job drew.
- **CI: snapshot deploy fails with HTTP 401 on Maven 3.10 (seen in java-llama.cpp).** The runners moved to Maven 3.10, which
  sends a `<server>`'s credentials only to the origins declared for it; for the id `central` that is
  `https://repo.maven.apache.org`, so the upload to `central.sonatype.com/repository/maven-snapshots/`
  goes out without credentials ("Not using credentials of server 'central'"). Every `deploy` step now
  passes `-Dmaven.repository.credentialScope=id`, Maven's own switch back to id-only matching, until a
  setup-java release can write `<repositoryOrigins>` (`mvn-server-repository-origins`, merged upstream
  but not yet released).
- **CI: shared files and the release gate are checked.** The files kept byte-identical with the sibling
  repositories are listed with their SHA-256 in `.github/shared-files.sha256`; a new `shared-files` job
  fails on a copy changed here alone and warns on a sibling's differing copy. The same job runs the
  shared build-check library's tests and `check-release-gate.py`: every job must gate both publish
  jobs unless `.github/release-gate-exemptions.txt` says why (`vmlens` now gates). The crash-log step
  and the signing-key preflight are shared scripts (`print-crash-logs.sh`, `verify-signing-key.sh`)
  instead of copies pasted into the workflow.
- **Workflow jobs kept identical across the repositories are checked too**: a
  `.github/shared-files.sha256` entry `.github/workflows/publish.yml#<job>` hashes one job (`startgate`,
  `shared-files`, `verify-signing-key`, `check-snapshot`, `check-tag`, and where present
  `verify-signing-key-gradle`, `github-snapshot`, `github-release`).
- **Own scripts are licensed `MIT OR Apache-2.0`** like the CI files: `examples/run_*`, `build.bat`,
  `update.bat`, `testAddressTxtLineTest.bat`, `helper/` and `docs/measurements/` (the Java sources and
  the rest of the project stay Apache-2.0).
- **More shared files, and files identical up to the repository name**: a shared-files entry ending
  in `?repo` is hashed with the repository's name replaced by `{repo}`. Added: `.editorconfig`,
  `.gitattributes` (now with `*.gguf binary` everywhere), `FUNDING.yml`, `CODEOWNERS`, the license texts,
  `SUPPORT.md`, `ISSUE_TEMPLATE/config.yml` and further files listed in `.github/shared-files.sha256`;
  the signing self-test now runs on Gradle 9.8.0 in all four repositories.
  `CODEOWNERS` lost a `/* ... */` license header -- not comment syntax there, so GitHub read its lines
  as rules with invalid owners.
- **The JDK is named once, in `.java-version`**: every workflow reads it through setup-java's
  `java-version-file` (the `JAVA_VERSION` env and the literal `21`s are gone); `.java-version` and
  `codeql.yml` are now byte-identical in all four sibling repositories and in the shared-files manifest.
  `codeql.yml` is now the compact form the siblings use (same build, same queries).
- **CI files are licensed `MIT OR Apache-2.0`**: every `.github` file carrying only the owner's
  copyright now has the same license header in all four sibling repositories, so the shared ones are
  byte-identical. `claude.yml`, `claude-code-review.yml`, `scorecard.yml`, `reuse.yml`,
  `osv-scanner.yml`, `dependabot.yml` and `CODE_OF_CONDUCT.md` joined the shared-files manifest;
  `osv-scanner.yml` now grants `contents: read` instead of `read-all` (as java-llama.cpp already did).
- **Workflow run scripts are parsed in the `shared-files` job**: `check-run-scripts.py` runs `bash -n`
  over every `run:` script of the workflows and composite actions that runs in bash (shell decided as
  the runner does), so a broken script fails within minutes instead of in the job that runs it.
- **Maven versions are compared with the sibling repositories**: `check-versions.py` (in the
  `shared-files` job) warns where a dependency or plugin -- incl. annotation-processor paths and the
  Spotless formatter version -- is used in another version than in a sibling's default branch.

### Fixed
- **`examples/websocket_scanner.html` sends the block that holds "To".** The page treated "To" as
  exclusive, which only a script comment said: with an aligned "To" (the default included) the block
  containing the named key was never sent while "Range complete." was logged anyway, unlike the
  inclusive `endPrivateKey` of an incremental range. The page now rounds up from "To" + 1, the default
  "To" is `…0107FFFFFF` so the default range keeps its 2^27 keys, and the label and the notes panel
  state the convention.
- **Fixes from the bug hunt of 2026-10-16 … 20** (details, reproduction and red/green proof per id in
  `BUGHUNT-FOUND.md`; the user-visible behaviour changes are listed under "Changed"). Lost or
  unchecked keys:
  - the GPU skipped keys 2 … `keysPerWorkItem`−1 of the grid at base 0, the default incremental start
    (F129); a finite GPU run lost its last batches, because the run ended while result readers were
    still reading back (F192);
  - batches still queued at shutdown were never checked (F15), and after the shutdown timeout they were
    reported as "checked, nothing found" against the closed database (F66); the result broadcasters
    were closed before the consumer had checked the last batches (F131);
  - a secrets file without `batchUsePrivateKeyIncrement` checked no secret (F08), and out-of-range keys
    in it are now treated as a grid treats them (F194); the group order n counted as a valid key and the
    last OpenCL base is n − 2^`batchSizeInBits` (F45, F151).

  Hangs, crashes and resource leaks: a shared socket/WebSocket/ZeroMQ key producer hung the shutdown
  (F10); concurrent ZeroMQ broadcasts corrupted the socket (F19); a result client that never reads froze
  the scan (F20); the WebSocket endpoint left a non-daemon thread (F21); the BIP39 producer kept every
  derived key (F14); the LMDB map grew only once per write (F27); a failed start, or a failed abort of
  one, left the consumer running (F139, F195); a producer whose release failed left the others running,
  and one still running was released (F52, F156); a client accepted during the socket broadcaster's
  close stayed connected (F196); OpenCL was initialised twice on a concurrent first call (F33);
  `OpenCLBuilder` queried a fixed three work-item sizes (F04).

  Wrong reports: interrupted exports, imports, deltas and secrets-file runs reported "done" (F46–F50);
  the tuner extended, measured and reported the wrong arm in several cases (F135, F136, F140, F152,
  F154, F164, F170).
- **CI: the GitHub `snapshot` pre-release holds exactly the current build, and the release assets
  include the pom.** `gh release upload --clobber` replaces same-named assets and never removes any
  other, so the `snapshot` release accumulated: the six 1.8.0-SNAPSHOT jars next to the 1.9.0-SNAPSHOT ones, 6
  of its 14 assets. `github-snapshot` now deletes every asset
  the run did not upload, after the upload and each printed by name; it refuses an empty upload,
  treats an asset that is already gone as removed and fails only after trying every deletion (asset
  by asset on purpose: the job's first form deleted and recreated the release, and failed in every
  burst of main pushes in May 2026); versioned releases are never pruned, and the next snapshot run
  clears the backlog by itself. The collect steps copy
  `<artifactId>-<version>.pom` + `.pom.asc` as well (Central had them, the release did not), and
  the signature check counts every attachable type (`*.jar`, `*.aar`, `*.pom`) instead of jars
  alone. Shared job text with the two sibling repositories (`shared-files.sha256`), the convention
  in `workspace/policies/fat-jar-release-assets.md`.

## [1.8.0] - 2026-08-29

### Added
- **Results can be broadcast, not just logged** — one message per *checked batch*, sent whether or
  not anything was found. Reporting only hits would leave a client unable to tell "range checked,
  nothing there" from "range never checked", which makes an automated from/to sweep unsafe: a range
  still queued when the process stops looks exactly like a clean one. The event is emitted after the
  batch has been checked, so it doubles as an exact completion signal. Available over all three
  existing transports, each off by default (`broadcastResults`), and each with different limits — the
  WebSocket serves both directions on one port and can catch up a client that connected early;
  ZeroMQ needs a second address (a `PULL` socket cannot send) and cannot detect subscribers at all,
  so it republishes the grid configuration periodically; plain TCP gets its own port and
  newline-delimited framing, because its inbound side reads fixed-width records from a single peer.
  **The payload contains private keys and every connected client receives every result.**
- **`examples/websocket_scanner.html`** — the browser as the whole user interface: it produces the
  ranges and displays the outcome over one connection. Defragmenter-style range map (in flight /
  checked / hit) plus a 256-bit view showing the base bits and the swept batch bits. It derives its
  step from the grid configuration the server announces, which is what keeps a systematic sweep from
  silently rescanning one block: the server aligns every incoming base down to a multiple of
  `2^batchSizeInBits`. Paired with `examples/config_Find_WebSocketUi.json`, which sets
  `timeoutMillis: -1` so a pause in the UI does not end the run.
- **Second contributed multi-GPU machine in the measurement registry** —
  `tuner_ryzen9800x3d_dualgpu.csv` carries all 73 arms of an RX 7900 XTX plus an integrated gfx1036,
  measured through the GPU-filtered pipeline with the shipped default candidates: **190.4 M keys/s**
  on the discrete card at `batchSizeInBits=24, keysPerWorkItem=512` and **4.61 M keys/s** on the
  iGPU at `23, 256`. Both winners came from the automatic sweep extension, which had to walk past
  the shipped candidate list on each device independently. The machine's older single-device file
  measured the full-transfer path and is not comparable — `docs/measurements/README.md` now says so
  rather than leaving two numbers for the same card side by side.
- **The same GPU behind two OpenCL platforms is swept once** — a duplicate ICD registration (common
  on Windows AMD systems, where driver and runtime both register) makes one card enumerate twice, so
  a two-card machine appeared as four devices and would have been measured, and then driven, twice
  over. Devices are now de-duplicated by a fingerprint of the *physical* card, taken from
  `cl_khr_device_uuid`, else `cl_khr_pci_bus_info`, else AMD's `cl_amd_device_attribute_query`. The
  first two are Khronos standards that NVIDIA and Intel report as well, so this is not AMD-specific.
  An unavailable fingerprint never merges: a rig of identical cards keeps every device, because the
  device name is deliberately not used as a signal.
- **The fat jar runs with a bare `java -jar`** — the assembly manifest now carries the `Add-Opens`
  that lmdbjava needs to reflect into `java.nio`/`sun.nio.ch`, so the launcher scripts are no longer
  required for LMDB access (they remain useful for the heap and Logback settings). Guarded by
  `JvmModuleFlagConsistencyTest`, which asserts the manifest entry equals the `java.base` opens in
  `.mvn/jvm.config` — the pom comment asked for that sync, nothing enforced it.
- **`TuneConfiguration` measures every GPU, and by default every one it detects** — it used to take
  `producerOpenCL.get(0)` silently. A dual-GPU configuration produced a full-looking report for one
  device and a paste-ready configuration whose second entry still held the operator's guesses,
  visually identical to the measured one; on a contributor's laptop that guess was 2.3× off that
  device's optimum. All configured entries are now swept in turn, each keeping its own winner, and an
  empty `producerOpenCL` list — previously an error — means "sweep every detected GPU", which is the
  state most first runs are in. CPU OpenCL runtimes (pocl, Intel's CPU ICD) are excluded, devices are
  measured one at a time so they do not contend, and the emitted configuration gives each producer
  its own key producer per the shipped examples' convention.
- **Light and Full DB entry counts refreshed** — `targetDatabaseEntries` defaults to `141045995`
  (Light, 2026-07-20 publication) instead of the stale `132288304`; the Full tier reference is
  `1472947953`. Historical measurement tables keep the counts they were measured at.
- **The tuning sweep extends itself instead of advising a re-run** — when the winner lands on the
  largest `keysPerWorkItem` or `batchSizeInBits` tried, `TuneConfiguration` keeps measuring past the
  configured candidates (doubling, or one bit at a time) until an extra arm stops improving. A winner
  at the edge is a lower bound, not a peak, and "widen the list and run it again" only reaches
  someone who reads the guide first — a 20-minute measurement is run once. Two field sweeps both won
  at the shipped list's last value of 256; widening by hand afterwards gained 17 % and 104 %. Costs
  one arm per step, bounded by `maxExtensionArms` (default 4), disabled with
  `extendSweepWhenWinnerIsAtTheEdge: false`. `batchSizeInBits=24` is treated as the framework
  maximum rather than a truncation, so it extends no further and the report says why.
- **Separate CI pipeline for the measurement tooling** — `.github/workflows/measurement-tools.yml`
  runs the `register_machine.py` tests on Python 3.9 and 3.13, path-filtered to
  `docs/measurements/**`. Kept out of the Java build, which shares none of its setup and takes
  minutes rather than a second.
- **Step-by-step tuning guide** — [`docs/tuning-your-gpu.md`](docs/tuning-your-gpu.md) walks through
  finding device indices, writing a tuning config, capturing the log, reading the report, tuning a
  second GPU and contributing the numbers back, with every command explained. Linked from the
  README's `TuneConfiguration` section.
- **`TuneConfiguration` distinguishes "too slow to measure" from a failure** — an arm that ran
  without error but completed no batch inside its measurement window now reports `NOT MEASURED:` with
  the reason and the remedy, instead of a bare `0.00 candidates/s` that reads like a driver
  rejection. Happens at large `batchSizeInBits` with tiny `keysPerWorkItem`.
- **First contributed multi-GPU machine in the measurement registry** —
  `coreultra7155h-95g-win11` (Lenovo ThinkPad P14s Gen 5: Core Ultra 7 155H, RTX 500 Ada + integrated
  Arc Pro), with `tuner_coreultra7155h.csv` carrying all 84 measured arms for both devices. Peaks:
  **110.7 M keys/s** on the RTX at `batchSizeInBits=24, keysPerWorkItem=2048` and **36.5 M keys/s**
  on the Arc at `23, 2048`. Until now every registered machine was high-end desktop hardware with a
  single GPU.

### Changed
- Dependencies bumped to latest stable: `com.google.protobuf:protobuf-javalite` 4.35.1 → 4.36.0
  (4.36.0 left RC), `org.lwjgl:lwjgl` / `lwjgl-opencl` 3.4.2 → 3.4.3 (including every `natives-*`
  classifier), `com.github.spotbugs:spotbugs-maven-plugin` 4.10.3.0 → 4.10.4.0.
- CI actions bumped to latest: `actions/setup-java` v5 → v6, `github/codeql-action/*` v4.37.7 →
  v4.37.8.
- **The OpenCL binding moved from JOCL to LWJGL 3** (`org.lwjgl:lwjgl` + `lwjgl-opencl` 3.4.2);
  `org.jocl` is gone from the build. Calls no longer reach the binding directly: a new
  `opencl/binding/` package carries an instance-based `ClApi` (mockable, unlike the binding's static
  surface), typed handle records restoring the per-object types LWJGL collapses into a bare `long`,
  and explicit error checking — LWJGL only *returns* `cl_int` codes and never throws, so an unchecked
  call would fail silently. Three consequences are worth knowing:
  - **A stock Linux install would not have started.** LWJGL resolves the library by plain name, but
    an OpenCL runtime package ships only the versioned SONAME `libOpenCL.so.1`; the unversioned
    symlink comes from the separate development package. JOCL never noticed, its JNI shim being
    linked against the SONAME. `OpenClLibraryLoader` therefore tries a fallback name chain,
    reproduced in a container carrying `pocl-opencl-icd` and nothing else.
  - **Host-to-device uploads are written in bounded slices** (`ClApi.writeBufferChunked`) instead of
    a `CL_MEM_COPY_HOST_PTR` allocation. LWJGL cannot hand a Java array to the driver, so a
    single-shot upload would need an off-heap staging copy the size of the payload — several
    gigabytes for the Full-DB 16-bit filter, whose byte image does not even fit a Java `byte[]`.
  - **The fat jar stays a single jar.** LWJGL ships one `natives-*` classifier per platform, but all
    nine may sit on one classpath and it picks the match at runtime, so no classifier split is
    needed.
- **The documented iGPU interference figure is qualified, not a single number** — `docs/tuning-your-gpu.md`
  cited "~3×" from one laptop pair (Intel Arc next to an RTX 500 Ada). A desktop pair (AMD gfx1036 next
  to an RX 7900 XTX) measured ~1.5×. Both are now shown side by side with the advice to measure your
  own, since platform, bandwidth headroom and thermal coupling all move it.
- **`machines.json` records `gpu` as a list** — multi-GPU machines are the normal case (any laptop
  with a discrete card also has an integrated one, and this project runs on both), so the field
  holds every device instead of a comma-joined string that readers had to split. Existing entries
  migrated; `register_machine.py --set gpu="A,B"` accepts a comma-separated value. `plot.py` reads
  only `cpu.l3_mb` from the registry and is unaffected.
- **Statistics line reports candidates per producer instead of dispatched batches** — the
  `Batches per producer` group is replaced by `Keys per producer`, which prints each producer's
  generated-candidate count and its **share of the total**
  (`gpu0 (Incremental, GPU)=19 G (94.0%), gpu1 (Random, GPU)=1 G (6.0%)`). Batch counts are not
  comparable between producers: one batch yields `2^batchSizeInBits` candidates, so on a multi-GPU
  run with different batch sizes the old field read as a near-even split where the real work split
  was an order of magnitude apart. `RuntimeStatistics` still tracks batch counts (used by
  `TuneConfiguration`); only the rendered field changed.

### Fixed
- **CVE-2026-49844** (GHSA-qv9r-c865-cp47, moderate): `org.apache.logging.log4j:log4j-api`
  2.25.3 arrives as a **test-scope** transitive of `io.github.hakky54:logcaptor` 2.12.6, and
  Dependabot could not update it on its own. Pinned `log4j-api` **and** `log4j-to-slf4j` to
  **2.26.1** in `dependencyManagement` — both together, since `log4j-to-slf4j` requires a
  matching `log4j-api` and the two must not skew. Neither reaches a published artifact.
- **A run fed by a blocking key producer could not be shut down** — `Finder.interrupt()` waited for
  the producers to stop *before* waking the key producers. A producer waiting for its next secret is
  parked inside the key producer, and one configured to block indefinitely (`timeoutMillis: -1`,
  which is what an interactively driven source wants) only releases that wait when interrupted — so
  the wake-up came after the wait it was supposed to end, and every shutdown stalled until
  `shutdownTimeoutSeconds` ran out. Key producers are now interrupted first.
- **A finished run logged `Main#run end.` and then never exited** — reported for incremental scans,
  reproduced here for a GPU producer, for `runOnce`, and for a merely *configured* socket / websocket
  / ZeroMQ key producer even on the CPU path. The completion path stopped the producers but never
  released what they own, so a producer's result-reader pool and the key producers' reader threads —
  all non-daemon — kept the JVM alive; a thread dump showed exactly `maxResultReaderThreads` idle
  workers parked in `LinkedBlockingQueue.take()` with `DestroyJavaVM` waiting on them. Releasing them
  happened only in `Finder.interrupt()`, which on that path was reached solely from the JVM shutdown
  hook — and a shutdown hook cannot run until every non-daemon thread has already ended, so the
  release that would have ended them was unreachable. The teardown now runs on the completion path
  itself. Note that `runOnce` is not a workaround and never was: it runs `produceKeys()` once, which
  is one *batch*, not one range.
- **Two overlapping teardowns released the same producer twice** — surfaced by the fix above, since
  the teardown now runs both on the completion path and from the shutdown hook, and a `Ctrl+C`
  landing while a finished run releases its producers is enough to overlap them. Each caller read the
  producer list before either had cleared it, and the second release of an OpenCL producer is
  rejected by the driver with `CL_INVALID_MEM_OBJECT`, ending an otherwise successful run with a
  fatal error. The "release each producer exactly once" guarantee was implicit in the order of a loop
  and a list-clear; it is now an object of its own (`ProducerReleaser`, tracking producers by
  identity), which is also what made it statable as a test.
- **CI on `main` was red after the multi-GPU merge** — two independent failures, both reachable
  locally and neither run before merging. `spotbugs:check` reported 14 findings in the new code; ten
  were fixed properly (a defensive copy in `DeviceSweep`, wider parameter types, exception messages
  that name the device and the counts, literal format strings, an empty array instead of `null`) and
  four are suppressed with rationale, following the precedent already in `spotbugs-exclude.xml`.
  Separately, `run_noProducerConfiguredButGpuPresent_sweepsTheDetectedDevices` failed on the pocl CI
  job: an OpenCL device is not necessarily a GPU, and auto-discovery deliberately sweeps GPUs only,
  so the test asked the tuner to find something correctly filtered out. It now assumes a GPU-type
  device via the existing `hasGpuOpenCLDevice()` helper.
- **Auto-discovered devices are swept with the GPU pre-filter on** — they inherited
  `CProducerOpenCL`'s default of `false`, which caused three faults at once: the filter payload the
  tuner builds for `targetDatabaseEntries` was thrown away unused, the sweep measured full-transfer
  throughput while claiming to describe the pipeline the operator will run, and every candidate
  crossed back to the host. The last one killed real runs: the result reader materialises
  `2^batchSizeInBits` `PublicKeyBytes` per grid, so a sweep died with `OutOfMemoryError: Java heap
  space` at `batchSizeInBits=22` on a 15.4 GiB heap — below the shipped candidate list's maximum
  of 24. Emptying the example's `producerOpenCL` list to demonstrate auto-discovery had silently
  moved it onto that default and taken its `enableGpuFilter: true` away. A sweep that still runs
  without a filter now warns up front with the estimated host-heap cost, because the JVM's own error
  names neither the setting nor the grid size responsible.
- **The sweep extension judged a slow device by a fast one's winner** — on a multi-device run the
  decision consulted the global best arm instead of the device being swept, so an integrated GPU that
  won at the largest `keysPerWorkItem` tried got no extension: the discrete card's winner sat inside
  its own range and reported "nothing to extend". The slower device's reported optimum was therefore
  a silent lower bound — exactly what the extension exists to prevent. Invisible on a single-GPU
  machine, where the global and the device winner are the same arm; found on a dual-GPU one.
- **Device de-duplication requires the name to agree, not just the identity** — a fingerprint shared
  by *differently named* devices is a driver defect rather than one card seen twice, and acting on it
  would silently halve a multi-GPU machine with nothing in the run reporting it. The name remains a
  veto and never a merge signal (merging on names would collapse a rig of identical cards). The
  asymmetry decides it: a missed merge costs one redundant sweep, a wrong merge costs half the
  hardware. Cheap insurance, since two of the three fingerprint sources have not yet run on real
  hardware — the only dual-ICD machine available reports neither Khronos extension.
- **`disableAddressLookup: true` no longer opens LMDB** — it opened the environment unconditionally
  and failed with `ESRCH` / `InaccessibleObjectException` when no database existed, which is exactly
  the situation disabling the lookup is meant to permit. A pure key-generation run, or the tuner's
  paste-ready config before a database has been imported, now works with no database on disk.
- **Example launchers and the tuning guide referenced a stale jar version** — 22 `run_*` scripts and
  `docs/tuning-your-gpu.md` still named `1.7.0`. Bumped to the project version and guarded by
  `ExampleRunScriptJarVersionTest`, which reads the version from `pom.xml` so it cannot drift again.
- **`TuneConfiguration` no longer reports a failing arm as a measurement** — an arm measured while
  the device was throwing errors was reported with whatever throughput it managed, indistinguishable
  from a healthy arm. A real sweep on an Intel Arc recorded `1,022,288 candidates/s` for an arm
  during which the producer hit `CL_OUT_OF_RESOURCES` **40 times**, and it was the arm immediately
  before every larger grid began failing — so the row that would have explained the cascade was
  presented as normal. Neither failure mode could reach the thread that started the producer: a
  fatal exception ends the run loop quietly, and a per-secret failure is caught, logged and skipped
  while the producer stays alive and looks healthy. Producers now record both
  (`ProducerStateProvider#getLastFailure()`), and the sweep marks such an arm `FAILED` with the
  cause, discarding the rate.
- **REUSE compliance restored** — the licensing check had been failing on `main`. All measurement
  data and generated plots are now covered by globs in `REUSE.toml` (so new machines and benchmark
  runs stay compliant without an edit), and four example configs that were never added to the
  existing list are included. 486/486 files compliant, was 462/486.
- **`register_machine.py` no longer hides every non-NVIDIA GPU** — `detect_gpu()` returned as soon
  as `nvidia-smi` named anything, which made the platform-wide enumeration unreachable on exactly
  the machines that need it: a laptop with an NVIDIA card *and* an integrated GPU recorded only the
  NVIDIA. All sources are now consulted and merged, with duplicates dropped. Detection reports what
  the OS enumerates, which need not match the OpenCL device list — `OpenCLInfo` stays authoritative
  for which devices this tool can drive.
- **Rate and count formatting no longer collapses precision at a unit boundary** — `formatRate` and
  `formatCount` rounded the scaled value to a whole number, so everything from 1.000 to 1.499 G/s
  printed as `1 G/s` and tuned runs became impossible to compare once they crossed 1 G/s (reported
  in #250). The same collapse happened at the `k/s` and `M/s` boundaries. Both now keep four
  significant digits: `1.400 G/s`, `130.0 M/s`, `2.013 k/s`, `412/s`.
- **Multi-line reports are readable again** — the `TuneConfiguration` report, the OpenCL device dump
  and the transformed-configuration echo were folded onto a single line separated by ` | ` by the
  log pattern's CRLF guard, forcing users to reformat them by hand to read them (reported in #250).
  They are now emitted one log record per line via `util.MultilineLogger`. The guard is unchanged
  and undiminished: every emitted line still carries a prefix the appender wrote, and the split
  consumes every line-break form so no line can carry another past it.

## [1.7.0] - 2026-07-23

Major release centred on high-performance probabilistic address filters (in-memory and GPU-side),
a self-tuning benchmark mode, a rebuilt runtime statistics log, and a reworked bulk-import pipeline.

### Added
- **Binary Fuse filters as address-lookup backends** — `BINARY_FUSE_8` (~1.13 B/entry, ~0.39 % FPR)
  and `BINARY_FUSE_16` (~2.25 B/entry, ~0.0016 % FPR) in-memory presence filters in front of LMDB,
  built by multi-pass peeling with progress logging. LMDB stays open to verify the handful of
  survivors, so a false positive can never be reported as a hit.
- **`BLOCKED_BLOOM` address-lookup backend** — a 512-bit-block Bloom filter sized with fastrange
  (exact, no power-of-two rounding) and configurable geometry (`blockedBloomBitsPerEntry`,
  `blockedBloomK`).
- **GPU-side filter cascade** — `producerOpenCL.gpuFilterType` (`FUSE_8` / `FUSE_16`) builds a filter
  from the database and uploads it to the GPU, so the kernel checks each derived hash160 inline and
  transfers back only the survivors ("compact mode"), collapsing the PCIe read-back. Independent of
  the CPU `addressLookupBackend`; works with the `LMDB_ONLY` default.
- **`TuneConfiguration` command** — measures the net end-to-end throughput of every
  `batchSizeInBits × keysPerWorkItem` arm on your own hardware and emits the winning config as
  ready-to-paste JSON, plus a measured database-lookup cost. The sweep runs up to the framework
  maximum `batchSizeInBits = 24`.
- **Rebuilt runtime statistics log** — reports `Generated` (candidate keys the producers actually
  compute) next to `-> LMDB` (survivors reaching the database) and the `pre-filtered` share, over a
  trailing windowed rate that auto-scales `/s … G/s`. Replaces the old consumer-only rate, which made
  a filtered GPU run look idle.
- **Filter build progress logging** — `reading → indexing → peeling → assigning → ready` phases with
  rate/ETA for every in-memory filter build.
- **Measurement suite** — storage-free filter comparison (`FilterMeasurementMain`,
  `bench_filters.sh`), GPU filter-probe and JMH benchmarks, and CSV-backed data under
  `docs/measurements/` with generated plots and tables (`plot.py`, self-registering `machines.json`).
- Example configs for the GPU filter cascade and per-tier filter choices; `useNoReadAhead` on the
  read-only LMDB configuration.
- **Decoupled, multi-threaded `AddressFilesToLMDB` import** — a single buffered reader streams file
  lines into a bounded queue, `threads` parser workers decode them, and one writer stores them in
  LMDB in batches (one transaction per `writeBatchSize` entries). New `CAddressFilesToLMDB` fields
  `threads` (default 1), `writeBatchSize` (default 32768) and `queueCapacity` (default 200000). Adds
  per-file byte-offset read progress and an "X/Y files" counter. `threads = 1` preserves the exact
  deterministic import order; higher values parse in parallel — order-independent when
  `useStaticAmount = true`, otherwise a non-determinism warning is logged.
- **`CompactLMDB` command** — writes a compacted copy of an existing LMDB database via LMDB's
  `MDB_CP_COMPACT` (free/dead pages omitted, pages laid out sequentially → a smaller, read-denser
  `data.mdb` in a separate target directory). The source is opened read-only and left unchanged; the
  compacted copy is re-opened and its entry count verified against the source. Ships an example
  `config_CompactLMDB.json` and run scripts.
- **Durable LMDB flush on close** — the writable env keeps its fast sync-free write flags
  (`MDB_NOSYNC` / `MDB_MAPASYNC`) during the import for speed and now forces one full `env.sync(true)`
  when closing, so a normal shutdown leaves the whole database on disk.
- **`LMDBDelta` command** — streams every address present in one or more source LMDB databases but
  **not** in a reference database to a plaintext file (one mainnet Base58 `1…` address per line,
  re-importable with `AddressFilesToLMDB`). It walks the key-ordered databases as a near-zero-memory
  k-way cursor merge (memory is just one 20-byte key per cursor, independent of the delta size), logs a
  per-source summary of how many delta addresses each source contained, and reports periodic progress
  with rate/ETA. See `examples/config_LMDBDelta.json` and the `run_LMDBDelta` launchers.

### Changed
- **`FUSE_16` is the recommended default GPU pre-filter** (best net end-to-end throughput; fits VRAM
  even at the Full DB tier). Documented in the README, `docs/filter-selection.md`, and
  `docs/performance.md`.
- Blocked Bloom `DEFAULT_K` 8 → 6, matching the fastrange density; the in-RAM Bloom filter is now
  keyed on 8 bytes instead of 20 (~22 % faster lookups).
- JSON/YAML config serialization uses field-only visibility so round-trips are stable (fixes derived
  getters such as `getOverallWorkSize` leaking into the emitted configuration).
- Dependency bumps: `bcprov-jdk15to18` 1.84 → 1.85, plus routine updates to `junit-jupiter`,
  `jackson`, `logback-classic`, `nullaway`, `pitest-maven`, `spotless`, the Checker Framework, and CI
  actions.
- **GPU example configs default to the inlined kernel** (`producerOpenCL.noInlineHelpers: false`) for
  full runtime throughput — measured ~3.6× faster than the out-of-lined kernel on an AMD RX 7900 XTX
  (RDNA3) and ~4.5× on NVIDIA; the trade-off is a one-time slow first compile on AMD (then
  `comgr`-cached). The runtime auto-default is unchanged (out-of-line on AMD to keep CI compiles fast)
  and now logs symmetric warnings so the compile-vs-speed trade-off is never silent.

### Fixed
- **GPU Fuse-16 upload at the Full DB tier** — the `short[]` fingerprints were flattened into a
  `byte[]` that overflowed `Integer.MAX_VALUE` (`NegativeArraySizeException`); they are now uploaded
  directly as `short[]`.
- **GPU filter upload under host-memory pressure (RDNA3)** — `clCreateBuffer` with
  `CL_MEM_COPY_HOST_PTR` failed with `CL_MEM_OBJECT_ALLOCATION_FAILURE` while the build's transient
  heap garbage was still resident; the heap is now reclaimed (`System.gc()`, injectable) immediately
  before the upload. Verified on an RX 7900 XTX.
- **`TuneConfiguration` on smaller GPUs** — an arm whose grid is too large for the device is caught
  and recorded as unusable instead of aborting the whole sweep, so `batchSizeInBits` candidates up to
  the hard framework cap can be swept on any card.
- Measurement plots — the GPU filter-probe chart now shows the 1 B blocked-Bloom bars
  (density-encoded filter names were not matched), and the blocked-Bloom sizing figure plots one
  coherent series instead of a zigzag across machines and `k`.
- Documentation corrected for the current statistics-log format, the keys-vs-addresses relationship
  (each key yields both a compressed and an uncompressed address, so addresses examined are twice the
  `Generated` rate and `pre-filtered` is measured against that doubled figure), and stale constant
  references.
- **Corrected the light/full prepared-database backend recommendation** in the README from
  `TRUNCATED_LONG_64` to `BINARY_FUSE_8` — `TRUNCATED_LONG_64` has the worst lookup latency of any
  backend at 100 M+ entries (it was already flagged as "never" for this in `docs/filter-selection.md`),
  while `BINARY_FUSE_8` costs ~7× less RAM and is far faster; documented the `FUSE_16` GPU +
  `BINARY_FUSE_8` CPU cascade at both tiers.
- **Reconciled the Full-DB backend recommendation across all docs.** An earlier recommendation flip
  (blocked Bloom → `BINARY_FUSE_8`, on total cost once Fuse-8's Full-DB build was shown feasible)
  updated `docs/filter-selection.md` and most of the README but missed several spots, leaving them
  contradictory. `docs/performance.md`, the remaining README conclusions, and the `BLOCKED_BLOOM`
  enum Javadoc now consistently recommend `BINARY_FUSE_8` on total cost, with `BLOCKED_BLOOM` framed
  as the rebuild-heavy / heap-constrained niche. The stale `BLOCKED_BLOOM` enum Javadoc (pre-fastrange
  power-of-two sizing, 1.56/2.06 B/entry, 0.49/0.18 % FPR) was corrected to the current fastrange
  numbers (1.375 B/entry at the default 11 bits/entry, ~0.76 % FPR); a stale README lookup-latency
  table was re-synced to the CSV-authoritative one; and the Full-DB RAM figures for the fuse filters
  were corrected (~1.8/3.6 GB → ~1.5/3.1 GB).

[Full changelog](https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.1...v1.7.0)

## [1.6.1] - 2026-06-18

### Fixed
- Release/CI: re-release of `1.6.0` to complete the publish pipeline. The `1.6.0`
  run published to Maven Central but did not attach the signed artifacts to the
  GitHub release. No functional code changes versus `1.6.0`.

## [1.6.0] - 2026-06-18

### Added
- OpenSSF Best Practices passing-level artifacts: `CONTRIBUTING.md`, `SECURITY.md`,
  `CHANGELOG.md`, and `docs/RELEASE.md`.

### Changed
- Refactored `PrivateKeyValidator` from static helper methods on `KeyUtility` into a
  dedicated non-static helper class, improving testability and dependency clarity.
- Migrated JSON and YAML configuration parsing from Gson + SnakeYAML to Jackson.
- Migrated publishing from OSSRH (legacy) to the Sonatype Central Publisher Portal.
- Unified the release pipeline into a single `publish.yml` workflow with a manual
  start-gate.

### Notes
- See the `Release Process` template in [`docs/RELEASE.md`](docs/RELEASE.md).

## [1.5.0] - 2025-07-29

### Added
- Key Producer for Sockets.

[Full changelog](https://github.com/bernardladenthin/BitcoinAddressFinder/compare/1.4.0...v1.5.0)

## [1.4.0] - 2025-07-05

First non-SNAPSHOT release; published to Maven Central.

### Changed
- Bump `com.google.guava:guava` from 33.4.6-jre to 33.4.7-jre
  ([#52](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/52)).
- Bump `com.google.code.gson:gson` from 2.12.1 to 2.13.0
  ([#51](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/51)).
- Bump `commons-io:commons-io` from 2.18.0 to 2.19.0
  ([#54](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/54)).
- Bump `com.google.guava:guava` from 33.4.7-jre to 33.4.8-jre
  ([#55](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/55)).
- Bump `com.google.code.gson:gson` from 2.13.0 to 2.13.1
  ([#56](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/56)).
- Bump `org.mockito:mockito-core` from 5.17.0 to 5.18.0
  ([#58](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/58)).
- Bump `org.apache.maven:maven-artifact` from 3.9.9 to 3.9.10
  ([#59](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/59)).

### New Contributors
- @dependabot made their first contribution in
  [#52](https://github.com/bernardladenthin/BitcoinAddressFinder/pull/52).

[Full changelog](https://github.com/bernardladenthin/BitcoinAddressFinder/compare/1.3.0-SNAPSHOT...1.4.0)

## [1.3.0-SNAPSHOT] - 2024-04-06

First Java 21+ snapshot build.

- Example configurations:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/tree/1.3.0-SNAPSHOT/examples>
- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

## [1.2.0-SNAPSHOT] - 2024-03-28

Earlier Java 21+ snapshot build.

- Example configurations:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/tree/1.2.0-SNAPSHOT/examples>
- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

## [1.1.0-SNAPSHOT] - 2022-12-19

Java 11+ snapshot build.

- Example configurations:
  <https://github.com/bernardladenthin/BitcoinAddressFinder/tree/1.1.0-SNAPSHOT/examples>
- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

## [1.0.0-SNAPSHOT] - 2021-02-10

First public pre-release version (Java 8).

- LMDB database:
  <https://github.com/bernardladenthin/BitcoinAddressFinder#use-my-prepared-database>

[Unreleased]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.8.0...HEAD
[1.8.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.1...v1.7.0
[1.6.1]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.5.0...v1.6.0
[1.5.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.4.0...v1.5.0
[1.4.0]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.3.0-SNAPSHOT...v1.4.0
[1.3.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.2.0-SNAPSHOT...v1.3.0-SNAPSHOT
[1.2.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.1.0-SNAPSHOT...v1.2.0-SNAPSHOT
[1.1.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/compare/v1.0.0-SNAPSHOT...v1.1.0-SNAPSHOT
[1.0.0-SNAPSHOT]: https://github.com/bernardladenthin/BitcoinAddressFinder/releases/tag/v1.0.0-SNAPSHOT
