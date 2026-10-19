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

**Ergebnis:** Gemessen 2026-10-19, 18:48–19:08 UTC.

**Umgebung:**
- DigitalOcean `gpu-mi325x1-256gb`, `tor1`, Image `gpu-amd-base`, KVM-Gast.
- AMD EPYC 9575F, 20 vCPU, 157 GB RAM, Ubuntu 24.04.4 LTS, Kernel `6.8.0-137-generic`.
- 1× **MI325X** (`gfx942:sramecc+:xnack-`), `amdgpu-dkms 1:6.19.14.31400000`, `amdrocm-opencl7.14 7.14.0~pre3`.
- ICD von Hand registriert wie in B7 (`/etc/OpenCL/vendors/amdocl64.icd` → `/opt/rocm/core-7.14/lib/opencl/libamdocl64.so`).
- Plattform `AMD Accelerated Parallel Processing`, `OpenCL 2.1 AMD-APP (3581.0)`, Treiber **`3581.0 (HSA1.1,LC)`**, `PLATFORM=0`.
- Temurin `21.0.12.1+1`, Branch-Stand `2d79dd5a`.
- Skript und Probe wörtlich aus diesem Auftrag, gestartet aus einem `nohup bash`-Lauf.

`-Xcheck:jni`, `SigProbe4 cl wait 0`, 5 s nach `READY` `SIGTERM`, Exit 143:

```
Warning: SIGSEGV handler modified!
Warning: SIGILL handler modified!
Warning: SIGFPE handler modified!
Warning: SIGBUS handler modified!
Warning: SIGUSR2 handler modified!
Signal Handlers:
   SIGSEGV: SignalHandler in libLLVM.so.23.0git, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, unblocked
    SIGBUS: SignalHandler in libLLVM.so.23.0git, …
    SIGFPE: SignalHandler in libLLVM.so.23.0git, …
   SIGPIPE: javaSignalHandler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked
   SIGXFSZ: SignalHandler in libLLVM.so.23.0git, …
    SIGILL: SignalHandler in libLLVM.so.23.0git, …
   SIGUSR2: SignalHandler in libLLVM.so.23.0git, …
    SIGHUP: SignalHandler in libLLVM.so.23.0git, …
    SIGINT: SignalHandler in libLLVM.so.23.0git, …
   SIGTERM: SignalHandler in libLLVM.so.23.0git, …
   SIGQUIT: SignalHandler in libLLVM.so.23.0git, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, blocked
   SIGTRAP: SignalHandler in libLLVM.so.23.0git, …
Consider using jsig library.
READY 9206
HOOK RAN
```

(`…` = dieselbe Maske und dieselben Flags wie in der Zeile `SIGSEGV`.) Die Menge ist dieselbe wie in B7, einschließlich `SIGXFSZ`, das in der Liste unter „Was bekannt ist“ fehlt. `nocl` mit `-Xcheck:jni` gab keine Warnung, Exit 143, `HOOK RAN`.

`signal-matrix.sh amd java` und `signal-matrix.sh amd+jsig env LD_PRELOAD="$JSIG" java`, wörtlich:

```
amd nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
amd nocl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd nocl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
amd nocl jcmd: alive-after=yes output=[9774: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
amd nocl JFR exit=0 done=1 executionSamples=7375
amd cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
amd cl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd cl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
amd cl jcmd: alive-after=yes output=[11221: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
amd cl JFR exit=0 done=1 executionSamples=7374
amd+jsig nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
amd+jsig nocl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd+jsig nocl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd+jsig nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
amd+jsig nocl jcmd: alive-after=yes output=[12208: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
amd+jsig nocl JFR exit=0 done=1 executionSamples=7380
amd+jsig cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
amd+jsig cl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd+jsig cl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
amd+jsig cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
amd+jsig cl jcmd: alive-after=yes output=[13402: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
amd+jsig cl JFR exit=0 done=1 executionSamples=7390
```

