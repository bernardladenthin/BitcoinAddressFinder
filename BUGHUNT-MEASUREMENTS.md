# Messauftrag zum Bug-Hunt 2026-10-16 … 19 — BitcoinAddressFinder

Dieser Auftrag sammelt jede Messung und jeden CI-Lauf, den der Bug-Hunt offen gelassen hat. Der
Hintergrund steht in `BUGHUNT-FOUND.md` (Fund-IDs) und `TODO.md`.

**Regeln für den ausführenden Agenten**

- **Nur messen und berichten.** Keine Produktionsänderung, keine Fixes. Wegwerf-Proben (Testklassen,
  Skripte) nach dem Lauf wieder entfernen; committet wird nur diese Datei mit den Ergebnissen.
- Arbeitsstand: Branch `ccr-90fc5f6a-vs4s0f` (nur dort gibt es diese Datei und die neuen Tests).
- Dateien aus `.github/shared-files.sha256` nicht verändern.
- Jeder Punkt wird beantwortet, auch wenn er nicht ausführbar war: dann mit Grund (z. B. „keine
  NVIDIA-GPU vorhanden“). Zu jedem Ergebnis gehören die Umgebung (Rechner, CPU/GPU, Treiber, OS, JDK)
  und die wörtlichen Ausgaben, die das Ergebnis tragen (Testzahlen, Logzeilen, Exit-Codes).
- Ergebnis jeweils unter **Ergebnis:** eintragen, darunter eine Zeile **Folgerung:** (was das
  Ergebnis für die genannte Entscheidung bedeutet).

**Gemeinsame Vorbereitung (Linux)**

```bash
mvn -B -q -DskipTests -Denforcer.skip=true compile
# pocl als CPU-OpenCL-Gerät, wie im CI-Job test-opencl:
sudo apt-get install -y pocl-opencl-icd ocl-icd-libopencl1 clinfo
export JSIG="$JAVA_HOME/lib/libjsig.so"   # Signalverkettung des JDK, F148
```

---

## Messumgebung (Ergebnisse vom 2026-10-19)

Gemessen 2026-10-19 auf Branch `ccr-90fc5f6a-vs4s0f` (`088d3c48`). Zwei Umgebungen:

- **Laptop (lokal, Windows):** Notebook, AMD Ryzen 7 5800H (8 Kerne / 16 Threads), 63,3 GB RAM, Windows 11 Pro
  10.0.26300, Temurin 21.0.12.1+1, Maven 3.9.11. Zwei OpenCL-Geräte:
  - `NVIDIA GeForce RTX 3070 Laptop GPU`, Treiber 581.83, `OpenCL 3.0 CUDA`, 40 CUs, 8191 MB global,
    `CL_DEVICE_MAX_MEM_ALLOC_SIZE` 2047 MB, max. Work-Group 1024 (Plattform 0)
  - `gfx90c` (Radeon-Vega-iGPU des 5800H), Treiber `3444.0 (PAL,HSAIL)`, `OpenCL 2.0 AMD-APP (3444.0)`, 8 CUs,
    24643 MB global (geteilter RAM), max. Allokation 20511 MB, max. Work-Group 256 (Plattform 1). Für die
    parametrisierten Tests (die immer Plattform 0 nehmen) wurde die AMD-ICD direkt geladen:
    `-Dorg.lwjgl.opencl.libname=C:\Windows\System32\DriverStore\FileRepository\u0382222.inf_amd64_de7bd56cf45c3a15\B380779\amdocl64.dll`
    (dann ist die iGPU die einzige Plattform; keine Code-Änderung).
  - Hinweis zu Oracle JDK 21.0.9: `mvn compile` bricht dort in NullAway ab (`-XDaddTypeAnnotationsToSymbol=true`
    „not supported by the running JDK“), deshalb Temurin.
- **Vultr-VM (Linux, pocl):** `voc-c-16c-32gb-300s-amd` (Frankfurt, dedizierte Kerne), AMD EPYC-Milan, 16 vCPU,
  31 GB RAM, Ubuntu 24.04.5 LTS, Kernel 6.8.0-146-generic, Temurin 21.0.12.1+1, Maven 3.9.11,
  `pocl-opencl-icd 5.0-2.1build3` (`OpenCL 3.0 PoCL 5.0+debian … LLVM 16.0.6`), `ocl-icd-libopencl1 2.3.2-1build1`.
  pocl-Gerät `cpu-haswell-AMD EPYC-Milan Processor`: 16 CUs, max. Work-Group 4096, 29,33 GiB global,
  max. Allokation 8 GiB. `JSIG=/opt/jdk-21.0.12.1+1/lib/libjsig.so`.

---

## B1 Vollständiger Testlauf ohne und mit OpenCL

**Ziel.** Der Bug-Hunt hat nur die betroffenen Testklassen laufen lassen. Ein voller Lauf in beiden
Formen, wie die CI-Jobs `test` und `test-opencl` sie fahren.

```bash
# 1. ohne OpenCL-Gerät (wie Job "test"): pocl vorher deinstallieren oder OCL_ICD_VENDORS auf ein leeres Verzeichnis zeigen lassen
OCL_ICD_VENDORS=/nonexistent mvn -B test
# 2. mit pocl (wie Job "test-opencl", dort ohne POCL_MAX_WORK_GROUP_SIZE):
LD_PRELOAD="$JSIG" mvn -B -e -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true test jacoco:report
```

**Hypothese.** Beide Läufe grün bis auf B2/B3.
**Entscheidet.** Ob der Branch so in die gemeinsame CI kann.

**Ergebnis:** (Vultr-VM, Befehle wörtlich aus dem Brief; `OCL_ICD_VENDORS` zeigt auf ein leeres Verzeichnis)

1. ohne OpenCL — `OCL_ICD_VENDORS=/tmp/emptyvendors mvn -B test`: **grün**
   ```
   [INFO] Tests run: 2904, Failures: 0, Errors: 0, Skipped: 107
   [INFO] BUILD SUCCESS
   [INFO] Total time:  06:53 min
   ```
