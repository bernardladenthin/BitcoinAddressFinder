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

**Ergebnis:**

**Folgerung:**

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

**Ergebnis:**

**Folgerung:**

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

**Ergebnis:**

**Folgerung:**

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

**Ergebnis:**

**Folgerung:**

---

## B5 F136: `sweepFilterTypes` mit zwei echten OpenCL-Geräten

**Ziel.** F136 (der Tuner misst die Filtertypen auf dem ersten Gerät mit dessen eigenem Gewinner) ließ sich
nur mit einem Gerät prüfen. Mit zwei Geräten (z. B. iGPU + dGPU, oder pocl + GPU):

Ausgangspunkt `examples/config_TuneConfiguration.json`, dort `tuneConfiguration.sweepFilterTypes` auf
`true` setzen und die `producerOpenCL`-Liste leer lassen (dann werden alle erkannten GPUs gemessen) oder
zwei Geräte explizit eintragen.

Im Bericht prüfen: der Filter-Sweep des ersten Geräts nutzt dessen eigenes Raster, und die ausgegebene
Konfiguration trägt für jedes Gerät seine eigenen Werte.

**Ergebnis:**

**Folgerung:**

---

## B6 Heap-Rückgewinnung vor dem GPU-Filter-Upload

Verfahren, Hintergrund und Erwartung: `TODO.md`, „Re-measure the heap reclaim before the GPU filter
upload on the chunked LWJGL upload“. Braucht eine GPU mit genügend Speicher für den Full-DB-Filter
(gemessen wurde ursprünglich auf einer RX 7900 XTX).

**Entscheidet.** Ob `OpenCLContext.reclaimHeapBeforeUpload()` (`GcTrigger`) mit dem stückweisen
LWJGL-Upload noch nötig ist.

**Ergebnis:**

**Folgerung:**

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

**Ergebnis:**

**Folgerung:**

---

## B8 Kosten des Zweigs für den Anker 0 im Kernel (F129)

Verfahren: `TODO.md`, „Measure the zero-anchor branch of the key kernel on a real GPU“ (Commit vor
`1a7b264` gegen `1a7b264`, gleiche Sweet-Spot-Konfiguration, mehrere Wiederholungen).

**Hypothese.** Nicht messbar (eine ODER-Verknüpfung je Work-Item).
**Entscheidet.** Ob der Zweig so bleiben kann.

**Ergebnis:**

**Folgerung:**
