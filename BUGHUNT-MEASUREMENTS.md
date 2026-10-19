# Messauftrag B9 — OpenCL-Treiber und die Signale der JVM (BitcoinAddressFinder)

B1–B8 sind gemessen und umgesetzt; der Auftrag mit allen Ergebnissen steht im Stand `c417479`
(`git show c417479:BUGHUNT-MEASUREMENTS.md`). Offen ist nur noch B9. Hintergrund: `TODO.md`, „GPU OpenCL
drivers and the JVM's signal handlers: what replacing them breaks“, und README „OpenCL on Linux: signal
chaining“.

**Regeln für den ausführenden Agenten**

- **Nur messen und berichten.** Keine Produktionsänderung, keine Fixes. Die Proben unten liegen außerhalb
  des Repositorys (z. B. `/tmp/sigprobe/`); committet wird nur diese Datei mit den Ergebnissen.
- Arbeitsstand: Branch `ccr-90fc5f6a-vs4s0f`.
- Dateien aus `.github/shared-files.sha256` nicht verändern.
- Jeder Teil wird beantwortet, auch wenn er nicht ausführbar war: dann mit Grund. Zu jedem Ergebnis die
  Umgebung (Rechner, CPU/GPU, Treiber, OS, JDK) und die wörtlichen Ausgaben (die Zeilen des Skripts,
  `-Xcheck:jni`-Ausschnitt, Exit-Codes).
- Ergebnis unter **Ergebnis:**, darunter **Folgerung:**.

---

## Was bekannt ist

- B7: pocl 5.0 und AMD ROCm 7.14 ersetzen die Signal-Handler der JVM nach dem Kernel-Bau (`-Xcheck:jni`),
  NVIDIA 580 nicht. Die Absturz-Probe (8 heiße Threads, 2000 Safepoints): pocl 5/5 Abstürze, ROCm 0/5.
- Beide ersetzen **dieselbe Menge**: `SIGSEGV`, `SIGBUS`, `SIGFPE`, `SIGILL`, `SIGUSR2`, `SIGHUP`, `SIGINT`,
  `SIGTERM`, `SIGQUIT`, `SIGTRAP`; nur `SIGPIPE` bleibt bei der JVM.
- **Referenz, pocl, lokal gemessen 2026-10-19** (Sandbox, x86-64, Ubuntu 24.04, OpenJDK 21.0.12, pocl 5.0,
  `POCL_MAX_WORK_GROUP_SIZE=256`), Skript und Probe wie unten, `cl` = Kernel gebaut:

  ```
  pocl cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
  pocl cl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
  pocl cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0
  pocl cl SIGQUIT exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
  pocl cl jcmd: alive-after=yes output=[2776: OpenJDK 64-Bit Server VM version 21.0.12.1+1-1-24.04.4-Ubuntu ]
  pocl cl JFR exit=0 done=1 executionSamples=2767
  pocl+jsig cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
  ```

  Ohne OpenCL (`nocl`) und mit `libjsig` sind alle Zeilen wie oben, außer `SIGQUIT`: dort `dump=1`. Also:
  die Shutdown-Hooks laufen, `jcmd` und JFR gehen, **`kill -3` liefert ohne `libjsig` keinen Thread-Dump**.
  (Bei `SIGQUIT` schickt das Skript 3 s später `SIGTERM` hinterher, daher Exit 143.)

## Vorbereitung (Linux)

```bash
mvn -B -q -DskipTests -Denforcer.skip=true compile
mvn -B -q dependency:build-classpath -Dmdep.outputFile=/tmp/sigprobe/cp.txt -DincludeScope=runtime
export CP="target/classes:$(cat /tmp/sigprobe/cp.txt)"
export OPENS="--add-opens=java.base/java.nio=ALL-UNNAMED --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
export PROBE_DIR=/tmp/sigprobe
export JSIG="$JAVA_HOME/lib/libjsig.so"
# SigProbe4.java und signal-matrix.sh (unten) nach /tmp/sigprobe/ legen, dann:
javac -cp "$CP" -d /tmp/sigprobe /tmp/sigprobe/SigProbe4.java
chmod +x /tmp/sigprobe/signal-matrix.sh
clinfo -l                                # Plattform-Index des zu messenden Treibers; PLATFORM=<n> setzen, falls nicht 0
java -Xcheck:jni $OPENS -cp "$PROBE_DIR:$CP" SigProbe4 cl wait ${PLATFORM:-0} &   # Handler sichtbar machen
#   nach "READY <pid>": kill -TERM <pid>; die Zeilen "handler modified" und "Signal Handlers:" festhalten
/tmp/sigprobe/signal-matrix.sh <label> java                         # ohne libjsig
/tmp/sigprobe/signal-matrix.sh <label>+jsig env LD_PRELOAD="$JSIG" java
```