2. mit pocl — `LD_PRELOAD="$JSIG" mvn -B -e -Dnet.ladenthin.bitcoinaddressfinder.disableLMDBTest=true test jacoco:report`
   (ohne `POCL_MAX_WORK_GROUP_SIZE`): **rot, ausschließlich durch `MainTest`**
   ```
   [INFO] Tests run: 2885, Failures: 0, Errors: 0, Skipped: 61
   [ERROR] Process Exit Code: 139
   [ERROR] Crashed tests:
   net.ladenthin.bitcoinaddressfinder.MainTest
   [INFO] BUILD FAILURE
   [INFO] Total time:  07:34 min
   ```
   Alle übrigen 2885 Tests: 0 Failures, 0 Errors. Die abgestürzte Fork ist die aus B2.

**Folgerung:** Der Branch ist bis auf den bekannten `MainTest`-Absturz auf pocl (B2) grün; ohne OpenCL ist er
vollständig grün. In die gemeinsame CI kann er erst, wenn B2 entschieden ist (Variable im Job oder
Local-Work-Size in BAF) — sonst wird `test-opencl` auf jedem Runner, auf dem pocl eine große Work-Group wählt,
rot. B3 hat auf dieser Maschine nicht gestört (s. dort).

---

## B2 `MainTest` auf pocl ohne `POCL_MAX_WORK_GROUP_SIZE`

**Ziel.** Lokal stirbt die Fork von `MainTest#testRoundtripOpenCLProducer_configurationsGiven_lmdbCreatedAndRunFindOpenCLDevice`
mit SIGSEGV (Exit 139, kein `hs_err`), wenn `POCL_MAX_WORK_GROUP_SIZE=256` fehlt. Ursache laut
`BUGHUNT-CHECKED.md` (Eintrag `OpenCLContext.java` + `generateKeysKernel_grid`, 2026-10-18): BAF übergibt
keine Local-Work-Size, pocl wählt 4096 und läuft in die Guard-Page seines 8-MB-Thread-Stacks. Der CI-Job
`test-opencl` setzt die Variable nicht.

```bash
for v in "" 256; do
  echo "POCL_MAX_WORK_GROUP_SIZE=$v"
  POCL_MAX_WORK_GROUP_SIZE=$v LD_PRELOAD="$JSIG" mvn -B -q test -Djacoco.skip=true \
    -Dtest='MainTest#testRoundtripOpenCLProducer*' 2>&1 | grep -E "Tests run|Exit Code"
done
clinfo | grep -iE "max work group size|max memory allocation|global memory size|compute units"
```

Zusätzlich, falls möglich: den echten CI-Job auslösen (`publish.yml`, `workflow_dispatch` auf dem
Branch) und das Ergebnis von `test-opencl` mit Run-ID eintragen.

**Hypothese.** Ohne die Variable rot (Exit 139), mit ihr grün; im CI hängt es vom Runner ab.
**Entscheidet.** Ob der Job `test-opencl` `POCL_MAX_WORK_GROUP_SIZE` setzen muss (oder BAF eine
Local-Work-Size vorgeben sollte).

**Ergebnis:** (Vultr-VM; pocl wählt hier max. Work-Group **4096**)

Achtung, der Befehl im Brief misst den ungesetzten Fall nicht: `POCL_MAX_WORK_GROUP_SIZE=$v` mit leerem `$v`
setzt die Variable auf einen **leeren String**. pocl bricht dann in seiner eigenen Assertion ab, Exit **134**:
```
java: ./lib/CL/devices/common.c:1480: pocl_init_default_device_infos: Assertion `max_wg > 0' failed.
[ERROR] Process Exit Code: 134
```
Deshalb zusätzlich mit wirklich ungesetzter Variable (`env -u POCL_MAX_WORK_GROUP_SIZE …`), je 5 Läufe,
`LD_PRELOAD="$JSIG" mvn -B test -Djacoco.skip=true -Dtest='MainTest#testRoundtripOpenCLProducer*'`:
```
unset run 1 mvn=1 Process Exit Code: 139 Tests run: 0, Failures: 0, Errors: 0 hs_err=0
256   run 1 mvn=0  Tests run: 1, Failures: 0, Errors: 0
unset run 2 mvn=1 Process Exit Code: 134 Tests run: 0, Failures: 0, Errors: 0 hs_err=1
256   run 2 mvn=0  Tests run: 1, Failures: 0, Errors: 0
unset run 3 mvn=1 Process Exit Code: 139 Tests run: 0, Failures: 0, Errors: 0 hs_err=1
256   run 3 mvn=0  Tests run: 1, Failures: 0, Errors: 0
unset run 4 mvn=1 Process Exit Code: 139 Tests run: 0, Failures: 0, Errors: 0 hs_err=1
256   run 4 mvn=0  Tests run: 1, Failures: 0, Errors: 0
unset run 5 mvn=1 Process Exit Code: 139 Tests run: 0, Failures: 0, Errors: 0 hs_err=1
256   run 5 mvn=0  Tests run: 1, Failures: 0, Errors: 0
```
Ungesetzt **5/5 rot** (4× 139 ohne `hs_err`, 1× 134 mit `hs_err`), mit `256` **5/5 grün**. Der eine `hs_err`
(`hs_err_pid30260.log`) zeigt die Ursache aus `BUGHUNT-CHECKED.md` direkt — ein pocl-Worker-Thread, der im
Kernel unter seinen Stack läuft:
```
#  SIGSEGV (0xb) at pc=0x000075cbcca3dcab, pid=30260, tid=30307
# Problematic frame:
# C  [generateKeysKernel_grid.so+0x3cab]  add+0x2b
Current thread is native thread
Stack: [0x000075cb3c7fc000,0x000075cb3cffc000],  sp=0x000075cb3c5ea730,  free space=18014398509479865k
C  [generateKeysKernel_grid.so+0x3cab]  add+0x2b
C  [generateKeysKernel_grid.so+0x4200]  mul_mod+0x150
C  [generateKeysKernel_grid.so+0x4a4c]  point_mul_xy_comb+0x2dc
```
(`sp` liegt 2,1 MB unterhalb der Stack-Untergrenze → Guard-Page.) Ob 134 oder 139 entscheidet nur, ob der
JVM-Handler den Fehler noch zu fassen bekommt.

`clinfo`:
```
  Max compute units                               16
  Max work group size                             4096
  Global memory size                              31497936896 (29.33GiB)
  Max memory allocation                           8589934592 (8GiB)