Die Zeilen `SIGINT`/`SIGHUP` mit `exit=0` messen das Signal nicht. Der Grund steht unter „Messfehler im Skript“ am Ende. Wiederholt mit `SIGHUP`/`SIGINT` auf Standard zurückgesetzt (`signal-matrix-t.sh`, das ist `signal-matrix.sh` ohne `jcmd`/JFR plus Spalte `timeout`; Präfix `env --default-signal=HUP,INT`):

```
amd-dfl nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl nocl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl nocl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig nocl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig nocl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig cl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
```

Die Absturz-Probe `SigProbe3` wurde nicht wiederholt, sie ist aus B7 bekannt (ROCm 0/5). Laufzeit der Droplet 18:47:00–19:08:56 UTC, rund 22 min, etwa 1,40 USD.

**Folgerung:**
- **Hypothese teilweise widerlegt, zum Guten.** Die Folgen bei ROCm sind milder als bei pocl. `SIGTERM`, `SIGINT` und `SIGHUP` beenden die JVM mit 143/130/129, und die Shutdown-Hooks laufen. `jcmd` und JFR gehen (7374 Samples mit `cl`, 7375 ohne, also kein Verlust beim `SIGUSR2`-Suspend).
- **Der Unterschied zu pocl:** `kill -3` liefert auch **ohne `libjsig` einen Thread-Dump** (`dump=1`).
- Die Handler sind trotzdem ersetzt, `SIGQUIT` eingeschlossen. Laut `-Xcheck:jni` sind sie zum Zeitpunkt der Prüfung eingetragen. Die beobachtbaren Folgen bleiben aber aus. Dazu passt die Absturz-Probe in B7 (0/5). Die naheliegende Erklärung, nicht gemessen: Der LLVM-Handler gibt nicht behandelte Signale an den vorher eingetragenen Handler der JVM weiter. Bei pocl geht `SIGQUIT` verloren.
- **README-Zeile für ROCm:** „replaces the handlers (same set as pocl); measured consequences on ROCm 7.14 / MI325X: none — hooks, `kill -3` dump, `jcmd` and JFR work, crash probe 0/5. `libjsig` is still recommended, since `-Xcheck:jni` reports the handlers as replaced.“

**Nachtrag: RDNA3 (Radeon PRO V710) mit ROCm 10.1.** Gemessen 2026-10-19, 22:28–23:01 UTC, dieselben Skripte
und Proben wie oben (`b9-run.sh amd`, danach `SigProbe3` und die Wiederholung mit `--default-signal`).

**Umgebung:**
- Azure `Standard_NV24ads_V710_v5` (ganze GPU), `southcentralus`, Image `Canonical:ubuntu-24_04-lts:server:latest`.
- AMD EPYC 9V64, 24 vCPU, 125 GB RAM, Ubuntu 24.04.5 LTS, Kernel `7.0.0-1017-azure`.
- GPU als SR-IOV-VF `1002:7461`, `rocminfo`: **`AMD Radeon Pro V710 MxGPU`**, `gfx1101` (RDNA3, wie RX 7700/7800 XT), 27 CUs laut OpenCL.
- `amdgpu-install` 31.60 (`--usecase=opencl`): `amdgpu-dkms 1:7.1.9.31600000`, `amdrocm-opencl10.1 10.1.0-3`.
- ICD von Hand registriert (`/opt/rocm/core-10.1/lib/opencl/libamdocl64.so`). Plattform `OpenCL 2.1 AMD-APP (3684.0)`, Treiber **`3684.0 (HSA1.1,LC)`**, `PLATFORM=0`.
- Temurin `21.0.12.1+1`, Branch-Stand `606823a9`.

`-Xcheck:jni`, `SigProbe4 cl wait 0`: dieselben fünf Warnungen und dieselbe Menge wie auf der MI325X, jetzt aus
`libLLVM.so.24.0git`. `SIGPIPE` bleibt bei der JVM, `SIGQUIT` ist wieder `blocked`. Exit 143, `HOOK RAN`.

```
   SIGSEGV: SignalHandler in libLLVM.so.24.0git, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, unblocked
   SIGPIPE: javaSignalHandler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked
   SIGQUIT: SignalHandler in libLLVM.so.24.0git, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, blocked
```