Die Absturz-Probe aus B7 (`SigProbe3`, Quelltext in `c417479`) zusätzlich, wo sie nicht schon gemessen ist
(Intel): je 5 Läufe `nocl`/`cl`/`jsig` mit 2000 Safepoints.

`SigProbe4.java`:

```java
import java.util.concurrent.atomic.AtomicBoolean;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerOpenCL;
import net.ladenthin.bitcoinaddressfinder.opencl.OpenCLContext;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;

/** B9: what happens to the JVM's own signals after an OpenCL kernel was built. */
public class SigProbe4 {
  static volatile long sink;

  // args: <cl|nocl> <wait|jfr> [platformIndex]
  public static void main(String[] a) throws Exception {
    CProducerOpenCL c = new CProducerOpenCL();
    c.batchSizeInBits = 8;
    c.keysPerWorkItem = 16;
    if (a.length > 2) c.platformIndex = Integer.parseInt(a[2]);
    OpenCLContext ctx = new OpenCLContext(c, new BitHelper());
    if (a[0].equals("cl")) ctx.init();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("HOOK RAN")));
    if (a[1].equals("wait")) {
      System.out.println("READY " + ProcessHandle.current().pid());
      System.out.flush();
      Thread.sleep(120_000);
      System.out.println("TIMEOUT");
      return;
    }
    // jfr: hot threads for 15 s while a recording samples them (JFR suspends threads via SIGUSR2)
    AtomicBoolean run = new AtomicBoolean(true);
    Thread[] ts = new Thread[8];
    for (int t = 0; t < ts.length; t++) {
      ts[t] = new Thread(() -> { long x = 0; while (run.get()) { for (int i = 0; i < 1_000_000; i++) x += i ^ (x >>> 3); sink = x; } });
      ts[t].start();
    }
    Thread.sleep(15_000);
    run.set(false);
    for (Thread t : ts) t.join();
    System.out.println("DONE");
  }
}
```

`signal-matrix.sh` (je Modus `nocl`/`cl`: `SIGTERM`, `SIGINT`, `SIGHUP`, `SIGQUIT` an die wartende Probe, dann
`jcmd <pid> VM.version`, dann eine JFR-Aufnahme mit Method-Sampling):

```bash
#!/usr/bin/env bash
# usage: signal-matrix.sh <label> <java-prefix...>   (env: CP, OPENS, PROBE_DIR, PLATFORM)
set -u
label=$1; shift
for mode in nocl cl; do
  for sig in TERM INT HUP QUIT; do
    out=$(mktemp)
    "$@" $OPENS -cp "$PROBE_DIR:$CP" SigProbe4 $mode wait ${PLATFORM:-0} >"$out" 2>&1 &
    pid=$!
    for i in $(seq 1 120); do grep -q '^READY' "$out" && break; sleep 0.5; done
    jpid=$(awk '/^READY/{print $2}' "$out")
    kill -s $sig "$jpid"; sleep 3
    if [ "$sig" = QUIT ]; then kill -s TERM "$jpid" 2>/dev/null; fi
    wait $pid; rc=$?
    echo "$label $mode SIG$sig exit=$rc hook=$(grep -c 'HOOK RAN' "$out") dump=$(grep -c 'Full thread dump' "$out") llvmcrash=$(grep -c 'Stack dump:\|PLEASE submit a bug report' "$out") hserr=$(grep -c 'A fatal error has been detected' "$out")"
    rm -f "$out"
  done
  # attach (jcmd) uses SIGQUIT to start the attach listener
  out=$(mktemp)
  "$@" $OPENS -cp "$PROBE_DIR:$CP" SigProbe4 $mode wait ${PLATFORM:-0} >"$out" 2>&1 &
  pid=$!
  for i in $(seq 1 120); do grep -q '^READY' "$out" && break; sleep 0.5; done
  jpid=$(awk '/^READY/{print $2}' "$out")
  jc=$(timeout 30 jcmd "$jpid" VM.version 2>&1 | grep -v '^Picked up' | head -2 | tr '\n' ' ')
  alive=$(kill -0 "$jpid" 2>/dev/null && echo yes || echo no)
  kill -s TERM "$jpid" 2>/dev/null; wait $pid
  echo "$label $mode jcmd: alive-after=$alive output=[$jc]"
  rm -f "$out"
  # JFR method sampling (SIGUSR2 suspend/resume)
  rm -f /tmp/b9.jfr
  "$@" $OPENS -XX:StartFlightRecording=filename=/tmp/b9.jfr,settings=profile -cp "$PROBE_DIR:$CP" SigProbe4 $mode jfr ${PLATFORM:-0} >/tmp/b9-jfr.out 2>&1
  rc=$?
  samples=$(jfr summary /tmp/b9.jfr 2>/dev/null | awk '$1=="jdk.ExecutionSample"{print $2}')
  echo "$label $mode JFR exit=$rc done=$(grep -c '^DONE' /tmp/b9-jfr.out) executionSamples=${samples:-none}"
done
```