```
Ein echter CI-Lauf wurde nicht ausgelöst (Entscheidung des Auftraggebers: nur lokal reproduzieren) — keine Run-ID.

**Folgerung:** Ohne Begrenzung stirbt `MainTest` auf pocl deterministisch (5/5), mit `256` nie (5/5). Der Job
`test-opencl` muss `POCL_MAX_WORK_GROUP_SIZE=256` setzen, oder BAF gibt eine Local-Work-Size vor (das
behebt die Ursache für jede Implementierung, die bei fehlender Angabe die maximale Gruppe wählt). Ob der
GitHub-Runner (4 vCPU) ebenfalls 4096 meldet, ist nicht gemessen; der Mechanismus hängt an der gewählten
Gruppengröße, nicht an der Kernzahl. Nebenbefund: die Schleife im Brief (`for v in "" 256`) prüft den
ungesetzten Fall nicht — wer sie wiederverwendet, sieht die pocl-Assertion (134) statt des Stack-Überlaufs.

---

## B3 `ProbeAddressesOpenCLTest#setSrcPrivateKeyChunk_privateKeyTooLarge_throwsException`

**Ziel.** Beide Fälle brachen lokal mit `OpenClCallFailedException: clCreateBuffer failed:
CL_INVALID_BUFFER_SIZE` in `openCLContext.init()` ab, bevor die eigentliche Prüfung lief: der Test baut den
Kontext mit `batchSizeInBits` 24, pocl erlaubte auf 4 Kernen/15 GB nur 1024 MB je Allokation.

```bash
LD_PRELOAD="$JSIG" POCL_MAX_WORK_GROUP_SIZE=256 mvn -B -q test -Djacoco.skip=true \
  -Dtest='ProbeAddressesOpenCLTest#setSrcPrivateKeyChunk_privateKeyTooLarge*' 2>&1 | grep -E "Tests run|Exception"
clinfo | grep -i "max memory allocation"
```

Auf dem CI-Runner (falls der Job läuft) und auf einer echten GPU wiederholen.

**Hypothese.** Rot überall dort, wo `CL_DEVICE_MAX_MEM_ALLOC_SIZE` unter dem Bedarf eines 2^24-Rasters liegt.
**Entscheidet.** Ob der Test ein kleineres Raster bekommt (Vorschlag in `TODO.md`).

**Ergebnis:** | Gerät | `CL_DEVICE_MAX_MEM_ALLOC_SIZE` | Ergebnis |
|---|---|---|
| pocl, Vultr-VM (31 GB RAM) | 8192 MB | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` |
| RTX 3070 Laptop (Windows) | 2047 MB | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` |
| gfx90c iGPU (Windows) | 20511 MB | `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0` |

Befehl auf der VM wörtlich aus dem Brief (`LD_PRELOAD="$JSIG" POCL_MAX_WORK_GROUP_SIZE=256 mvn -B -q test …`),
lokal derselbe `-Dtest` ohne die Linux-Variablen. Kein `CL_INVALID_BUFFER_SIZE` auf einem der drei Geräte.

Gegenprobe auf derselben VM, pocl künstlich auf den Fall des Briefs begrenzt (`POCL_MEMORY_LIMIT=<GiB>`, sonst
gleicher Befehl):
```
== POCL_MEMORY_LIMIT=4
  Global memory size                              4294967296 (4GiB)
  Max memory allocation                           1073741824 (1024MiB)
[ERROR]   ProbeAddressesOpenCLTest.setSrcPrivateKeyChunk_privateKeyTooLarge_throwsException:360 » OpenClCallFailed OpenCL call clCreateBuffer failed: CL_INVALID_BUFFER_SIZE (-61)
[ERROR] Tests run: 2, Failures: 0, Errors: 2, Skipped: 0
== POCL_MEMORY_LIMIT=2
  Max memory allocation                           536870912 (512MiB)
[ERROR] Tests run: 2, Failures: 0, Errors: 2, Skipped: 0   (gleiche Ausnahme)
```
Ein CI-Lauf wurde nicht ausgelöst (nur lokal, Entscheidung des Auftraggebers).

**Folgerung:** Der Test ist an die Allokationsgrenze des Geräts gebunden: bei 1024 MiB (der Wert des Briefs,
4 Kerne/15 GB) scheitert er reproduzierbar mit genau `CL_INVALID_BUFFER_SIZE` in `clCreateBuffer`, ab 2047 MB
(RTX 3070) läuft er. Auf einem CI-Runner mit ähnlich wenig RAM wie im Brief (15 GB) ist dasselbe zu erwarten
(dort nicht gemessen). Ein kleineres Raster (Vorschlag in `TODO.md`) macht ihn hardwareunabhängig und ändert an
der geprüften Aussage nichts.

---

## B4 Die neuen OpenCL-Tests auf einer echten GPU

**Ziel.** Die neuen OpenCL-Tests liefen nur auf pocl (CPU). Auf einer echten GPU wiederholen, beide
Feld-Implementierungen sind in den Tests parametrisiert.

```bash
LD_PRELOAD="$JSIG" mvn -B -q test -Djacoco.skip=true -Dtest='OpenCLZeroAnchorTest,ProbeAddressesOpenCLTest#setSrcPrivateKeyChunk_gridWouldReachTheGroupOrder*,TuneConfigurationTest#run_noArmSucceedsOnOneDevice*,ProducerOpenCLTest,OpenCLContextTest'
```