(`SIGBUS`, `SIGFPE`, `SIGXFSZ`, `SIGILL`, `SIGUSR2`, `SIGHUP`, `SIGINT`, `SIGTERM`, `SIGTRAP`: wie `SIGSEGV`.)

Matrix, wörtlich. Die Zeilen ohne `libjsig` sind gleich aufgebaut wie die mit `libjsig`:

```
amd cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
amd cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
amd cl jcmd: alive-after=yes output=[33119: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
amd cl JFR exit=0 done=1 executionSamples=7291
amd nocl JFR exit=0 done=1 executionSamples=7280
amd+jsig cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
amd+jsig cl JFR exit=0 done=1 executionSamples=7266
amd-dfl cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig cl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
amd-dfl+jsig cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
```

- Alle übrigen Zeilen haben `hook=1 llvmcrash=0 hserr=0`, und `jcmd` lief jedes Mal mit `alive-after=yes`.
- `SIGINT`/`SIGHUP` aus dem Lauf wie im Auftrag (geerbte Einstellungen) wieder `exit=0`. Das ist der Messfehler im Skript, belegt wie unten: `SigIgn: 0000000000000003`.
- Absturz-Probe `SigProbe3` (8 heiße Threads, 2000 Safepoints): `nocl` 0/5, `cl` 0/5, `cl` mit `libjsig` 0/5. Jeder Lauf mit Exit 0 und `survived`, keine `hs_err`-Datei.
- Laufzeit der VM 22:19:38–23:05:20 UTC, rund 46 min, etwa 1,60 EUR.

**Folgerung (Nachtrag):**
- Auf RDNA3 mit ROCm 10.1 ist das Bild **genau wie auf CDNA3 mit ROCm 7.14**: ersetzt ist dieselbe Menge, beobachtbare Folgen gibt es keine. Der Dump bei `kill -3` kommt auch ohne `libjsig`, JFR zeigt keinen Sample-Verlust, die Absturz-Probe ergibt 0/5.
- Das Ergebnis hängt damit weder an der GPU-Generation noch an der ROCm-Hauptversion (7.14 und 10.1, `libLLVM` 23 und 24).
- Für die README-Zeile: „measured consequences on ROCm 7.14 / MI325X (CDNA3) and ROCm 10.1 / Radeon PRO V710 (RDNA3, `gfx1101`): none …“, Rest unverändert.

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

**Ergebnis:** Gemessen 2026-10-19, 18:33–19:15 UTC.

**Umgebung:**
- Vultr `vhp-8c-16gb-intel` (`fra`), KVM-Gast, 8 vCPU „Intel Xeon Processor (Cascadelake)“, 15 GB RAM.
- Ubuntu 24.04.5 LTS, Kernel `6.8.0-146-generic`.
- `intel-oneapi-runtime-opencl 2026.2.0-863` aus dem oneAPI-Repository, wie im Auftrag; die ICD-Datei registrierte das Paket selbst (`/etc/OpenCL/vendors/intel64.icd` → `/opt/intel/oneapi/redist/lib/libintelocl.so`).
- Kein pocl. Einzige Plattform `Intel(R) OpenCL`, `OpenCL 3.1 LINUX`, Gerät `Intel Xeon Processor (Cascadelake)`, `CL_DEVICE_TYPE_CPU`, Treiber **`2026.22.10.0.01_160000`**, `PLATFORM=0`.
- Temurin `21.0.12.1+1`, Branch-Stand `2d79dd5a`.

`-Xcheck:jni`, `SigProbe4 cl wait 0`, 5 s nach `READY` `SIGTERM`, Exit 143:

```
Warning: SIGSEGV handler modified!
Warning: SIGILL handler modified!
Warning: SIGFPE handler modified!
Warning: SIGBUS handler modified!
Warning: SIGUSR2 handler modified!
Signal Handlers:
   SIGSEGV: SignalHandler in libintelocl.so, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, unblocked
    SIGBUS: SignalHandler in libintelocl.so, …
    SIGFPE: SignalHandler in libintelocl.so, …
   SIGPIPE: javaSignalHandler in libjvm.so, mask=11100100010111111101111111111110, flags=SA_RESTART|SA_SIGINFO, unblocked
   SIGXFSZ: SignalHandler in libintelocl.so, …
    SIGILL: SignalHandler in libintelocl.so, …
   SIGUSR2: SignalHandler in libintelocl.so, …
    SIGHUP: SignalHandler in libintelocl.so, …
    SIGINT: SignalHandler in libintelocl.so, …
   SIGTERM: SignalHandler in libintelocl.so, …
   SIGQUIT: SignalHandler in libintelocl.so, mask=00000000000000000000000000000000, flags=SA_ONSTACK|SA_RESETHAND|SA_SIGINFO|SA_NODEFER|Unknown_flags:80000000, blocked
   SIGTRAP: SignalHandler in libintelocl.so, …
Consider using jsig library.
READY 3222
HOOK RAN
```

(`…` = Maske und Flags wie bei `SIGSEGV`.) Die Warnungen kommen im Log zwischen der Geräteinfo (18:35:56,529) und der Meldung zu `noInlineHelpers` (18:35:56,595). Der Kernel-Bau endet erst um 18:36:09 (`Kernel resource usage`). Die Handler sind also schon beim Laden bzw. beim Kontext ersetzt, nicht erst beim Kernel-Bau. Als Besitzer steht `libintelocl.so` da, kein eigenes `libLLVM`. Dass es LLVMs Handler ist, zeigt erst dessen Absturz-Text weiter unten. `nocl` mit `-Xcheck:jni` gab keine Warnung, Exit 143, `HOOK RAN`.

`SigProbe3`, je 5 Läufe mit 2000 Safepoints:

```
nocl run 1..5 exit=0 survived=1 hs_err=0          (alle fünf gleich)
/root/b9/b9-run.sh: line 65:  3907 Segmentation fault      timeout 600 java $OPENS -cp "$PROBE_DIR:$CP" SigProbe3 $mode 2000 > /tmp/p.out 2>&1
cl run 1 exit=139 survived=0 hs_err=0
cl run 2 exit=139 survived=0 hs_err=0
cl run 3 exit=139 survived=0 hs_err=0
cl run 4 exit=139 survived=0 hs_err=0
cl run 5 exit=139 survived=0 hs_err=0             (jeder mit eigener "Segmentation fault"-Zeile)
jsig run 1..5 exit=0 survived=1 hs_err=0          (alle fünf gleich)
```

`signal-matrix.sh intel java` und `signal-matrix.sh intel+jsig env LD_PRELOAD="$JSIG" java`, wörtlich:

```
intel nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
intel nocl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel nocl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
intel nocl jcmd: alive-after=yes output=[24838: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
intel nocl JFR exit=0 done=1 executionSamples=7191
intel cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
intel cl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel cl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=1 hserr=0
intel cl jcmd: alive-after=yes output=[25350: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
intel cl JFR exit=0 done=1 executionSamples=7183
intel+jsig nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
intel+jsig nocl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel+jsig nocl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel+jsig nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
intel+jsig nocl jcmd: alive-after=yes output=[25927: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
intel+jsig nocl JFR exit=0 done=1 executionSamples=7213
intel+jsig cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0
intel+jsig cl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel+jsig cl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0
intel+jsig cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0
intel+jsig cl jcmd: alive-after=yes output=[26949: OpenJDK 64-Bit Server VM version 21.0.12.1+1-LTS ]
intel+jsig cl JFR exit=0 done=1 executionSamples=7200
```

Mit Spalte `timeout`, einmal mit den geerbten Einstellungen wie oben und einmal mit `SIGHUP`/`SIGINT` auf Standard (siehe „Messfehler im Skript“):

```
intel nocl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=1
intel nocl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=1
intel cl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=1
intel cl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=1
intel-dfl nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl nocl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl nocl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
intel-dfl cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl cl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=1 hserr=0 timeout=0
intel-dfl+jsig nocl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig nocl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig nocl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig nocl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig cl SIGTERM exit=143 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig cl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig cl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl+jsig cl SIGQUIT exit=143 hook=1 dump=1 llvmcrash=0 hserr=0 timeout=0
```