Spalten: `exit` = Exit-Code, `hook` = Shutdown-Hook gelaufen, `dump` = Thread-Dump gedruckt,
`llvmcrash` = LLVM-Absturzausgabe (`Stack dump:`), `hserr` = Absturz der JVM, `executionSamples` = Anzahl
`jdk.ExecutionSample` in der Aufnahme.

---

## B9.1 AMD ROCm

Wie B7 (z. B. DigitalOcean `gpu-mi325x1-256gb` oder eine RDNA3-Karte unter Linux; ICD ggf. registrieren wie
in B7). `-Xcheck:jni`, dann das Skript ohne und mit `libjsig`.

**Hypothese.** Wie pocl: Hooks laufen, `kill -3` ohne Dump, `jcmd` und JFR gehen. Abweichungen wären der
eigentliche Befund (etwa JFR ohne Samples oder ein Abbruch bei `SIGUSR2`).
**Entscheidet.** Die README-Zeile für ROCm (Folgen statt nur ersetzter Signale).

**Ergebnis:**

**Folgerung:**

---

## B9.2 Intel CPU-OpenCL-Runtime (jede x86-VM)

```bash
wget -O- https://apt.repos.intel.com/intel-gpg-keys/GPG-PUB-KEY-INTEL-SW-PRODUCTS.PUB \
  | gpg --dearmor | sudo tee /usr/share/keyrings/oneapi-archive-keyring.gpg >/dev/null
echo "deb [signed-by=/usr/share/keyrings/oneapi-archive-keyring.gpg] https://apt.repos.intel.com/oneapi all main" \
  | sudo tee /etc/apt/sources.list.d/oneAPI.list
sudo apt-get update && sudo apt-get install -y intel-oneapi-runtime-opencl ocl-icd-libopencl1 clinfo
clinfo -l      # falls die Intel-Plattform fehlt: die ICD-Datei des Pakets nach /etc/OpenCL/vendors/ verlinken
```

Kein pocl installieren (oder `PLATFORM` auf die Intel-Plattform setzen). `-Xcheck:jni`, `SigProbe3` (5×
`nocl`/`cl`/`jsig`), dann das Skript ohne und mit `libjsig`.

**Hypothese.** LLVM im Prozess, also dieselbe Menge ersetzter Handler wie pocl.
**Entscheidet.** Eine README-Zeile für Intels CPU-Runtime.

**Ergebnis:**

**Folgerung:**

---

## B9.3 Intel-GPU (compute-runtime/NEO)

Nur mit Intel-GPU unter Linux (Arc oder iGPU; Ubuntu `intel-opencl-icd`, notfalls ein Live-USB). Gleiche
Schritte wie B9.2. Ohne Hardware: mit Grund beantworten.

**Hypothese.** Der Kernel-Compiler `libigc` ist LLVM-basiert; ob er die Handler setzt, ist offen.
**Entscheidet.** Die Intel-Zeile der README-Tabelle (heute „not measured“).

**Ergebnis:**

**Folgerung:**