Zuerst mit `{"command":"OpenCLInfo"}` Plattform und Gerät bestimmen; ein Multi-GPU-Rechner sollte die
Tests auf jedem Gerät laufen lassen (Plattform/Gerät über die Testkonfiguration).

**Hypothese.** Grün wie auf pocl.
**Entscheidet.** Ob F129 (Anker 0), F151 (oberstes Raster) und F154 (Tuner-Gerät ohne Gewinner) auf
echter Hardware bestätigt sind.

**Ergebnis:** Befehl aus dem Brief (unter Windows ohne `LD_PRELOAD`), je einmal pro Gerät:

RTX 3070 Laptop (NVIDIA, Plattform 0) — Kernel inlined:
```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 52.40 s -- in net.ladenthin.bitcoinaddressfinder.command.TuneConfigurationTest
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 93.17 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContextTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 49.11 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLZeroAnchorTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.221 s -- in net.ladenthin.bitcoinaddressfinder.ProbeAddressesOpenCLTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.68 s -- in net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCLTest
Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
```
gfx90c (AMD-iGPU, über die AMD-ICD direkt) — Kernel automatisch out-of-line:
```
noInlineHelpers -> OUT-OF-LINE kernel (-D FORCE_NO_INLINE) on device 'gfx90c' (vendor 'Advanced Micro Devices, Inc.', source: auto)
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 8.727 s -- in net.ladenthin.bitcoinaddressfinder.command.TuneConfigurationTest
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 33.26 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContextTest
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 14.20 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLZeroAnchorTest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 9.117 s -- in net.ladenthin.bitcoinaddressfinder.ProbeAddressesOpenCLTest
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 57.67 s -- in net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCLTest
Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
```
`OpenCLInfo` vorher: siehe Umgebung oben. (`TuneConfigurationTest#run_noArmSucceedsOnOneDevice*` loggt wie
vorgesehen `Invalid device index: 99` für das absichtlich ungültige Gerät.)

Dazu unter **Linux**: RTX 3080 (Vast.ai, Treiber 580.126.18, Umgebung wie in B7), Testsatz des Briefs plus
`ProbeAddressesOpenCLTest#setSrcPrivateKeyChunk_privateKeyTooLarge*`, mit `LD_PRELOAD="$JSIG"`:
```
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 41.90 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLZeroAnchorTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 13.74 s -- in net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCLTest
[INFO] Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
Dazu die **RX 7900 XTX** (`gfx1100`, `3679.0 (PAL,LC)`, Windows; Umgebung wie in B6), Befehl des Briefs, Kernel
automatisch out-of-line (`noInlineHelpers -> OUT-OF-LINE kernel (-D FORCE_NO_INLINE) on device 'gfx1100' … source: auto`):
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.874 s -- in net.ladenthin.bitcoinaddressfinder.command.TuneConfigurationTest
[INFO] Tests run: 32, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.477 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContextTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.729 s -- in net.ladenthin.bitcoinaddressfinder.opencl.OpenCLZeroAnchorTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.782 s -- in net.ladenthin.bitcoinaddressfinder.ProbeAddressesOpenCLTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.59 s -- in net.ladenthin.bitcoinaddressfinder.producer.ProducerOpenCLTest
[INFO] Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
Ein Multi-GPU-Rechner stand nicht zur Verfügung; die beiden lokalen Geräte gehören zu getrennten Plattformen
und wurden einzeln getestet (der Tuner-Lauf mit beiden zugleich steht unter B5).

**Folgerung:** F129 (Anker 0, `OpenCLZeroAnchorTest`, beide Feld-Implementierungen), F151 (oberstes Raster)
und F154 (Tuner-Gerät ohne Gewinner) sind auf vier echten GPUs bestätigt — NVIDIA unter Windows (47/47) und
Linux (49/49), AMD-dGPU RDNA3 (47/47) und AMD-iGPU (47/47), beide PAL/OpenCL 2.0 unter Windows, sowie AMD
Instinct MI325X (CDNA3, `gfx942`) unter Linux mit ROCm 7.14 (49/49, Umgebung wie in B7; Testsatz wie bei der
RTX 3080, Kernel automatisch out-of-line):
```
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.241 s -- in net.ladenthin.bitcoinaddressfinder.ProbeAddressesOpenCLTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.744 s -- in net.ladenthin.bitcoinaddressfinder.command.TuneConfigurationTest
[INFO] Tests run: 49, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## B5 F136: `sweepFilterTypes` mit zwei echten OpenCL-Geräten

**Ziel.** F136 (der Tuner misst die Filtertypen auf dem ersten Gerät mit dessen eigenem Gewinner) ließ sich
nur mit einem Gerät prüfen. Mit zwei Geräten (z. B. iGPU + dGPU, oder pocl + GPU):

Ausgangspunkt `examples/config_TuneConfiguration.json`, dort `tuneConfiguration.sweepFilterTypes` auf
`true` setzen und die `producerOpenCL`-Liste leer lassen (dann werden alle erkannten GPUs gemessen) oder
zwei Geräte explizit eintragen.

Im Bericht prüfen: der Filter-Sweep des ersten Geräts nutzt dessen eigenes Raster, und die ausgegebene
Konfiguration trägt für jedes Gerät seine eigenen Werte.