`llvmcrash=1` bei `SIGQUIT`, einmal von Hand wiederholt (`cl wait`, `kill -QUIT`, 3 s, Prozess lebt noch, dann `SIGTERM`, Exit 143):

```
READY 31540
PLEASE submit a bug report to https://software.intel.com/en-us/support/priority-support and include the crash backtrace and instructions to reproduce the bug.
2026-10-19 19:15:23
Full thread dump OpenJDK 64-Bit Server VM (21.0.12.1+1-LTS mixed mode, sharing):
…
HOOK RAN
```

**Folgerung:**
- **Hypothese bestätigt, mit pocl-artigem Absturz.** Intels CPU-Runtime ersetzt dieselbe Menge Handler wie pocl und ROCm (inklusive `SIGXFSZ`, ohne `SIGPIPE`). Sie tut es schon beim Initialisieren, nicht erst beim Kernel-Bau.
- **Absturz-Probe:** ohne `libjsig` **5/5 Abstürze** (Exit 139, kein hs_err), mit `libjsig` 0/5. Das ist dasselbe Bild wie pocl in B7, nicht wie ROCm.
- **Signale:** Shutdown-Hooks laufen bei allen Abbruchsignalen, `jcmd` und JFR gehen. `kill -3` liefert den Thread-Dump. Bei jedem `SIGQUIT` druckt aber zuerst Intels LLVM-Handler seine Absturz-Bitte „PLEASE submit a bug report …“ und gibt das Signal dann an die JVM weiter. Mit `libjsig` verschwindet diese Zeile.
- **README-Zeile für Intels CPU-Runtime:** „replaces the handlers (from `libintelocl.so`, already at context creation); crash probe 5/5 without `libjsig`, 0/5 with it — `libjsig` required, as for pocl. `kill -3` still dumps but prints a spurious LLVM 'PLEASE submit a bug report' line first.“
- Die Warnung von `SignalChainingCheck` („measured: pocl“) trifft damit auch auf Intel zu.

---

## B9.3 Intel-GPU (compute-runtime/NEO)

Nur mit Intel-GPU unter Linux (Arc oder iGPU; Ubuntu `intel-opencl-icd`, notfalls ein Live-USB). Gleiche
Schritte wie B9.2. Ohne Hardware: mit Grund beantworten.

**Hypothese.** Der Kernel-Compiler `libigc` ist LLVM-basiert; ob er die Handler setzt, ist offen.
**Entscheidet.** Die Intel-Zeile der README-Tabelle (heute „not measured“).

**Ergebnis:** Nicht messbar, keine Intel-GPU erreichbar. Geprüft 2026-10-19.

**Die einzige mietbare Möglichkeit, ausprobiert:**
- Server: Vultr Bare Metal `vbm-8c-132gb-v2` in `fra`, Intel Xeon **E-2388G**. Die CPU hat eine UHD-P750-iGPU (Gen12, von Intels aktuellem compute-runtime unterstützt).
- Bestellt 19:07 UTC, `active` mit SSH etwa 19:29, gelöscht 19:30.
- Board `Supermicro X12STE-F`, BIOS `1.9a.V1`, Ubuntu 24.04.
- Auf dem PCI-Bus steht nur die BMC-Grafik:

```
06:00.0 VGA compatible controller [0300]: ASPEED Technology, Inc. ASPEED Graphics Family [1a03:2000] (rev 52)
00:02.0 lines: 0
```

- `/dev/dri` enthält nur `card0` (`ast`), kein `renderD*`, und `i915`/`xe` sind nicht geladen.
- Die iGPU (`00:02.0`) ist im BIOS abgeschaltet. Mit dem Betriebssystem lässt sie sich nicht einschalten, also kein OpenCL-Gerät.

