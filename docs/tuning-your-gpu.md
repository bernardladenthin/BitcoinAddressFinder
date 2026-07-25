# Tuning your GPU — a step-by-step guide

This guide walks through measuring the best `producerOpenCL` settings for **your** hardware with the
`TuneConfiguration` command, and (optionally) contributing the result back so other people with
similar hardware get a useful starting point.

Every command is spelled out and explained. You do not need to know Java or Maven — for the tuning
part you only need the released jar. The contribution part additionally needs Python 3 and a git
clone.

**Time required:** about 15 minutes of setup, then 15–60 minutes of unattended measuring per GPU.

**What this is not:** the tuner does not read, build or modify your address database. It builds a
synthetic filter of the size you tell it to and throws it away afterwards.

---

## Table of contents

1. [What the tuner actually measures](#1-what-the-tuner-actually-measures)
2. [Before you start](#2-before-you-start)
3. [Step 1 — find your devices](#3-step-1--find-your-devices)
4. [Step 2 — write a tuning config](#4-step-2--write-a-tuning-config)
5. [Step 3 — run the tuner and capture the log](#5-step-3--run-the-tuner-and-capture-the-log)
6. [Step 4 — read the report](#6-step-4--read-the-report)
7. [Step 5 — apply the result](#7-step-5--apply-the-result)
8. [Tuning a second GPU](#8-tuning-a-second-gpu)
9. [Contributing your numbers](#9-contributing-your-numbers)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. What the tuner actually measures

`TuneConfiguration` runs your pipeline once for every combination ("arm") of two settings:

| Setting | What it controls |
|---|---|
| `batchSizeInBits` | How many candidate keys one GPU launch computes: `2^batchSizeInBits`. `20` means 1 048 576 keys per launch. |
| `keysPerWorkItem` | How many keys each GPU thread computes within that launch. |

For each arm it measures the **net end-to-end throughput** — candidate keys per second through the
whole pipeline, not just kernel time — and prints a table of every arm plus the winner.

The best combination is **device-specific and not guessable**. On the same laptop, one GPU peaked at
`batchSizeInBits=23` and another at `20`. Vendor, driver, memory type and compute-unit count all
move it.

---

## 2. Before you start

You need:

- **The runnable jar.** `bitcoinaddressfinder-<version>-jar-with-dependencies.jar`, from the
  [releases page](https://github.com/bernardladenthin/BitcoinAddressFinder/releases) or built with
  `mvn package -P assembly -DskipTests`.
- **A Java 21+ runtime.** Check with `java -version`.
- **Working GPU drivers with OpenCL.** Step 1 verifies this.
- **The launcher scripts** from [`examples/`](../examples/) — `run_TuneConfiguration.bat` (Windows)
  or `run_TuneConfiguration.sh` (Linux/macOS).

> **Use the launcher, not a bare `java -jar`.** The launchers pass a set of `--add-opens` flags that
> the JVM needs before it will let the LMDB layer reach into `java.nio` internals. Without them the
> tuner throws `InaccessibleObjectException` the moment you point it at a real database. A bare
> `java -jar` only works if no database is configured at all.

Put the jar, the launcher and your config file in the same directory. The launcher refers to the jar
by name, so if your version differs, open it in a text editor and fix the filename on the
`bitcoinaddressfinder-…jar` line.

---

## 3. Step 1 — find your devices

The tuner targets one device at a time, identified by two numbers: `platformIndex` (which OpenCL
driver) and `deviceIndex` (which device under that driver). Ask the tool what it sees.

[`examples/config_OpenCLInfo.json`](../examples/config_OpenCLInfo.json) already contains everything
this needs — the whole file is two lines:

```json
{
  "command": "OpenCLInfo"
}
```

Run it with the supplied launcher (`run_OpenCLInfo.bat` on Windows, `run_OpenCLInfo.sh` elsewhere),
or directly:

```bash
java -jar bitcoinaddressfinder-1.7.0-jar-with-dependencies.jar config_OpenCLInfo.json
```

This is the one command where a bare `java -jar` is fine — no database is involved.

You get one block per device. The two numbers you need are printed with it, along with a heuristic
starting point:

```
--- Info for OpenCL device: NVIDIA RTX 500 Ada Generation Laptop GPU ---
CL_DEVICE_NAME:                        NVIDIA RTX 500 Ada Generation Laptop GPU
CL_DEVICE_MAX_COMPUTE_UNITS:           16
CL_DEVICE_MAX_MEM_ALLOC_SIZE:          1023 MByte
SUGGESTED START CONFIG (heuristic from the info above; sweep keysPerWorkItem to confirm):
    producerOpenCL.batchSizeInBits = 21
    producerOpenCL.keysPerWorkItem = 256
```

Write down, for each GPU you want to tune, its **platform index** and **device index**. Platforms are
numbered from 0 in the order printed; devices are numbered from 0 **within each platform**.

> **Two GPUs from different vendors are usually on different platforms.** An NVIDIA card and an Intel
> integrated GPU are typically `platformIndex 0 / deviceIndex 0` and `platformIndex 1 / deviceIndex 0`
> — *not* device 0 and 1 of the same platform. Two cards from the same vendor usually *are* device 0
> and 1 of one platform. Reading it off the output beats guessing.

If this command lists no devices, stop here and fix your OpenCL installation — nothing below will
work. See [Troubleshooting](#10-troubleshooting).

---

## 4. Step 2 — write a tuning config

Start from [`examples/config_TuneConfiguration.json`](../examples/config_TuneConfiguration.json) and
change four things. Copy it to `config_TuneConfiguration_gpu0.json` (name it after the device — you
will make one per GPU).

### 4.1 Point it at your device

In the `producerOpenCL` block:

```json
"platformIndex": 0,
"deviceIndex": 0,
```

Use the numbers from Step 1.

### 4.2 Widen `keysPerWorkItemCandidates` — this one matters

The shipped default is:

```json
"keysPerWorkItemCandidates": [1, 4, 16, 64, 256],
```

On modern GPUs the best value is frequently **above 256**, and a sweep that stops at 256 will report
256 as the winner simply because it never tried anything larger. Use:

```json
"keysPerWorkItemCandidates": [16, 64, 256, 512, 1024, 2048],
```

Dropping `1` and `4` costs you nothing — they are far too small to win on any GPU — and pays for the
larger values without lengthening the run.

**How to tell your sweep was too narrow:** if the winner sits at the *largest* value you swept, the
real optimum is probably higher. Add another value and re-run.

### 4.3 Set `targetDatabaseEntries` to your real database size

```json
"targetDatabaseEntries": 132288304,
```

State the size of the database you **intend to scan against**, not one you happen to have. The tuner
builds a synthetic filter of exactly that size — the filter's contents do not affect timing, but its
size does (GPU memory occupancy and how many candidates cross the PCIe bus).

The value is a count of addresses. Two reference points: the Light DB tier is `132288304`, the Full
DB tier is `1377000000`. If your database is already imported, the address count is printed at
startup by any `Find` run (`Binary Fuse16 filter: ready (140964881 addresses, …)`).

### 4.4 Point the consumer at your database (optional but recommended)

```json
"lmdbConfigurationReadOnly": {
  "lmdbDirectory": "lmdb",
  ...
}
```

This is used **only** to measure what one database lookup costs on your storage — the number the
`FUSE_8` vs `FUSE_16` recommendation hinges on, and one that ranges from 4 µs to 293 µs depending on
whether the data is in the page cache. The grid sweep itself never touches the database.

Leave the directory pointing at a real LMDB if you have one. If you do not, the tuner still runs and
falls back to a documented estimate — it says so in the report.

### 4.5 Leave these alone

| Setting | Default | Why |
|---|---|---|
| `secondsPerArm` | `20` | Below ~5 s the ranking of neighbouring arms stops being reproducible. |
| `warmupSecondsPerArm` | `5` | Covers kernel compilation and GPU clock ramp-up, which would otherwise drag the first arm down. |
| `sweepFilterTypes` | `false` | Setting it `true` forces a second full filter build (~150 s at the Light tier, ~26 min at the Full tier) to measure something that is derived accurately anyway. |

---

## 5. Step 3 — run the tuner and capture the log

The report is printed to the log, **not written to a file**. Capture it, or you will watch a
20-minute measurement scroll out of your terminal buffer.

**Windows** — open `run_TuneConfiguration.bat` in a text editor. Two edits:

1. Change the config filename on the last line to your file:
   ```bat
   config_TuneConfiguration_gpu0.json
   ```
2. Uncomment the redirect on the final line by deleting the leading `rem `:
   ```bat
   >> log_TuneConfiguration_gpu0.txt 2>&1
   ```

Then run it:

```bat
run_TuneConfiguration.bat
```

**Linux / macOS** — same two edits in `run_TuneConfiguration.sh` (delete the leading `# ` on the
redirect line), then:

```bash
chmod +x run_TuneConfiguration.sh
./run_TuneConfiguration.sh
```

Or redirect without editing the script:

```bash
./run_TuneConfiguration.sh > log_TuneConfiguration_gpu0.txt 2>&1
```

`2>&1` means "send error output to the same file as normal output" — without it, errors go to the
screen and are lost from the log.

### How long it takes

```
run time = number of arms × (warmupSecondsPerArm + secondsPerArm)
```

With 7 batch sizes × 6 `keysPerWorkItem` values = 42 arms × 25 s ≈ **18 minutes**, plus a one-time
filter build of roughly 44 s per 100 M entries.

**Leave the machine otherwise idle.** The tuner measures throughput; anything else using the GPU,
the CPU or memory bandwidth during a run lands directly in the numbers.

### What files this creates

| File | Created by | Contents |
|---|---|---|
| `log_TuneConfiguration_gpu0.txt` | your redirect | The whole run, including the report. **This is the file to keep.** |
| *(nothing else)* | — | The tuner writes no files of its own. The recommended config is printed inside the log, not saved separately. |

---

## 6. Step 4 — read the report

Find the block delimited by `########## BEGIN TuneConfiguration report ##########`.

```
Arms (all figures MEASURED on this machine):
  batchSizeInBits  keysPerWorkItem        candidates/s addresses checked/s
  23               64                    64,588,860.65       1,006,766.88
  23               256                   94,791,059.02       1,473,710.16
  ...
Winner (MEASURED): batchSizeInBits=23 keysPerWorkItem=256 at 94,791,059.02 candidates/s.

Filter choice - total = probe + fpr x verification:
  verification cost   12.61 us   MEASURED on this database
  FUSE_8              probe 76.70 ns, fpr 0.003874   DOCUMENTED constants -> total 125.55 ns
  FUSE_16             probe 80.40 ns, fpr 0.000016   DOCUMENTED constants -> total 80.60 ns
  Recommended: FUSE_16   (DERIVED, not measured)

Paste-ready configuration:
{ ... }
```

Every figure is labelled **MEASURED** (on your machine, just now) or **DOCUMENTED / ESTIMATED** (a
published constant that does not vary by machine), so you always know which is which.

Three things to check before trusting the winner:

1. **Is the winner at the edge of your sweep?** If `keysPerWorkItem` won at the largest value you
   listed, widen the list and re-run — see [4.2](#42-widen-keysperworkitemcandidates--this-one-matters).
2. **Any `FAILED:` arms?** Those are combinations your driver rejected, usually because the output
   buffer for that `batchSizeInBits` exceeds the device's maximum allocation. Harmless — the sweep
   records them and moves on.
3. **Any arms reading `0.00 candidates/s`?** Not a failure: that arm is so slow that not one batch
   completed inside the 20 s measurement window. It happens at large `batchSizeInBits` combined with
   tiny `keysPerWorkItem`. Ignore those arms, or raise `secondsPerArm` if you specifically want a
   number for them.

> **In release 1.7.0 the report arrives as one very long line** with ` | ` between what should be
> line breaks. That is the log pattern's CRLF guard folding the block. To read it, paste it into an
> editor and replace ` | ` with a newline. Later releases emit the report one line per record and
> need no such treatment.

---

## 7. Step 5 — apply the result

The report ends with a **paste-ready configuration** — a complete `Find` config carrying the winning
values, with everything the sweep did not vary (device indices, consumer settings, key producer)
carried through unchanged. Copy it into your `config_Find.json` and run it.

If you prefer to edit your existing config by hand, copy exactly three values into your
`producerOpenCL` block:

```json
"batchSizeInBits": 23,
"keysPerWorkItem": 256,
"gpuFilterType": "FUSE_16"
```

---

## 8. Tuning a second GPU

**The sweep tunes one device: the first entry in `producerOpenCL`.** It ignores any further entries.
So with two GPUs, tune them separately:

1. Make a second config, e.g. `config_TuneConfiguration_gpu1.json`, whose **single** `producerOpenCL`
   entry carries the second device's `platformIndex` / `deviceIndex`.
2. Run it, capturing to `log_TuneConfiguration_gpu1.txt`.
3. Put each winner into the corresponding entry of your dual-GPU `Find` config.

Two things to know about running two GPUs together:

- **Each producer needs its own `keyProducerId`.** Key producers cannot be shared between producers.
- **A tuned-alone result may not hold when both run at once.** On a laptop with a discrete NVIDIA
  card and an integrated Intel GPU, the integrated GPU measured 16.3 M keys/s alone but delivered
  5.5 M keys/s with the discrete card running — a 3× loss. An integrated GPU has no memory of its
  own; it shares system RAM with the host, so a discrete GPU saturating memory bandwidth takes
  directly from it. A discrete card with its own VRAM does not suffer this. Tune alone, then check
  the real split in the `Keys per producer` field of the statistics line.

---

## 9. Contributing your numbers

Results are hardware-dependent, so the project keeps them **per machine** rather than averaging them
away. Two GPUs are currently registered, both high-end desktop parts — laptop, integrated and
mid-range hardware is exactly what is missing.

This part needs a git clone of the repository and Python 3 (standard library only, nothing to
install).

### 9.1 Register your machine

```bash
python docs/measurements/register_machine.py --set storage="Samsung 990 PRO 4TB NVMe"
```

This detects your CPU, cache sizes, RAM, GPU, OS and JDK and writes an entry into
[`docs/measurements/machines.json`](measurements/machines.json). It prints the `machine_id` it
generated, e.g. `ryzen75800h-63g-win11`. Re-running updates the same entry instead of adding a
duplicate.

Useful variants:

```bash
python docs/measurements/register_machine.py --dry-run        # show the entry, write nothing
python docs/measurements/register_machine.py --id my-laptop   # choose the id yourself
python docs/measurements/register_machine.py --set cpu.l3_mb=32   # fill in a field it missed
```

Anything it cannot detect is left `null`. **Check `cpu.l3_mb` in particular** — the filter-selection
plots annotate the L3 boundary from it, and detection fails on some platforms.

### 9.2 Send the numbers

Open an issue or a discussion and attach:

| What | Why it is needed |
|---|---|
| `log_TuneConfiguration_<gpu>.txt`, one per GPU | The arms table is the actual data; the log also records driver version, device properties and the exact settings used. |
| The `machine_id` and the `machines.json` entry `register_machine.py` printed | Every measurement row references a machine; without it a number cannot be interpreted. |
| Whether the sweep hit the edge (see [step 4](#6-step-4--read-the-report)) | A truncated sweep reports a lower bound, not a peak, and must be labelled as such. |

If you would rather open a pull request directly, append one row per arm to a
`docs/measurements/tuner_<machine_id>_<gpu>.csv` using the column layout of
[`tuner_ryzen9800x3d_gfx1100.csv`](measurements/tuner_ryzen9800x3d_gfx1100.csv):

```
machine_id,gpu,date,batch_size_in_bits,keys_per_work_item,candidates_per_second,addresses_checked_per_second,seconds_per_arm,warmup_seconds,verification_micros_measured,winner,kernel
```

Then regenerate the plots and the tables in [`performance.md`](performance.md):

```bash
python docs/measurements/plot.py
```

Never retype a number into prose — the CSVs are the single source of truth and the tables are
generated from them between `<!-- BEGIN GENERATED:… -->` markers.

---

## 10. Troubleshooting

**`OpenCLInfo` lists no devices.**
Install your vendor's OpenCL runtime — the GPU driver alone is sometimes not enough. On Linux,
install the ICD package (`nvidia-opencl-icd`, `intel-opencl-icd`, `mesa-opencl-icd`) and verify with
`clinfo -l`, which enumerates platforms and devices in the same order this tool does.

**`CL_OUT_OF_RESOURCES` during the run.**
The per-launch buffer for that `batchSizeInBits` is larger than the device allows. The sweep records
the arm as failed and continues, so this is not fatal during tuning. If it happens on every arm,
start from the `SUGGESTED START CONFIG` values that `OpenCLInfo` printed.

**`InaccessibleObjectException` at startup.**
You ran a bare `java -jar` with a database configured. Use the launcher script, which passes the
required `--add-opens` flags.

**Numbers differ noticeably between two runs of the same config.**
Something else was using the machine. Close other GPU workloads, then re-run. Laptops additionally
throttle on temperature — a run started on a cold machine and one started on a hot machine will not
agree.

**The winner changes every time I run it.**
If several arms are within a few percent of each other, any of them is a fine choice — pick the one
with the smaller `batchSizeInBits`, which reaches steady state faster and shuts down more responsively.