**Ergebnis:** Laptop (Windows), RTX 3070 + gfx90c als zwei echte Geräte zweier Plattformen.
`examples/config_TuneConfiguration.json` mit `sweepFilterTypes: true`, leerer `producerOpenCL`-Liste und — um die
Laufzeit klein zu halten — verkleinertem Raster: `batchSizeInBitsCandidates [18,20]`,
`keysPerWorkItemCandidates [1,16]`, `secondsPerArm 5`, `warmupSecondsPerArm 2`, `targetDatabaseEntries 10000000`
(ohne LMDB: synthetischer Filter, Verifikationskosten als ESTIMATED markiert). `java -Xmx8g … cli.Main`, Exit 0.
```
No producer configured; sweeping every detected GPU instead. 2 found: NVIDIA GeForce RTX 3070 Laptop GPU (platformIndex=0, deviceIndex=0, identity uuid:36e7dc7a8961da6c2338c7593e5d9375); gfx90c (platformIndex=1, deviceIndex=0, identity amd-pcie:7:0:0).
Sweeping 2 devices in turn, 4 arms each — roughly 1 minutes in total.
...
Winner per device (MEASURED):
  GPU platformIndex=0 deviceIndex=0: batchSizeInBits=20 keysPerWorkItem=128 at 214,371,255.75 candidates/s
  GPU platformIndex=1 deviceIndex=0: batchSizeInBits=20 keysPerWorkItem=256 at 6,693,605.65 candidates/s
Rebuilding the pre-filter as FUSE_8 to measure it empirically (this is the opt-in cost of sweepFilterTypes) ...
Filter arm FUSE_8: batchSizeInBits=20 keysPerWorkItem=128 -> 215,829,567.84 candidates/s (3,365,619.31 addresses checked/s over 5.00 s)
Rebuilding the pre-filter as FUSE_16 to measure it empirically (this is the opt-in cost of sweepFilterTypes) ...
Filter arm FUSE_16: batchSizeInBits=20 keysPerWorkItem=128 -> 220,970,676.20 candidates/s (13,589.55 addresses checked/s over 5.01 s)
  Recommended: FUSE_16   (MEASURED empirically)
```
Ausgegebene Konfiguration (`producerOpenCL`, gekürzt):
```
{ "keyProducerId" : "exampleKeyProducerSecureRandomId",   "batchSizeInBits" : 20, "platformIndex" : 0, "deviceIndex" : 0, "keysPerWorkItem" : 128, "enableGpuFilter" : true, "gpuFilterType" : "FUSE_16", ... },
{ "keyProducerId" : "exampleKeyProducerSecureRandomId_2", "batchSizeInBits" : 20, "platformIndex" : 1, "deviceIndex" : 0, "keysPerWorkItem" : 256, "enableGpuFilter" : true, "gpuFilterType" : "FUSE_16", ... }
```
Nebenbefund: auf der iGPU stoppte die Kanten-Erweiterung bei `maxExtensionArms`
(„The winner may still be a lower bound“), auf der RTX 3070 lag das Optimum innerhalb (der Arm 20/256 war mit
152,9 M langsamer als 20/128 mit 214,4 M candidates/s).

**Folgerung:** F136 ist mit zwei echten Geräten bestätigt: Der Filter-Sweep läuft auf dem ersten Gerät mit
**dessen eigenem** Gewinner (20/128, nicht dem der iGPU 20/256), und die ausgegebene Konfiguration trägt für
jedes Gerät seine eigenen Werte (Plattform, `keysPerWorkItem`); der Filtertyp ist eine gemeinsame Wahl.

---

## B6 Heap-Rückgewinnung vor dem GPU-Filter-Upload

Verfahren, Hintergrund und Erwartung: `TODO.md`, „Re-measure the heap reclaim before the GPU filter
upload on the chunked LWJGL upload“. Braucht eine GPU mit genügend Speicher für den Full-DB-Filter
(gemessen wurde ursprünglich auf einer RX 7900 XTX).

**Entscheidet.** Ob `OpenCLContext.reclaimHeapBeforeUpload()` (`GcTrigger`) mit dem stückweisen
LWJGL-Upload noch nötig ist.

**Ergebnis:** Gemessen auf einer privaten Maschine derselben Konfiguration wie die Originalzeile in
`docs/measurements/gpu_filter_probe.csv` (`ryzen79800x3d8core-61g-win11`, gfx1100):

| | |
|---|---|
| CPU / RAM | AMD Ryzen 7 9800X3D (8 Kerne), 64 GB DDR5 verbaut, 61,61 GiB nutzbar |
| GPU | AMD Radeon RX 7900 XTX (`gfx1100`), Adrenalin 26.6.4, Anzeigetreiber 32.0.31021.5001 |
| OpenCL | `CL_DRIVER_VERSION: 3679.0 (PAL,LC)`, `OpenCL 2.0 AMD-APP (3679.0)`, 48 CUs, `CL_DEVICE_MAX_MEM_ALLOC_SIZE: 20876 MByte`, `CL_DEVICE_GLOBAL_MEM_SIZE: 24560 MByte`; erste Plattform, Gerät 0 (die iGPU `gfx1036` ist Gerät 1) |
| OS / JDK | Windows 11 Pro 26H2, Build 26300.9711; Temurin 21.0.12.1+1; Maven 3.9.16 |
| Stand | Branch `ccr-90fc5f6a-vs4s0f`, `a8752011` (gegenüber `088d3c48` nur diese Datei geändert) |

Verfahren wie `GpuFilterProbeBenchmark` (FUSE16, synthetische 1.377.478.516 Einträge aus `PrngAddressIterable`,
`probeCount` 4194304 — wie die Originalmessung, keine Datenbank), in einer Wegwerf-JMH-Klasse im Paket `opencl`
mit dem `GcTrigger` als Parameter: `NOOP` (keine Collection vor dem Upload) bzw. `SYSTEM` (`System.gc()` mit
Zeitmessung). Je ein Lauf `-f 1 -wi 1 -w 5 -i 3 -r 5`, `-jvmArgsAppend "-Xmx48g …"`, NOOP zuerst. Puffer:
1.549.795.328 Fingerprints × 2 B = **3.099.590.656 B (3,10 GB)**. (Die Probe gab die GB-Zahl mit einem
`int`-Überlauf negativ aus, `-1,195 GB`; die Rohzahl ist richtig.)