**Sonst:**
- Keiner der Anbieter mit vorhandenem Konto vermietet eine Intel-GPU, am selben Tag per Read-only-Listing geprüft: SaladCloud (60 GPU-Klassen, nur NVIDIA und AMD), Vast.ai, RunPod, DigitalOcean, AWS, Azure; Lightning verkauft nur AWS und GCP weiter.
- Außerhalb dieser Konten, alle ungeprüft: Hetzner Dedicated EX44 (i5-13500, UHD 770; neues Konto, i915 im Image gesperrt), Intels eigene Cloud (Lunar Lake/Arc Pro nur als Preview auf Antrag) oder eigene Hardware.

**Folgerung:**
- Die Intel-Zeile der README-Tabelle bleibt für die GPU „not measured“.
- **Begründete Erwartung, nicht gemessen:** Die CPU-Runtime aus B9.2 ersetzt die Handler mit ihrem eingebauten LLVM. Der GPU-Treiber (NEO) kompiliert über `libigc`, ebenfalls LLVM im Prozess. Sicherer Standard ist daher auch für Intel-GPUs `libjsig`. Die README kann die Intel-GPU-Zeile so lassen oder auf „presumably like Intel CPU runtime (B9.2), unmeasured“ setzen.
- Messen lässt sie sich mit den Schritten aus B9.2 auf jedem Linux-Rechner mit Intel-iGPU (Gen12 oder neuer). Statt der oneAPI-CPU-Runtime installiert man `intel-opencl-icd`, z. B. aktuell aus `ppa:kobuk-team/intel-graphics`. Zuerst mit `lspci -nn | grep -iE 'vga|display'` und `ls /dev/dri` prüfen, ob die iGPU überhaupt sichtbar ist.

---

## Messfehler im Skript: `SIGINT` (und unter `nohup` auch `SIGHUP`) kommt nie an

Gemessen auf beiden Rechnern von B9.1/B9.2, 2026-10-19.

`signal-matrix.sh` startet die Probe mit `&` aus einem nicht interaktiven bash. Für solche Hintergrundprozesse setzt bash `SIGINT` (und `SIGQUIT`) auf *ignoriert*. Läuft das Ganze unter `nohup`, ist zusätzlich `SIGHUP` ignoriert. Die JVM richtet für Signale, die beim Start schon ignoriert sind, keine Handler ein. Das Signal verpufft also, die Probe schläft ihre 120 s zu Ende, druckt `TIMEOUT`, endet mit Exit 0 und lässt dabei den Shutdown-Hook laufen. Das ergibt genau die Zeile `exit=0 hook=1`.

Belegt mit `/proc/<pid>/status` einer so gestarteten Probe und mit der Spalte `timeout` (`signal-matrix-t.sh`):

```
### SigIgn of a probe started like in signal-matrix.sh (background, under nohup)
SigIgn:	0000000000000003
SigCgt:	0000000101005ccc
### same with env --default-signal=HUP,INT
SigIgn:	0000000000000000
SigCgt:	0000000101005ccf
intel nocl SIGINT exit=0 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=1
intel nocl SIGHUP exit=0 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=1
intel-dfl nocl SIGINT exit=130 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
intel-dfl nocl SIGHUP exit=129 hook=1 dump=0 llvmcrash=0 hserr=0 timeout=0
```

(`SigIgn` 0x3 = Signal 1 `SIGHUP` und 2 `SIGINT`.) `SIGQUIT` ist nicht betroffen: Die JVM setzt ihren Handler dafür immer. Die Referenzzeile `pocl cl SIGINT exit=0 hook=1` unter „Was bekannt ist“ hat dieselbe Ursache, sie misst also nicht pocls Verhalten bei `SIGINT`. Bei `SIGHUP exit=129` war die Referenz offenbar nicht unter `nohup` gestartet.

**Für Wiederholungen:** die Probe mit `env --default-signal=HUP,INT` vor `java` starten (coreutils ≥ 8.31) und `grep -c '^TIMEOUT'` als Spalte mitschreiben. Mit Standard-Signalen ergeben ROCm und Intel-CPU bei `SIGINT` 130 und bei `SIGHUP` 129, mit und ohne OpenCL. Die Hooks laufen in beiden Fällen.
