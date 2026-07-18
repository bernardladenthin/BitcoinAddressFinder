<!--
SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>

SPDX-License-Identifier: Apache-2.0
-->

# Measurement data

The CSVs in this directory are the **single source of truth** for every performance number quoted in
[`../performance.md`](../performance.md). Markdown cannot import data, so the tables there are
*generated* from these CSVs and rewritten in place between `<!-- BEGIN GENERATED:… -->` markers.

**Never edit inside those markers, and never retype a number into prose.** Edit the CSV, then run:

```bash
python docs/measurements/plot.py
```

That regenerates `plots/*.png` and the generated tables in one step.

## Files

| File | Contents |
|---|---|
| `machines.json` | Machine registry keyed by `machine_id`, written by `register_machine.py`. Every measurement row references it. |
| `register_machine.py` | Detects this machine's CPU/L3/RAM/GPU/OS/JDK and registers it. Run once per machine. |
| `filter_lookup.csv` | Lookup latency (ns/op) per backend and entry count — `FilterLookupBenchmark`. |
| `filter_build.csv` | Build time, retained memory, FPR against **real** LMDB databases — `FilterMeasurementMain`. |
| `k_sweep.csv` | Blocked Bloom false-positive rate vs `k`, per bit density. |
| `filter_sizing.csv` | Blocked Bloom FPR and speed vs filter size at fixed `k`. |
| `plot.py` | Reads all of the above; writes `plots/*.png` and the generated tables. |

## Adding your own machine

Results are hardware-dependent — most of all the lookup-latency curves, whose shape is governed by
**L3 cache size**. The crossover between `BINARY_FUSE_8` and `BLOCKED_BLOOM` sits where the fuse
array outgrows L3, so a machine with a different cache will place it elsewhere. That is exactly why
measurements are keyed by machine rather than averaged.

1. **Register the machine.** This detects the hardware and writes `machines.json` for you — no
   hand-editing:

   ```bash
   python docs/measurements/register_machine.py --set storage="<your disk>"
   python docs/measurements/register_machine.py --dry-run          # preview only
   python docs/measurements/register_machine.py --id my-own-name   # override the generated id
   ```

   It prints the `machine_id` to use in every measurement row (e.g. `ryzen75800h-63g-win11`) and is
   idempotent — re-running updates the same entry instead of duplicating it. Anything it cannot
   detect stays `null` and can be filled in with `--set`, including nested fields
   (`--set cpu.l3_mb=32`).

   **Check `l3_mb`.** If detection failed there, set it manually: the lookup-latency plot annotates
   the L3 boundary from this value, and the whole crossover story is expressed in terms of it.

2. **Materialise the classpath** (once):

   ```bash
   mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp-test.txt -DincludeScope=test
   ```

3. **Run the benchmarks.** The `--add-opens` set must match `pom.xml`'s `<argLine>` (lmdbjava
   reflects into `sun.nio.ch`). Use `;` as classpath separator on Windows, `:` on POSIX.

   Lookup latency — storage-free, so no database is required:

   ```bash
   java -Xmx12g --add-opens=java.base/java.lang=ALL-UNNAMED \
        --add-opens=java.base/java.io=ALL-UNNAMED --add-opens=java.base/java.nio=ALL-UNNAMED \
        --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
        --add-opens=java.base/jdk.internal.misc=ALL-UNNAMED \
        --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
        -cp "target/test-classes;target/classes;$(cat target/cp-test.txt)" \
        org.openjdk.jmh.Main FilterLookupBenchmark \
        -p entries=100000,1000000,10000000 -f 1 -wi 3 -w 2 -i 5 -r 3
   ```

   Large scales (omit `HASHSET`, it needs ~80 B/entry — about 8 GB at 100 M):

   ```bash
   ... org.openjdk.jmh.Main FilterLookupBenchmark \
       -p backend=BLOOM,TRUNCATED_LONG_64,BINARY_FUSE_8,BINARY_FUSE_16,BLOCKED_BLOOM \
       -p entries=50000000,100000000 -f 1 -wi 2 -w 2 -i 4 -r 3
   ```

   `k` sweep at a chosen bit density, without any database — bit density and per-block load are
   scale-independent, so a small synthetic set answers the same question as a billion-entry build:

   ```bash
   # 10,737,418 entries at bitsPerEntry=12 reproduces the Full DB's 12.5 bits/entry
   ... net.ladenthin.bitcoinaddressfinder.benchmark.FilterMeasurementMain \
       "prng:10737418" BLOCKED_BLOOM 2000000 <k> 12
   ```

   Build time and FPR against a real database (needs an LMDB directory):

   ```bash
   ... net.ladenthin.bitcoinaddressfinder.benchmark.FilterMeasurementMain \
       /path/to/lmdb BLOCKED_BLOOM 5000000
   ```

4. **Append the rows** to the matching CSV with your `machine_id`, then re-run `plot.py`. Plots draw one
   line per machine, so several machines can coexist in one figure.

## Measurement hygiene

Lessons that cost real time in this project:

- **Give JMH enough iterations.** A Fuse-8 point once measured 37.2 ± 50.3 ns — an error bar wider
  than the score — and it happened to sit under a load-bearing conclusion. Re-run with `-i 5`; if the
  error is a large fraction of the score, the number is not usable.
- **Sweep past the interesting regime.** Data that stopped at 10 M suggested there was no crossover
  at all. It appears between 10 M and 50 M.
- **Cold-cache runs need `PageCacheBuster`.** Anything reading a database larger than free RAM is
  dominated by cache state — measured ~7× between warm and cold on the Full DB. Run
  `PageCacheBuster <GiB>` before each arm of an A/B, and record `free_ram_gb_at_start`.
- **Free RAM matters more than cache warmth** for full-database builds: 1 869 s with ~2 GB free
  versus 1 043 s from a *colder* cache with 58 GB free.
- **Check platform support before measuring a flag.** `MDB_NORDAHEAD` is a no-op on Windows; a
  careful A/B there measured nothing but run-to-run variance (~7–8 %, which is a useful noise floor
  but was not the intended result).