Lauf (a) `gcMode=NOOP`:
```
B6| filter built in 321 s, fingerprints=1549795328 (-1,195 GB), heap used=40,9 GiB committed=41,1 GiB max=48,0 GiB
B6| GcTrigger NOOP called, heap used=40,9 GiB committed=41,1 GiB max=48,0 GiB
B6| UPLOAD OK in 490 ms (gcMode=NOOP)
Benchmark                (entries)  (gcMode)  (probeCount)  Mode  Cnt  Score   Error  Units
B6GcTriggerProbe.probe  1377478516      NOOP       4194304  avgt    3  2,253 ± 0,026  ms/op
```
Lauf (b) `gcMode=SYSTEM`:
```
B6| filter built in 321 s, fingerprints=1549795328 (-1,195 GB), heap used=40,9 GiB committed=41,1 GiB max=48,0 GiB
B6| before System.gc(): heap used=40,9 GiB committed=41,1 GiB max=48,0 GiB
B6| System.gc() took 83 ms; after: heap used=3,0 GiB committed=10,3 GiB max=48,0 GiB
B6| UPLOAD OK in 585 ms (gcMode=SYSTEM)
Benchmark                (entries)  (gcMode)  (probeCount)  Mode  Cnt  Score   Error  Units
B6GcTriggerProbe.probe  1377478516    SYSTEM       4194304  avgt    3  2,252 ± 0,052  ms/op
```
Beide Läufe Exit 0, keine Exception (kein `CL_MEM_OBJECT_ALLOCATION_FAILURE`, kein `OutOfMemoryError`).

Probe (nicht committet; nur der für B6 wesentliche Teil):
```java
package net.ladenthin.bitcoinaddressfinder.opencl;
@BenchmarkMode(Mode.AverageTime) @OutputTimeUnit(TimeUnit.MILLISECONDS) @State(Scope.Benchmark)
public class B6GcTriggerProbe {
    @Param({"NOOP", "SYSTEM"}) public String gcMode;
    @Param({"1377478516"}) public long entries;
    @Param({"4194304"}) public int probeCount;
    @Setup(Level.Trial) public void setUp() throws Exception {
        CProducerOpenCL config = new CProducerOpenCL(); config.batchSizeInBits = 8;
        context = new OpenCLContext(config, new BitHelper()); context.init();
        context.setGcTrigger(gcMode.equals("NOOP") ? () -> log("GcTrigger NOOP called")
                : () -> { long t0 = System.nanoTime(); System.gc(); log("System.gc() took " + ms(t0)); });
        // probe keys and the FUSE16 filter exactly as in GpuFilterProbeBenchmark (MEMBER_SEED 0xC0FFEE,
        // PROBE_SEED 0x5EED_1234_ABCD), then:
        context.prepareBenchFilterProbeFuse16(data.fingerprints(), meta, probeKeys);   // calls reclaimHeapBeforeUpload()
    }
    @Benchmark public int probe() { return context.runBenchFilterProbe(); }
}
```

**Folgerung:** Mit dem stückweisen LWJGL-Upload ist die Collection nicht mehr nötig: ohne `System.gc()` wurde der
3,10-GB-Fuse-16-Puffer bei **40,9 GiB lebendigem Heap** angelegt und gefüllt (490 ms) — mehr Heap-Druck als beim
ursprünglichen Fehlschlag (33,9 GiB, `CL_MEM_COPY_HOST_PTR`), auf demselben OpenCL-Treiberstand `3679.0 (PAL,LC)`.
Der Unterschied liegt damit im Upload-Pfad, nicht im Treiber (ein Gegenlauf mit dem alten JOCL-Pfad wurde nicht
gemacht). Die Collection kostet einmalig **83 ms** (40,9 → 3,0 GiB) und ändert den Probe-Durchsatz nicht
(2,253 vs. 2,252 ms/op). `reclaimHeapBeforeUpload()`/`GcTrigger` können entfernt werden; wer den
Sicherheitsgurt für andere Treiber behalten will, zahlt dafür ~0,1 s beim Start — das gehört dann als
gemessener Grund und Preis in die `GcTrigger`-Javadoc.

---

## B7 Ersetzen GPU-OpenCL-Treiber die Signal-Handler der JVM?

Hintergrund: F148 und `TODO.md`, „Check whether GPU OpenCL drivers replace the JVM's signal handlers
(pocl does)“. Auf pocl gemessen: nach `OpenCLContext.init()` 5/5 Abstürze (Exit 139, kein `hs_err`),
ohne Kernel-Bau 0/5, mit `libjsig` 0/5. Zu messen auf NVIDIA, AMD und Intel, jeweils:

```bash
# Probe (Quelltext unten) nach /tmp/sigprobe/SigProbe3.java legen, dann:
CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout -DincludeScope=runtime)"
OPENS="--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
javac -cp "$CP" -d /tmp/sigprobe /tmp/sigprobe/SigProbe3.java
for mode in nocl cl; do for i in 1 2 3 4 5; do
  java $OPENS -cp "/tmp/sigprobe:$CP" SigProbe3 $mode 2000; echo "$mode run $i exit=$?"; done; done
# und einmal mit Signalverkettung:
for i in 1 2 3 4 5; do LD_PRELOAD="$JSIG" java $OPENS -cp "/tmp/sigprobe:$CP" SigProbe3 cl 2000; echo "jsig run $i exit=$?"; done
# Handler direkt sichtbar machen:
java -Xcheck:jni $OPENS -cp "/tmp/sigprobe:$CP" SigProbe3 cl 10 2>&1 | grep -i "handler"
```

```java
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import java.util.concurrent.atomic.AtomicBoolean;
public class SigProbe3 {
  static volatile long sink;
  public static void main(String[] a) throws Exception {
    CProducerOpenCL c = new CProducerOpenCL(); c.batchSizeInBits = 8; c.keysPerWorkItem = 16;
    OpenCLContext ctx = new OpenCLContext(c, new BitHelper());
    if (a[0].equals("cl")) ctx.init();
    AtomicBoolean run = new AtomicBoolean(true);
    Thread[] ts = new Thread[8];
    for (int t = 0; t < ts.length; t++) {
      ts[t] = new Thread(() -> { long x = 0; while (run.get()) { for (int i = 0; i < 1_000_000; i++) x += i ^ (x >>> 3); sink = x; } });
      ts[t].start();
    }
    Thread.sleep(2000);                       // let the loops get compiled
    for (int i = 0; i < Integer.parseInt(a[1]); i++) { System.gc(); Thread.getAllStackTraces(); }
    run.set(false); for (Thread t : ts) t.join();
    System.out.println("survived");
    if (a[0].equals("cl")) ctx.close();
  }
}
```

**Entscheidet.** Ob die Warnung von `SignalChainingCheck` und der `LD_PRELOAD`-Block in den
`run_*OpenCL*.sh`-Skripten nur pocl betreffen oder allgemein nötig sind (README „Known Issues“ entsprechend).

**Ergebnis:** **NVIDIA (Linux) — gemessen.** Vast.ai-Container, `NVIDIA GeForce RTX 3080` (USA), Treiber 580.126.18
(`OpenCL 3.0 CUDA 13.0.97`, ICD `libnvidia-opencl.so.1`), Leistungsgrenze 320 W = Standard, Host-Kernel
`6.8.0-101-generic`, Container `eclipse-temurin:21-jdk` (Ubuntu 26.04.1), 10 vCPU, 251 GB RAM, Temurin 21.0.12.1+1,
Branch `088d3c48`. Probe `SigProbe3` wörtlich aus dem Brief, Befehle wie im Brief (Exit-Code, Zahl der
`survived`-Zeilen, Zahl der `hs_err`-Dateien):
```
### -Xcheck:jni handler check (cl, 10)
... WARN  net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext - The JDK's signal chaining (libjsig) is not preloaded. An OpenCL driver that compiles kernels in-process with LLVM (measured: pocl) replaces the JVM's SIGSEGV handler, ...
survived
### probe runs
nocl run 1 exit=0 1 hs_err=0   (… run 2–5 identisch)
cl run 1 exit=0 1 hs_err=0     (… run 2–5 identisch)
jsig run 1 exit=0 1            (… run 2–5 identisch)
```
`-Xcheck:jni` (`grep -i handler`) meldet **kein** `SIGSEGV handler modified!` — die einzige Treffer-Zeile ist BAFs
eigene `SignalChainingCheck`-Warnung. Nocl 0/5, cl 0/5, jsig 0/5 Abstürze (pocl zum Vergleich laut `TODO.md`:
cl 5/5).

**AMD (Linux) — gemessen.** DigitalOcean GPU-Droplet `gpu-mi325x1-256gb` (`tor1`, Image `gpu-amd-base`):
1× AMD Instinct **MI325X** (`gfx942:sramecc+:xnack-`, 304 CUs, 256 GB HBM), KVM-Gast auf AMD EPYC 9575F,
20 vCPU, 157 GB RAM, Ubuntu 24.04.4 LTS, Kernel `6.8.0-137-generic`, `amdgpu-dkms 6.19.14.31400000`,
**ROCm 7.14.0** (`amdrocm-opencl7.14`), OpenCL `AMD Accelerated Parallel Processing`, `OpenCL 2.1 AMD-APP
(3581.0)`, Treiber **`3581.0 (HSA1.1,LC)`**, Temurin 21.0.12.1+1. Das Image installiert die ROCm-OpenCL-Laufzeit,
registriert sie aber nicht; dafür `ocl-icd-libopencl1` installiert und
`/etc/OpenCL/vendors/amdocl64.icd` → `/opt/rocm/core-7.14/lib/opencl/libamdocl64.so` angelegt (sonst nichts
geändert). Stand des Klons: `36e31137` (der Branch war inzwischen weitergewandert; Quellcode des Kernels und von
`OpenCLContext` gegenüber `088d3c48` für die Frage unverändert). Probe und Befehle wie bei NVIDIA:
```
### -Xcheck:jni handler check (cl, 10)
Warning: SIGSEGV handler modified!
Warning: SIGILL handler modified!
Warning: SIGFPE handler modified!
Warning: SIGBUS handler modified!
Warning: SIGUSR2 handler modified!
Signal Handlers:
   SIGSEGV: SignalHandler in libLLVM.so.23.0git, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, unblocked
  *** Handler was modified!
  *** Expected: javaSignalHandler in libjvm.so, mask=11100100110111111111111111111110, flags=SA_RESTART|SA_SIGINFO
    SIGBUS: SignalHandler in libLLVM.so.23.0git, … (ebenso)
    SIGFPE: SignalHandler in libLLVM.so.23.0git, … (ebenso)
   SIGPIPE: javaSignalHandler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked
   SIGXFSZ: SignalHandler in libLLVM.so.23.0git, … *** Handler was modified!
    SIGILL: SignalHandler in libLLVM.so.23.0git, … *** Handler was modified!
   SIGUSR2: SignalHandler in libLLVM.so.23.0git, …
  *** Handler was modified!
  *** Expected: SR_handler in libjvm.so, mask=00000000000000000000000000000000, flags=SA_RESTART|SA_SIGINFO
    SIGHUP / SIGINT / SIGTERM / SIGQUIT / SIGTRAP: SignalHandler in libLLVM.so.23.0git, … flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|…
survived
### probe runs
nocl run 1 exit=0 1 hs_err=0   (… run 2–5 identisch)
cl run 1 exit=0 1 hs_err=0     (… run 2–5 identisch)
jsig run 1 exit=0 1            (… run 2–5 identisch)
```
Der ROCm-OpenCL-Treiber baut Kernel mit einem in den Prozess geladenen `libLLVM.so.23.0git` und ersetzt dabei —
wie pocl — die Handler für `SIGSEGV`, `SIGBUS`, `SIGFPE`, `SIGILL`, dazu `SIGUSR2` (den Suspend/Resume-Handler
`SR_handler` der JVM), `SIGXFSZ` und die Abbruchsignale `SIGHUP`/`SIGINT`/`SIGTERM`/`SIGQUIT`/`SIGTRAP`, jeweils
mit `SA_RESETHAND`. Die Absturz-Probe (8 heiße Threads, 2000 erzwungene Safepoints nach dem Kernel-Bau) überlebte
trotzdem 5/5 (pocl laut `TODO.md`: 5/5 Abstürze). Kosten des Laufs: ~11,5 min Droplet, ~0,72 USD.

**Intel — nicht ausgeführt.** Kein Anbieter, bei dem ein Konto besteht, vermietet Intel-GPUs (Intel Tiber AI
Cloud nicht mehr erreichbar); kein Intel-Rechner mit Linux verfügbar.

**Folgerung:** Die Warnung darf **nicht** auf pocl eingeengt werden: der AMD-ROCm-Treiber ersetzt die
Signal-Handler der JVM genauso (LLVM in-process, `SA_RESETHAND`), sogar `SIGUSR2` und die Abbruchsignale. Dass die
Probe auf ROCm nicht abstürzte, entkräftet das nicht — sie misst nur eine Absturzbedingung; ein ersetzter
`SR_handler` und ersetzte `SIGTERM`/`SIGINT`-Handler sind eigene, ungemessene Risiken (Thread-Suspendierung,
Shutdown-Hooks). Der NVIDIA-Treiber ersetzt nichts (0/5). Die Warnung von `SignalChainingCheck` und der
`LD_PRELOAD`-Block sollten daher für jeden Linux-Start bleiben; die README („Known Issues“) sollte pocl **und**
AMD ROCm als gemessen betroffen nennen, NVIDIA als gemessen unbetroffen, Intel als offen.

---

## B8 Kosten des Zweigs für den Anker 0 im Kernel (F129)

Verfahren: `TODO.md`, „Measure the zero-anchor branch of the key kernel on a real GPU“ (Commit vor
`1a7b264` gegen `1a7b264`, gleiche Sweet-Spot-Konfiguration, mehrere Wiederholungen).

**Hypothese.** Nicht messbar (eine ODER-Verknüpfung je Work-Item).
**Entscheidet.** Ob der Zweig so bleiben kann.

**Ergebnis:** RTX 3070 Laptop (Windows, Treiber 581.83), `8b0faa60` (= `1a7b264~1`) gegen `1a7b2648`, je ein
Git-Worktree. Die Sweet-Spot-Konfiguration `batchSizeInBits=24`, `keysPerWorkItem=512`; da
`GridSizeSweepBenchmark` `useReducedRadixField` nicht als Parameter hat, lief eine Wegwerf-JMH-Klasse
(identisch zu dessen `oneKernelLaunch`, plus `@Param useReducedRadixField {true,false}`; Quelltext unten, nicht
committet). JMH `-f 1 -wi 2 -w 5 -i 5 -r 10` (die Werte von `GridSizeSweepBenchmark`), Einheit ops/s =
Kernel-Starts à 2^24 Schlüssel pro Sekunde.

Läufe 1–4 (beide Zweige je Lauf, Reihenfolge vorher/nachher/nachher/vorher):
```
run1 vorher   true 1,198 ± 0,005   false 1,147 ± 0,007
run2 nachher  true 1,185 ± 0,007   false 1,155 ± 0,020
run3 nachher  true 1,187 ± 0,003   false 1,122 ± 0,007
run4 vorher   true 1,186 ± 0,002   false 2,813 ± 0,008
```
Der Sprung in Lauf 4 (`false` 2,4× schneller) war ein Zustandswechsel der Laptop-GPU während der Messung
(Leistungsprofil/Takt), kein Code-Effekt — die Wiederholungen im neuen, stabilen Zustand (danach
`nvidia-smi`: 1740 MHz, P0, ~41 W) zeigen es, abwechselnd vorher/nachher:
```
false: run5 vorher 2,824 ± 0,010 | run6 nachher 2,844 ± 0,010 | run7 vorher 2,838 ± 0,016 | run8 nachher 2,820 ± 0,073
true:  run9 vorher 2,767 ± 0,031 | run10 nachher 2,892 ± 0,060 | run11 vorher 2,880 ± 0,024 | run12 nachher 2,898 ± 0,015
```
Mittel im stabilen Zustand: `false` vorher 2,831 / nachher 2,832 ops/s; `true` vorher 2,824 / nachher 2,895 ops/s
(der erste Lauf 2,767 ist der Aufwärmlauf; ohne ihn vorher 2,880).

Wegwerf-Probe (`src/test/java/net/ladenthin/bitcoinaddressfinder/benchmark/B8ZeroAnchorProbe.java`, nur in den
Scratch-Worktrees):
```java
@BenchmarkMode(Mode.Throughput) @OutputTimeUnit(TimeUnit.SECONDS) @State(Scope.Benchmark)
public class B8ZeroAnchorProbe {
    @Param({"24"}) public int batchSizeInBits;
    @Param({"512"}) public int keysPerWorkItem;
    @Param({"true", "false"}) public boolean useReducedRadixField;
    private OpenCLContext ctx; private BigInteger privateKeyBase;
    @Setup(Level.Trial) public void setUp() throws Exception {
        CProducerOpenCL p = new CProducerOpenCL();
        p.batchSizeInBits = batchSizeInBits; p.keysPerWorkItem = keysPerWorkItem;
        p.useReducedRadixField = useReducedRadixField;
        ctx = new OpenCLContext(p, new BitHelper()); ctx.init();
        privateKeyBase = new BigInteger(256, new SecureRandom());
    }
    @Benchmark public int oneKernelLaunch() {
        try (OpenCLGridResult result = ctx.createKeys(privateKeyBase)) { return result.getResult().getInt(0); }
    }
    @TearDown(Level.Trial) public void tearDown() { if (ctx != null) ctx.close(); }
}
```

**Folgerung:** Kein messbarer Preis des Anker-0-Zweigs: Die Unterschiede zwischen den Commits (< 0,1 % bei
`false`, < 1 % bei `true` ohne Aufwärmlauf) liegen unter der Lauf-zu-Lauf-Streuung (bis ~4 %). Der Zweig kann
so bleiben. Einschränkung: gemessen auf einer Notebook-GPU mit Leistungsdeckel und dem Sweet Spot der
gemieteten Karten statt dem eigenen; der Vergleich ist relativ (gleiche Karte, gleicher Zustand), auf einer
Desktop-Karte nicht wiederholt.
