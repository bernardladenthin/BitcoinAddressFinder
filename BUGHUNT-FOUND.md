
# Bug-Hunt: gefundene Fehler — BitcoinAddressFinder

Fortlaufendes Fundbuch der Fehlersuche auf dem Arbeitsbranch `ccr-90fc5f6a-vs4s0f`. Jeder Fund hat eine
globale ID (`Fnn`, über alle drei Repos eindeutig), Datei:Zeile, Schwere, Beschreibung, Reproduktion (Testname),
Status und Commit. Neue Funde werden **unten angehängt**; die nächste freie ID ist die höchste ID in allen drei
Repos plus eins. Geprüfte Klassen ohne Fund stehen in [`BUGHUNT-CHECKED.md`](BUGHUNT-CHECKED.md).

## Übersicht

- [F04](#f04) — niedrig — *gefixt*
- [F08](#f08) — hoch — *gefixt*
- [F10](#f10) — mittel — *gefixt*
- [F11](#f11) — niedrig — *gefixt*
- [F14](#f14) — hoch — *gefixt*
- [F15](#f15) — hoch — *gefixt*
- [F19](#f19) — hoch — *gefixt*
- [F20](#f20) — hoch — *gefixt*
- [F21](#f21) — mittel — *gefixt*
- [F22](#f22) — mittel — *gefixt*
- [F23](#f23) — niedrig — *gefixt*
- [F27](#f27) — mittel — *gefixt*
- [F31](#f31) — hoch — *gefixt*
- [F32](#f32) — niedrig — *gefixt*
- [F33](#f33) — niedrig — *gefixt*
- [F34](#f34) — mittel — *gefixt*
- [F35](#f35) — niedrig — *gefixt (Doku)*
- [F40](#f40) — mittel — *gefixt*
- [F41](#f41) — niedrig — *gefixt*
- [F42](#f42) — mittel — *gefixt*
- [F43](#f43) — mittel — *gefixt*
- [F44](#f44) — niedrig — *gefixt*
- [F45](#f45) — mittel — *gefixt*
- [F46](#f46) — niedrig — *gefixt*
- [F47](#f47) — niedrig — *gefixt*
- [F48](#f48) — niedrig — *gefixt*
- [F49](#f49) — niedrig — *gefixt*
- [F50](#f50) — niedrig — *gefixt*
- [F51](#f51) — niedrig — *gefixt*
- [F52](#f52) — mittel — *gefixt*
- [F58](#f58) — niedrig — *gefixt*
- [F66](#f66) — mittel — *gefixt*
- [F100](#f100) — niedrig — *gefixt*
- [F101](#f101) — niedrig — *gefixt*
- [F119](#f119) — niedrig — *gefixt*
- [F120](#f120) — mittel — *gefixt*
- [F121](#f121) — niedrig — *gefixt*
- [F122](#f122) — niedrig — *gefixt*
- [F124](#f124) — niedrig — *gefixt*
- [F126](#f126) — niedrig — *gefixt*
- [F127](#f127) — mittel — *gefixt*
- [F129](#f129) — niedrig — *gefixt*
- [F130](#f130) — mittel — *gefixt*
- [F131](#f131) — mittel — *gefixt*
- [F132](#f132) — mittel — *gefixt*
- [F135](#f135) — niedrig — *gefixt*
- [F136](#f136) — mittel — *gefixt*
- [F139](#f139) — mittel — *gefixt*
- [F140](#f140) — mittel — *gefixt*
- [F141](#f141) — niedrig — *gefixt (Doku)*
- [F142](#f142) — niedrig — *gefixt (Doku)*
- [F144](#f144) — niedrig — *gefixt (Doku)*
- [F145](#f145) — niedrig — *gefixt (Doku)*
- [F146](#f146) — niedrig — *gefixt (Doku)*
- [F147](#f147) — niedrig — *gefixt (Doku)*
- [F148](#f148) — mittel — *gefixt*
- [F150](#f150) — niedrig — *gefixt*
- [F151](#f151) — niedrig — *gefixt*
- [F152](#f152) — niedrig — *gefixt*
- [F153](#f153) — mittel — *gefixt*
- [F154](#f154) — niedrig — *gefixt*
- [F155](#f155) — niedrig — *gefixt*
- [F156](#f156) — niedrig — *gefixt*
- [F157](#f157) — niedrig — *gefixt*
- [F159](#f159) — niedrig — *gefixt*
- [F160](#f160) — niedrig bis mittel — *gefixt*
- [F161](#f161) — niedrig — *gefixt*
- [F162](#f162) — niedrig — *gefixt*
- [F163](#f163) — niedrig — *gefixt*
- [F164](#f164) — niedrig — *gefixt*
- [F169](#f169) — niedrig — *gefixt*
- [F170](#f170) — niedrig — *gefixt*
- [F171](#f171) — niedrig — *gefixt*
- [F191](#f191) — niedrig — *gefixt*
- [F192](#f192) — hoch — *gefixt*
- [F193](#f193) — mittel — *gefixt*
- [F194](#f194) — niedrig — *gefixt*
- [F195](#f195) — niedrig — *gefixt*
- [F196](#f196) — niedrig — *gefixt*
- [F206](#f206) — niedrig bis mittel — *gefixt*
- [F207](#f207) — niedrig bis mittel — *gefixt*
- [F208](#f208) — niedrig — *gefixt*
- [F209](#f209) — niedrig — *gefixt*
- [F210](#f210) — niedrig — *gefixt*
- [F211](#f211) — mittel — *gefixt*

## Funde

<a id="f04"></a>
### F04 – BAF: `OpenCLBuilder` fragt `CL_DEVICE_MAX_WORK_ITEM_SIZES` mit fest 3 Einträgen ab
- **Zeit:** 2026-10-16 22:58 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/OpenCLBuilder.java` (`createOpenCLDevice`, `MAX_WORK_ITEM_SIZE_DIMENSIONS = 3`) + `opencl/binding/LwjglClApi.java:193`
- **Schwere:** niedrig
- **Beschreibung:** Der Puffer für `CL_DEVICE_MAX_WORK_ITEM_SIZES` ist fest 3×`size_t` groß. Meldet ein Gerät `CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS > 3` (Spezifikation erlaubt das, Minimum ist 3), verlangt `clGetDeviceInfo` einen größeren Puffer und liefert `CL_INVALID_VALUE` → `OpenClCallFailedException` → `build()` scheitert für **alle** Plattformen, auch `OpenCLInfo`. Die Dimension wird im selben Konstruktoraufruf bereits abgefragt und könnte als Anzahl dienen. Alle gängigen Treiber (NVIDIA, AMD, Intel, PoCL) melden 3, daher theoretisch.
- **Nebenbei geprüft, widerlegt:** `getComparableVersionFromDeviceVersion` mit realen Strings (`OpenCL 2.0 AMD-APP (3513.0)`, `OpenCL 3.0 PoCL HSTR: …`, `OpenCL 1.2 Apple`, `OpenCL 3.0 Mesa 24.0.5`, …) gegen `ComparableVersion("2.0")` – alle korrekt eingeordnet.
- **Reproduktion:** `OpenCLBuilderTest#build_deviceWithMoreThanThreeWorkItemDimensions_readsEverySize` (Mockito-`ClApi`, das wie `clGetDeviceInfo` einen zu kleinen Puffer für `CL_DEVICE_MAX_WORK_ITEM_SIZES` mit `CL_INVALID_VALUE` ablehnt)
- **Fix:** `createOpenCLDevice` fragt `CL_DEVICE_MAX_WORK_ITEM_DIMENSIONS` zuerst ab und dimensioniert damit die Abfrage der Größen; die feste Konstante `MAX_WORK_ITEM_SIZE_DIMENSIONS = 3` entfällt.
- **Rot-Grün-Nachweis:** ohne Fix rot 1/4 („Unexpected exception thrown: IllegalStateException: clGetDeviceInfo: CL_INVALID_VALUE“), mit Fix grün 4/4 (1 übersprungen: der vorhandene Test für ein echtes OpenCL-Gerät, das hier fehlt), Gegenprobe per `git stash` rot, nach `stash pop` grün. `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `e3b3ef9`

<a id="f08"></a>
### F08 – BAF: Secrets-Datei mit `batchUsePrivateKeyIncrement=false` prüft kein einziges Secret
- **Zeit:** 2026-10-16 23:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerJavaSecretsFiles.java` (ehem. Override `processSecrets`, Zeile ~91)
- **Schwere:** hoch (stiller Totalausfall eines konfigurierbaren Modus)
- **Beschreibung:** `ProducerJavaSecretsFiles.processSecrets()` warf `UnsupportedOperationException("Not supported yet.")`. Mit `batchUsePrivateKeyIncrement=false` leitet `AbstractProducer.consumeSecrets` jedes Secret genau dorthin. `AbstractPlaintextFile.readFile()` fängt die Exception pro Zeile ab: Für **jede** Zeile wird „Error in line“ geloggt, die Zeile in `ReadStatistic.errors` gesammelt (wächst unbegrenzt) und **kein Secret geprüft** – der Lauf endet trotzdem „erfolgreich“. Default ist `true` und alle Beispielkonfigurationen setzen `true`, daher bisher unbemerkt. Fix: Override entfernt; die geerbte `ProducerJava.processSecrets()` erzeugt die Public Keys und gibt sie an den Consumer – wie bei den übrigen Java-Producern.
- **Reproduktion:** `ProducerJavaSecretsFilesTest#produceKeys_filesConfiguredWithoutPrivateKeyIncrement_keysCreated` (alle 4 Formate; vorher rot: 0 statt 6 Keys, danach grün). `ProducerJavaSecretsFilesTest` 11/11.
- **Nebenbefund (nicht geändert, niedrig):** `interrupt()` kann zwischen der `shouldRun`-Prüfung und `currentSecretsFile.set(...)` fallen; dann wird die nächste Datei trotzdem komplett gelesen. Außerdem bleibt `currentSecretsFile` bei einer Exception aus `readFile()` gesetzt (kein `finally`).
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `1fa0506`

<a id="f10"></a>
### F10 – BAF: Shutdown hängt, wenn mehrere Producer einen Socket/WebSocket/ZMQ-Key-Producer teilen (`timeoutMillis: -1`)
- **Zeit:** 2026-10-16 23:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/AbstractKeyProducerQueueBuffered.java` (`createSecrets`, Sentinel-Zweig; `signalShutdown`)
- **Schwere:** mittel
- **Beschreibung:** `signalShutdown()` legt **ein** `SHUTDOWN_SENTINEL` in die Queue. Mehrere Producer dürfen denselben `keyProducerId` nutzen (`Finder` prüft das nicht). Mit `timeoutMillis: -1` – genau die Einstellung, die CLAUDE.md für interaktive Feeds empfiehlt – blockiert jeder in `take()`. Nur der Aufrufer, der das Sentinel bekommt, wird geweckt; die anderen bleiben für immer hängen, der Shutdown kommt nicht zu Ende. Fix: Wer das Sentinel entnimmt, legt es vor dem Werfen wieder zurück.
- **Reproduktion:** `AbstractKeyProducerQueueBufferedTest#interrupt_unblocksEveryBlockedConsumer_negativeTimeout` (vorher rot: `TimeoutException` beim zweiten Aufrufer, danach grün). Zusätzlich grün: `AbstractKeyProducerQueueBufferedTest` 11/11, `KeyProducerJavaWebSocketTest` 5/5, `KeyProducerJavaSocketTest` 17/17, `KeyProducerJavaZmqTest` 7/7. PIT nicht gelaufen (der neue `offer`-false-Zweig ist nur Trace-Logging, wie der bestehende in `signalShutdown`).
- **Nebenbefund (nicht geändert):** Bricht `createSecrets` mitten in einem Batch ab (Timeout bei `i > 0`, nur ohne `batchUsePrivateKeyIncrement`), sind die bereits entnommenen Secrets verloren und werden nie geprüft. `addSecret` loggt bei voller Queue `{}` mit einem `byte[]` – Ausgabe ist `[B@…`, aber die Standard-Queue ist unbegrenzt.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `d193ab7`

<a id="f11"></a>
### F11 – BAF: „Average contains time“ misst praktisch immer 0 ms
- **Zeit:** 2026-10-16 23:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/consumer/ConsumerJava.java:1006` (`containsAddress`, `System.currentTimeMillis()` vor/nach jeder Einzel-Abfrage) → `statistics/Statistics.java` (`averageContainsTime … ms`)
- **Schwere:** niedrig (falsche Kennzahl, kein Funktionsfehler)
- **Beschreibung:** Jede einzelne Adressabfrage wird mit Millisekunden-Auflösung umklammert. Eine LMDB-/Filter-Abfrage dauert Mikro- bis Nanosekunden, das Delta ist daher fast immer 0 (gelegentlich 1 durch einen Millisekunden-Wechsel). Die Summe geteilt durch die Abfragen ergibt dauerhaft „Average contains time: 0 ms“. Die Statistikzeile zeigt also eine Größe, die nichts misst. Fix wäre `System.nanoTime()` plus Ausgabe in µs/ns – das ändert das Log-Format und den Hot Path, deshalb nicht angefasst.
- **Nebenbei (kosmetisch):** `Statistics.withSignificantDigits` rundet an der Einheitengrenze über: 999 960/s → „1000.0 k/s“ statt „1.000 M/s“.
- **Reproduktion:** `ConsumerJavaTest#containsAddress_lookupsFasterThanAMillisecond_sumOfTimeIsStillMeasured` (eine Abfrage, die 200 µs aktiv wartet, fünfmal)
- **Fix:** `containsAddress` misst mit `System.nanoTime()` und summiert Nanosekunden; die Statistikzeile meldet „Average contains time: N ns“. README-Beispiel und -Tabelle sowie die erwarteten Zeichenketten in `ConsumerJavaTest`/`StatisticsTest` angepasst. Achtung, sichtbare Formatänderung: Wer die Logzeile maschinell liest, sieht jetzt `ns` statt `ms`.
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is a value equal to or greater than <1000000L> but: <1L> was less than <1000000L>“), mit Fix grün; `ConsumerJavaTest` 35/35, `StatisticsTest` 11/11 (skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `0fb1dcb`

<a id="f14"></a>
### F14 – BAF: BIP39-Producer hält jeden abgeleiteten Schlüssel im Speicher → OutOfMemory bei langen Läufen
- **Zeit:** 2026-10-17 00:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/BIP39KeyProducer.java` (`nextKey`, ehem. `keyChain.getKeyByPath(path, true)`)
- **Schwere:** hoch
- **Beschreibung:** `DeterministicKeyChain.getKeyByPath(path, true)` (bitcoinj 0.17.1) legt jeden abgeleiteten Schlüssel dauerhaft in der `DeterministicHierarchy` des Chains ab. Der BIP39-Producer leitet pro Kandidat einen Schlüssel ab. **Gemessen:** Cache wächst exakt um 1 Eintrag pro Schlüssel; 200 000 Schlüssel = **103 MB** dauerhaft belegt (~540 Byte/Schlüssel). Mit dem Test-/Default-Heap von 2 GB ist nach ~4 Mio. Kandidaten Schluss. Fix: Nur der private Schlüssel am Basispfad wird gehalten, jedes Kind direkt mit `HDKeyDerivation.deriveChildKey(parent, child)` abgeleitet. Gleiche Schlüssel, nachgerechnet für 1000 normale Indizes und gehärtete Kinder; nach dem Fix bleibt bei 200 000 Schlüsseln **nichts** zurück (−1 MB gemessen).
- **Reproduktion:** `BIP39KeyProducerTest#producer_keepsNoKeyChainThatCachesEveryDerivedKey` (vorher rot, danach grün; strukturell, weil die Cache-Größe nur über bitcoinj-Interna sichtbar ist) + `#nextKey_matchesTheKeyChainDerivation_forPlainAndHardenedChildren` (Gleichwertigkeit, normal und gehärtet). `BIP39KeyProducerTest` 296/296, `KeyProducerJavaBip39Test` 1/1. Messprogramm (Heap vor/nach 200k `nextKey()`): Scratchpad `bip/Q.java`.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `8163bfe`

<a id="f15"></a>
### F15 – BAF: Beim Shutdown werden noch eingereihte Batches nie geprüft (Treffer gehen verloren)
- **Zeit:** 2026-10-17 00:35 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/consumer/ConsumerJava.java` (`consumeKeysRunner`, Schleifenende)
- **Schwere:** hoch (stiller Verlust geprüfter Arbeit inkl. möglicher Treffer)
- **Beschreibung:** `Finder.shutdownAndAwaitTermination()` stoppt zuerst die Producer, dann `ConsumerJava.interrupt()`. Laut Javadoc von `CConsumerJava.awaitQueueEmptySeconds` wartet das darauf, dass der Worker-Pool „die Keys-Queue leert“. Die Worker-Schleife (`while (shouldRun.get())`) endete aber sofort, als `shouldRun` auf `false` ging, ohne die Queue zu leeren. Alles, was noch eingereiht war (bis `queueSize` = 10 Batches à `2^batchSizeInBits` Schlüssel), blieb ungeprüft. Betroffen ist jeder Lauf, der von selbst endet: inkrementeller Bereich bis zum Ende, Secrets-Datei, `runOnce`, Socket-Feed mit Timeout. Gerade das Ende des Bereichs bzw. die letzten Zeilen fehlten, ein Treffer dort ging verloren. Mit Result-Broadcast fehlte für diese Bereiche auch das Event. Fix: Jeder Worker leert die Queue nach der Schleife einmal (`consumeKeys`), bevor er endet.
- **Reproduktion:** `ConsumerJavaTest#interrupt_batchesStillQueued_areCheckedBeforeTheConsumerStops` (Listener hält Batch 1 fest, Batch 2 liegt in der Queue, Shutdown beginnt; vorher rot: 1 statt 2 geprüfte Batches, danach grün). `ConsumerJavaTest` 34/34, `FinderTest` 21/21.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `e1b05c9`
- **Nachtrag (Branch-Review 2026-10-20):** Der Test erzwang den Weg nicht, auf dem er rot wird: Nahm schon die nicht blockierende Leerung des Workers (`consumeKeys(ByteBuffer)` am Zyklusanfang) Batch 1, holte dieselbe Schleife nach der Freigabe auch Batch 2, und der Test war ohne den Fix grün. Jetzt setzt er `queuePollTimeoutMillis = 30_000` und reiht Batch 1 erst ein, wenn ein Thread mit `ConsumerJava.consumeOneCycle` im Stack im Zustand `TIMED_WAITING` in `LinkedBlockingQueue.poll` steht (`awaitWorkerParkedInTheTimedPoll`; das nicht blockierende `poll()` parkt nie zeitgesteuert, ein Leerlaufzyklus muss also nicht ablaufen). Zusätzlich hält der Listener über `StackWalker` fest, wer `processBatch` für Batch 1 rief, und der Test verlangt `consumeOneCycle`. Gegenprobe: ohne die Leerung nach der Schleife dreimal rot („Expected: is <2> but: was <1>“); Batch 1 vor `startConsumer()` eingereiht (also über die Leerung) rot („Expected: is "consumeOneCycle" but: was "consumeKeys"“); unverändert `ConsumerJavaTest` 43/43 grün.

<a id="f19"></a>
### F19 – BAF: ZeroMQ-Ergebnis-Broadcast aus mehreren Consumer-Threads korrumpiert den Socket → Consumer-Threads hängen für immer
- **Zeit:** 05:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/ZmqResultBroadcaster.java:183` (`send`, ebenso `close`)
- **Schwere:** hoch
- **Beschreibung:** `ConsumerJava` ruft seine `ResultListener` aus allen `consumerJava.threads` Worker-Threads auf; ein ZeroMQ-Socket ist nicht thread-sicher. Mit `keyProducerJavaZmq.broadcastResults = true` und mehr als einem Consumer-Thread senden mehrere Threads gleichzeitig auf demselben `PUB`-Socket: jeromq wirft intern `IndexOutOfBoundsException` (wird von `send` geschluckt und nur geloggt), danach drehen die sendenden Threads endlos – die Consumer prüfen keine Schlüssel mehr, der Scan steht still, ohne Fehler. Ebenso lief `close()` (Shutdown-Pfad, `Finder.interrupt`) unsynchronisiert parallel zu Sends.
- **Fix:** `send` und `close` teilen ein privates Lock; ein Send nach `close` wird übersprungen, `close` ist idempotent. WebSocket- und Socket-Broadcaster geprüft: Java-WebSocket ist thread-sicher, `SocketResultBroadcaster` schreibt jede Nachricht in einem einzigen `write`, das `NioSocketImpl` unter seinem Write-Lock ausführt → keine Verzahnung.
- **Reproduktion:** `ZmqResultBroadcasterTest#onBatchChecked_calledFromSeveralConsumerThreads_everyCallReturns` (8 Threads × 200 Aufrufe; vorher hängt er, nachher 4/4 grün in 1,5 s). Zusätzliche Probe `/home/user/BUGHUNT-2026-10-17-F19-probe.java`: vorher 3 von 3 Läufen Timeout mit 1 100–1 500 `IndexOutOfBoundsException`, nachher 1600/1600 Nachrichten empfangen.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `1922809`

<a id="f20"></a>
### F20 – BAF: Ein Ergebnis-Client, der verbindet und nie liest, friert über `SocketResultBroadcaster` den ganzen Scan ein
- **Zeit:** 05:31 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/SocketResultBroadcaster.java:215` (`broadcast`/`writeTo`)
- **Schwere:** hoch
- **Beschreibung:** `onBatchChecked` schrieb auf den Consumer-Worker-Threads synchron per blockierendem `OutputStream.write` an jeden Client. Ein blockierender Socket-Write scheitert nicht, wenn der Peer nicht liest – er wartet ewig, sobald die TCP-Puffer voll sind. Ein einziger Client, der sich auf `resultPort` verbindet (der `ServerSocket` lauscht auf allen Interfaces) und nichts liest, hält damit nach einigen MB alle Consumer-Threads an – der Scan steht, ohne Fehler. Das Klassen-Javadoc behauptete das Gegenteil („will eventually fill its buffer and be dropped on the resulting error rather than blocking forever“).
- **Fix:** pro Client eine begrenzte Queue (4096 Nachrichten) und ein eigener Writer (Executor); `broadcast` legt nur ab, ein Client, dessen Queue voll ist, wird getrennt. Greeting wird vor der Registrierung eingereiht (bleibt die erste Zeile). `close()` beendet auch die Writer. Javadoc korrigiert.
- **Reproduktion:** `SocketResultBroadcasterTest#onBatchChecked_clientConnectedButNeverReading_doesNotBlockTheCaller` (vorher `TimeoutException` nach 15 s, nachher 6/6 grün in 1,1 s); `BitcoinAddressFinderArchitectureTest` 17/17, `FinderTest` 21/21 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `dbdefe4`

<a id="f21"></a>
### F21 – BAF: `WebSocketEndpoint` lässt einen Nicht-Daemon-Thread zurück → JVM beendet sich nach einem Find-Lauf mit WebSocket-Key-Producer nie
- **Zeit:** 05:41 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/WebSocketEndpoint.java:176` (`start`)
- **Schwere:** mittel
- **Beschreibung:** `start()` übergab `WebSocketServer.start()` – das sofort zurückkehrt und eigene Threads startet – an einen frisch erzeugten `Executors.newSingleThreadExecutor()`, der nie heruntergefahren wurde. Dessen untätiger Worker (`pool-N-thread-1`) ist kein Daemon und überlebt `stop()`. Da `Main` kein `System.exit` aufruft, hält er die JVM am Leben: ein Lauf, dessen Producer enden (z. B. `timeoutMillis` > 0 → `NoMoreSecretsAvailableException`), kehrt aus `main` zurück, der Prozess aber nie. Probe: nach `start(); stop();` bleibt `[pool-1-thread-1]` übrig, die JVM wird erst vom `timeout` beendet (Exit 124).
- **Fix:** Executor direkt nach dem `submit` per `shutdown()` beenden (die Startaufgabe läuft noch zu Ende). **Überholt durch `48616fd`:** `WebSocketEndpoint.start()` ruft `localServer.start()` seither direkt auf, es gibt keinen Start-Executor mehr; die Wirkung (kein Nicht-Daemon-Thread hält die JVM) ist dieselbe.
- **Reproduktion:** neuer `WebSocketEndpointTest#stop_afterStart_leavesNoNonDaemonThreadBehind` (vorher rot: `<[Thread[#31,pool-2-thread-1,5,main]]>`, nachher grün); `KeyProducerJavaWebSocketTest` 5/5 und `WebSocketResultBroadcasterTest` 4/4 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `1baed42`

<a id="f22"></a>
### F22 – BAF: `KeyProducerJavaSocket` – `connectRetryCount` ist ein Lebenszeit-Budget → Producer stirbt still nach N Trennungen
- **Zeit:** 05:46 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaSocket.java:108` (Reader-Schleife in `start`)
- **Schwere:** mittel
- **Beschreibung:** Jede verlorene Verbindung (Peer trennt, `End of stream`, oder `SO_TIMEOUT` von 3 s auf einem ruhenden Feed) zählte `attempts` hoch; zurückgesetzt wurde der Zähler nie – auch nicht nach einer erfolgreichen Verbindung. Nach insgesamt `connectRetryCount` (Default 5) Trennungen über die ganze Laufzeit endet der Reader endgültig (im Server-Modus wird auch der `ServerSocket` geschlossen), die `IllegalStateException` landet ungelesen im `Future`, und der Producer liefert still keine Secrets mehr (`createSecrets` → `NoMoreSecretsAvailableException`). Ein Feed, der z. B. fünfmal ein paar Sekunden pausiert, legt den Scan damit dauerhaft still.
- **Fix:** `attempts = 0` sobald eine Verbindung steht; `connectRetryCount` begrenzt damit wie dokumentiert die Versuche *pro* Verbindungsaufbau.
- **Reproduktion:** `KeyProducerJavaSocketTest#createSecrets_peerReconnectsMoreOftenThanTheRetryCount_keepsReading` (4 Verbindungen à 1 Secret bei `connectRetryCount = 2`; vorher `NoMoreSecretsAvailableException` beim dritten Secret, nachher grün; Klasse 18/18).
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `fec089b`

<a id="f23"></a>
### F23 – BAF: Adresszeilen mit führendem Leerraum werden beim Import als „address is empty“ verworfen
- **Zeit:** 05:51 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java:118` (`fromLine`, Ursache in `SeparatorFormat.split`)
- **Schwere:** niedrig
- **Beschreibung:** `SeparatorFormat` trennt auch an Leerzeichen und Tab. Eine eingerückte Zeile (`" 1Abc…,123"`, `"\t1Abc…"`) ergibt daher ein leeres erstes Token; `fromLine` trimmt zwar die Adressspalte, aber erst nachdem sie schon leer ist, und wirft `REASON_EMPTY`. Beim Import (`AddressFilesToLMDB`) werden solche Zeilen nur als „unsupported“ gezählt – ausgerichtete oder von Hand bearbeitete Adresslisten verlieren still Einträge.
- **Fix:** `fromLine` trimmt die ganze Zeile vor dem Aufteilen.
- **Reproduktion:** `AddressTxtLineTest#fromLine_addressWithLeadingWhitespace_returnHash160AndAmount` (Leerzeichen, Tab, zwei Leerzeichen; vorher `AddressFormatNotAcceptedException: address is empty`, nachher grün). `AddressTxtLineTest` 291/291, `SeparatorFormatTest` 83/83, `AddressFilesToLMDBTest` 24/24 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `33c2210`

<a id="f27"></a>
### F27 – BAF: LMDB-Map wird bei vollem Speicher nur einmal vergrößert → Batch-Import bricht mit `MapFullException` ab
- **Zeit:** 06:16 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/persistence/lmdb/LMDBPersistence.java:556` (`putNewAmounts`/`putNewAmountWithAutoIncrease`)
- **Schwere:** mittel
- **Beschreibung:** Bei `MapFullException` wurde die Map um genau einen Schritt (`increaseSizeInMiB`) vergrößert und noch einmal geschrieben; reichte das nicht, flog die Ausnahme. `AddressFilesToLMDB` schreibt aber 32 768 zufällige Schlüssel pro Transaktion (`writeBatchSize`), die je eine Seite verschmutzen – das kann mehrere MiB auf einmal brauchen, und das mitgelieferte `examples/config_AddressFilesToLMDB.json` setzt `increaseSizeInMiB: 1`. Ein echter (großer) Import bricht damit ab; der CI-Smoke merkt es nicht, weil die Beispieldateien in die initialen 16 MiB passen. Der alte Kommentar nannte das ein Konfigurationsproblem – mit Batch-Schreiben ist es aber die Standardkonfiguration.
- **Fix:** beide Schreibpfade vergrößern die Map schrittweise, bis die Transaktion passt (`growMapOrRethrow`); `increaseSizeInMiB = 0` oder `increaseMapAutomatically = false` werfen wie bisher. Javadoc von `CLMDBConfigurationWrite.increaseSizeInMiB` angepasst.
- **Reproduktion:** `LMDBPersistenceTest#putNewAmounts_batchLargerThanOneIncreaseStep_mapGrowsUntilTheBatchFits` (128 Ki Einträge in einer Transaktion bei 1 MiB Schrittweite; vorher `MapFullException: Environment mapsize reached`, nachher grün). `LMDBPersistenceTest` 50/50 (inkl. der bestehenden Tests zur genauen Anzahl der Vergrößerungen), `AddressFilesToLMDBTest` 24/24 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `8260810`
- **Nachtrag:** die private Javadoc von `putNewAmountWithAutoIncrease` wurde dabei übersehen, siehe [F142](#f142).

<a id="f31"></a>
### F31 – BAF: `LMDBCompact` löscht die Quell-Datenbank, wenn das Ziel die Quelle unter anderer Schreibweise ist
- **Zeit:** 06:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBCompact.java:62`
- **Schwere:** hoch (Datenverlust der importierten Adressdatenbank)
- **Beschreibung:** Die Schutzprüfung „targetDirectory muss sich vom lmdbDirectory unterscheiden" verglich nur `getAbsolutePath()`. `<quelle>/.`, `<quelle>/../<quelle>`, `./lmdb` gegen `lmdb` oder ein Symlink auf die Quelle bestehen die Prüfung; danach löscht `run()` „eine frühere Kompaktierung" – also `data.mdb` **der Quelle** – noch bevor die Quelle geöffnet wird. Fix: Vergleich der kanonischen Pfade (`getCanonicalFile`). Nebenbefund ohne Fix: Das Feld `shouldRun` wird in `run()` nie gelesen, obwohl der Kommentar sagt, es verhindere einen noch nicht gestarteten Lauf (`interrupt()` ist damit wirkungslos).
- **Reproduktion:** `LMDBCompactTest#compactLMDB_targetIsSourceSpelledDifferently_throwsAndKeepsTheSourceDatabase` (ohne Fix: Quelle gelöscht, `LmdbNativeException`; mit Fix 3/3 grün)
- **Status:** gefixt
- **Commit:** 301fe73
- **Nachtrag (Branch-Review 2026-10-20):** Den Symlink-Fall, nach dem der kanonische Vergleich gewählt wurde, deckte kein Test ab; `<quelle>/.` fängt auch ein Vergleich über `toAbsolutePath().normalize()`, der keinem Link folgt. Neu `LMDBCompactTest#compactLMDB_targetIsASymlinkToTheSource_throwsAndKeepsTheSourceDatabase`: Ziel ist ein symbolischer Link auf die Quelle (kann das Dateisystem keine Links anlegen, bricht der Test über `Assumptions.abort` ab statt still grün zu sein); verlangt `IllegalArgumentException` mit „must differ“, die Quelle `data.mdb` unverändert und den Link weiter auf die Quelle zeigend. Typ und Meldung pinnen die kanonische Prüfung selbst: seit der Prüfung auf ein leeres Ziel stünde ohne sie eine `IllegalStateException` an `lock.mdb` statt eines Datenverlusts. Gegenprobe: `canonical()` als `toAbsolutePath().normalize()` rot („Unexpected exception type thrown, expected: <java.lang.IllegalArgumentException> but was: <java.lang.IllegalStateException>“), der `/.`-Test bleibt dabei grün; unverändert `LMDBCompactTest` 7/7 grün.

<a id="f32"></a>
### F32 – BAF: `LMDBCompact.interrupt()` ist wirkungslos – ein vor dem Start unterbrochener Lauf löscht und kopiert trotzdem
- **Zeit:** 2026-10-17 10:08 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBCompact.java:57`
- **Schwere:** niedrig
- **Beschreibung:** `interrupt()` setzte `shouldRun` auf `false`, aber `run()` las das Feld nie, obwohl der Kommentar sagt, es verhindere einen noch nicht gestarteten Lauf. Ein vor dem Start unterbrochener Compact-Befehl legte trotzdem das Zielverzeichnis an, löschte eine frühere Kompaktierung (`data.mdb` im Ziel) und kopierte die ganze Datenbank. Nebenbefund aus F31. Fix: `run()` prüft `shouldRun` zuerst und kehrt ohne Schreibzugriff zurück.
- **Reproduktion:** `LMDBCompactTest#compactLMDB_interruptedBeforeRun_writesNothing`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <false> but: was <true>“: Zielverzeichnis angelegt), mit Fix grün 4/4 (skipped 0), Gegenprobe per `git stash` rot, nach `stash pop` wieder grün 4/4.
- **Status:** gefixt
- **Commit:** BAF `ab52958`

<a id="f33"></a>
### F33 – BAF: `OpenClLibraryLoader.ensureLoaded()` initialisiert OpenCL bei gleichzeitigem Erstaufruf zweimal
- **Zeit:** 2026-10-17 10:25 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/binding/OpenClLibraryLoader.java:82`
- **Schwere:** niedrig
- **Beschreibung:** `ensureLoaded()` war ein Check-then-act ohne Synchronisation, `loaded` nicht `volatile`. Zwei Threads, die gleichzeitig den ersten OpenCL-Aufruf machen, riefen beide `libraryInitializer.create()`; LWJGLs zweites `CL.create()` wirft eine `IllegalStateException`, die hier niemand fängt. Im Programm selbst werden die OpenCL-Producer nacheinander initialisiert, daher niedrig. Fix: privates `loadLock` um den Rumpf, `loaded` `volatile` (für `isLoaded()`).
- **Reproduktion:** `OpenClLibraryLoaderTest#ensureLoaded_calledFromTwoThreadsAtOnce_initializesOnlyOnce` (der Fake hält den ersten `create()` fest, bis der zweite Thread `ensureLoaded()` betreten hat)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <1> but: was <2>“), mit Fix grün 9/9 (skipped 0), Gegenprobe per `git stash` rot, nach `stash pop` grün 9/9.
- **Status:** gefixt
- **Commit:** BAF `b50c531`

<a id="f34"></a>
### F34 – BAF: `Finder.interrupt()` aus Shutdown-Hook und Main-Thread gleichzeitig → `ConcurrentModificationException` im Shutdown
- **Zeit:** 2026-10-17 10:52 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java:724` (`interrupt`), Aufrufer `cli/Main` (Shutdown-Hook) und `Finder.shutdownAndAwaitTermination`
- **Schwere:** mittel
- **Beschreibung:** Bei Ctrl-C ruft der Shutdown-Hook `Main.interrupt()` → `finder.interrupt()`. Dadurch enden die Producer, `awaitTermination` im Main-Thread kehrt zurück, und `shutdownAndAwaitTermination()` ruft **gleichzeitig** ein zweites `interrupt()`. Beide Aufrufe liefen über dieselben `ArrayList`s (`zmqBroadcasters`, `socketBroadcasters`) und leerten sie; der eine `clear()`te, während der andere noch iterierte → `ConcurrentModificationException` im Shutdown, die übrigen Broadcaster und Producer wurden in diesem Thread nicht mehr freigegeben. (Schlüssel-Producer und Producer laufen über Kopien, `ProducerReleaser` gibt jeden genau einmal frei – die Broadcaster-Listen waren die Lücke.) Fix: privates `interruptLock` serialisiert `interrupt()`; der zweite Aufruf findet die Listen leer.
- **Reproduktion:** `FinderTest#interrupt_calledFromTwoThreadsAtOnce_neitherFails` (ein ZMQ-Broadcaster auf `inproc://`, dessen erstes `close()` festgehalten wird, per Reflection in die private Liste gelegt)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is null but: was <java.util.ConcurrentModificationException>“), mit Fix grün, `FinderTest` 22/22, `BitcoinAddressFinderArchitectureTest` 17/17; Gegenprobe per `git stash` rot, nach `stash pop` grün. `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `514f564`
- **Nachtrag (Branch-Review 2026-10-20):** Seit F206 durchläuft `interrupt()` die Broadcaster-Listen nicht mehr, das tut `closeBroadcasters()` (unter demselben `interruptLock`). Der Test heißt deshalb `FinderTest#teardown_calledFromTwoThreadsAtOnce_neitherFails` und lässt zwei Threads `abortStart()` rufen (jeweils `interrupt()`, dann `closeBroadcasters()`); der festgehaltene `close()` liegt weiter im ersten Aufruf.

<a id="f35"></a>
### F35 – BAF: Javadoc begründet den GC vor dem GPU-Filter-Upload mit `CL_MEM_COPY_HOST_PTR`, das der Code nicht mehr nutzt
- **Zeit:** 2026-10-17 16:24 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/GcTrigger.java:15` (Klassen-Javadoc „Why this exists in the upload path“), `opencl/OpenCLContext.java:466` (`reclaimHeapBeforeUpload`), `opencl/OpenCLContext.java:683` (`uploadGpuFilter`: „Two CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR buffers“)
- **Schwere:** niedrig (nur Dokumentation)
- **Beschreibung:** Die drei Javadocs begründen den GC vor dem Filter-Upload damit, dass der Treiber für `CL_MEM_COPY_HOST_PTR` einen Host-Staging-Bereich anheften muss. Der Code nutzt `CL_MEM_COPY_HOST_PTR` nicht mehr: Die Puffer werden mit `ClBufferFlags.READ_ONLY` angelegt und über `ClApi.writeBufferChunked` in Scheiben durch einen kleinen wiederverwendeten Staging-Puffer beschrieben (so auch CLAUDE.md, Abschnitt „OpenCL / GPU Code“). Die Doku beschreibt damit einen Mechanismus, den es nicht mehr gibt; ob der GC mit dem gestückelten Upload überhaupt noch nötig ist, ist offen (er ist harmlos, kostet einmalig Millisekunden). Verhalten korrekt: `reclaimHeapBeforeUpload()` läuft in `allocateFilterBuffers` unmittelbar vor `createBuffer`.
- **Weitere Doku-Abweichung derselben Art (2026-10-17):** `persistence/AddressLookup.java:25` zeigt im Beispiel `consumerJava.useLookup(lookup)`; diese Methode gibt es nicht, sie heißt `ConsumerJava.initWithLookup(AddressPresence)`.
- **Historie:** Der GC kam mit `e3f3e72` (2026-07-20, RDNA3: `clCreateBuffer` für den 3,1-GB-Fuse-16-Puffer scheiterte bei 33,9 GiB lebendem Heap, nach einer Collection nicht mehr), verifiziert in `3a2cfba` (gleicher Befehl gestern rot, mit Fix grün). Damals JOCL mit `CL_MEM_COPY_HOST_PTR`. `5479507` (2026-08-02, LWJGL) stellte auf `CL_MEM_READ_ONLY` plus `writeBufferChunked` um und ließ die drei Javadocs stehen. Das `AddressLookup`-Beispiel stammt aus `f9af746` (2026-05-29) mit einem geplanten `useLookup`, das nie gebaut wurde; die Kette baut seit `ba51113` `ConsumerJava.initLMDB()`, und „SortedArray“ wurde in `5b4aa76` entfernt.
- **Fix:** Entscheidung des Nutzers (Variante B). `GcTrigger` beschreibt die Messung, dass sie vom alten Upload-Weg stammt und auf dem gestückelten Upload nicht nachgemessen ist; `reclaimHeapBeforeUpload` ohne „few milliseconds“ und `CL_MEM_COPY_HOST_PTR`; `uploadGpuFilter` nennt `CL_MEM_READ_ONLY` + `writeBufferChunked`; `AddressLookup` zeigt eine echte Kette und die tatsächliche Verdrahtung. Neuer `TODO.md`-Eintrag „Re-measure the heap reclaim before the GPU filter upload on the chunked LWJGL upload“ (AMD-Karte, no-op-`GcTrigger` gegen `GcTrigger.SYSTEM`, Dauer der Collection) – CI-/Hardware-Bedarf.
- **Reproduktion:** keine – reine Dokumentationsabweichung; `OpenCLContextGpuFilterWidthTest#reclaimHeapBeforeUpload_invokesTheTrigger` prüft weiterhin, dass die Collection läuft.
- **Rot-Grün-Nachweis:** – (Doku-Korrektur, Freigabe des Nutzers für Doku ohne roten Test)
- **Überholt:** `51fcfb3` (Messauftrag B6) hat `GcTrigger` und den `System.gc()` vor dem Upload entfernt, nachdem die Nachmessung auf der RX 7900 XTX ohne ihn grün war; die hier beschriebene Doku und der Test `OpenCLContextGpuFilterWidthTest#reclaimHeapBeforeUpload_invokesTheTrigger` gibt es deshalb nicht mehr.
- **Status:** gefixt (Doku)
- **Commit:** c7c2aaf

<a id="f40"></a>
### F40 – BAF: Secrets-Datei im falschen Format hält jede Zeile in `ReadStatistic.errors` → OutOfMemory
- **Zeit:** 2026-10-17 17:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AbstractPlaintextFile.java:87` (`readFile`), `statistics/ReadStatistic.java` (`errors`), `producer/ProducerJavaSecretsFiles.java` (`logProgress`)
- **Schwere:** mittel
- **Beschreibung:** Jede Zeile, deren Verarbeitung scheiterte, wurde in `ReadStatistic.errors` gespeichert, ohne Grenze. Der Secrets-Datei-Producer liest davon nur `errors.size()`. Eine Secrets-Datei im falschen Format (etwa `secretFormat` passt nicht zum Inhalt, oder eine riesige Wortliste) scheitert an jeder Zeile, und der Producer hielt damit jede Zeile der Datei im Speicher, bis zum OutOfMemoryError – dieselbe Klasse wie F14. Die gespeicherten Zeilen sind dort zudem Secrets, und `ReadStatistic.toString()` (Lombok) gibt sie vollständig aus.
- **Fix:** `errors` behält nur die ersten `ReadStatistic.MAX_STORED_ERRORS` (100) Zeilen zur Diagnose, das neue Feld `errorCount` zählt alle; `ProducerJavaSecretsFiles` protokolliert `errorCount`. Das Feld wurde zuerst verhaltensneutral eingezogen (toString-Test angepasst), dann der rote Test geschrieben.
- **Reproduktion:** `AbstractPlaintextFileTest#readFile_moreFailingLinesThanKept_keepsTheFirstLinesAndCountsAll`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: a collection with size <100> but: collection size was <105>“), mit Fix grün 11/11 (skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `ReadStatisticTest` 15/15, `SecretsFileTest` 10/10, `AddressFileTest` 11/11, `ProducerJavaSecretsFilesTest` 11/11, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün. `AddressFilesToLMDB` führt eine eigene Fehlerliste, die es im Abschluss Zeile für Zeile ausgibt; die ist ebenfalls unbegrenzt, wird aber tatsächlich benutzt und bleibt unverändert.
- **Musterdurchsicht nach F40 (2026-10-17):** alle Instanz-Collections in BAF, java-llama.cpp (Java) und streambuffer, die pro Element wachsen. Begrenzt bzw. unkritisch: `SlidingWindowRate.samples` (auf das Zeitfenster gekürzt), `OpenClTask.hostBufferPool` (enthält nur zurückgegebene Puffer, höchstens so viele wie gleichzeitig in Gebrauch), Producer-/Broadcaster-/Interruptable-Listen (durch die Konfiguration begrenzt), Tool-Maps pro Stream. Die unbegrenzte Fehlerliste von `AddressFilesToLMDB` ist kein realistisches Risiko: Formatfehler landen als „unsupported“ nur in einem Zähler, ein nicht numerischer Betrag fällt auf den Standardwert zurück, der `RuntimeException`-Pfad fängt nur echte Einzelfehler.
- **Status:** gefixt
- **Commit:** BAF `03409ac`

<a id="f41"></a>
### F41 – BAF: abgeschnittene Meldung, wenn der inkrementelle Producer schon hinter dem Bereichsende steht
- **Zeit:** 2026-10-17 17:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaIncremental.java:42` (`createSecrets`)
- **Schwere:** niedrig
- **Beschreibung:** Steht `currentValue` bereits hinter `endPrivateKey` (Bereich erschöpft oder Start > Ende konfiguriert), wirft `createSecrets` `NoMoreSecretsAvailableException` mit der Meldung `"<wert> exceeds "` – der Satz bricht ab, das Bereichsende fehlt. Die zweite Prüfung in der Schleife derselben Methode nennt es („exceeds end private key …“). Seit der Einführung der Datei so (`git log -L`).
- **Fix:** Die Meldung nennt das Ende wie die Schleifenprüfung: `currentValue + " exceeds end private key " + endPrivateKey`.
- **Reproduktion:** `KeyProducerJavaIncrementalTest#createSecrets_startExceedsEnd_messageNamesTheEndPrivateKey`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is "16 exceeds end private key 5" but: was "16 exceeds "“), mit Fix grün 10/10 (skipped 0); Gegenprobe per `git stash` rot (10 Tests, 1 Failure), nach `stash pop` grün. `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `6021b2a`

<a id="f42"></a>
### F42 – BAF: UTF-8-BOM am Dateianfang bleibt in der ersten Zeile – erstes Brainwallet-Secret falsch, erste Adresse unlesbar
- **Zeit:** 2026-10-17 18:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AbstractPlaintextFile.java:73` (`readFile`)
- **Schwere:** mittel
- **Beschreibung:** `readFile` dekodiert jede Zeile als UTF-8, entfernt aber eine Byte-Order-Mark (EF BB BF) am Dateianfang nicht; Windows-Editoren schreiben UTF-8 oft so. Die erste Zeile beginnt dann mit U+FEFF. Bei `secretFormat` `STRING_SHA256` wird die erste Passphrase mitsamt BOM gehasht: ein falsches Secret, ohne Fehler und ohne Zählung – die gemeinte Brainwallet wird nie geprüft. Bei `BIG_INTEGER`, `SHA256` und `DUMPED_PRIVATE_KEY` scheitert die erste Zeile, bei `AddressFile` (nur in Tests benutzt) ist die erste Adresse „unsupported“; der produktive Import `AddressFilesToLMDB` liest mit einem eigenen Reader und hatte denselben Fehler, siehe F43.
- **Fix:** Die erste Zeile verliert ein führendes U+FEFF, bevor sie an `processLine` geht; alle weiteren Zeilen bleiben unverändert.
- **Reproduktion:** `SecretsFileTest#readFile_stringDoSha256FormatWithUtf8ByteOrderMark_firstLineIsHashedWithoutTheMark`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <72155939…63272> but: was <26164307…44373>“ – SHA256 von „U+FEFF test“ statt „test“), mit Fix grün 11/11 (skipped 0); Gegenprobe per `git stash` rot (1 Failure), nach `stash pop` grün. `AbstractPlaintextFileTest` 11/11, `AddressFileTest` 11/11, `ProducerJavaSecretsFilesTest` 11/11, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `22ba361`

<a id="f43"></a>
### F43 – BAF: Adressimport (`AddressFilesToLMDB`) verliert die erste Adresse einer Datei mit UTF-8-BOM
- **Zeit:** 2026-10-17 18:25 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/AddressFilesToLMDB.java:276` (`readFileLines`)
- **Schwere:** mittel
- **Beschreibung:** Musterdurchsicht nach F42. Der produktive Import liest Adressdateien nicht über `AbstractPlaintextFile` (dessen `AddressFile` nur in Tests benutzt wird), sondern mit einem eigenen `BufferedReader`. `InputStreamReader` mit UTF-8 entfernt eine Byte-Order-Mark nicht, die erste Zeile beginnt mit U+FEFF, wird als Format nicht erkannt und landet nur im „unsupported“-Zähler. Die erste Adresse einer mit BOM gespeicherten Datei fehlt also in der Datenbank – ein Treffer auf genau diese Adresse würde nie gemeldet.
- **Fix:** Die erste Zeile jeder Datei verliert ein führendes U+FEFF, bevor sie in die Zeilen-Queue geht (wie F42). Die Fortschrittszählung `charsRead` bleibt auf der ungekürzten Zeile.
- **Reproduktion:** `AddressFilesToLMDBTest#addressFilesToLMDB_fileWithUtf8ByteOrderMark_importsTheFirstAddress`
- **Rot-Grün-Nachweis:** ohne Fix rot („the first address after the byte order mark was not imported – Expected: is <true> but: was <false>“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. Ganze Klasse `AddressFilesToLMDBTest` 25/25, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `f5ba9b2`

<a id="f44"></a>
### F44 – BAF: JSON-Konfiguration mit UTF-8-BOM lässt sich nicht laden
- **Zeit:** 2026-10-17 18:35 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/cli/Main.java:119` (`readString`)
- **Schwere:** niedrig
- **Beschreibung:** Musterdurchsicht nach F42/F43. `readString` gibt den Dateiinhalt unverändert an Jackson; `ObjectMapper` lehnt ein führendes U+FEFF ab, `YAMLMapper` überspringt es. Eine im Windows-Editor gespeicherte `.json`/`.js`-Konfiguration bricht den Start deshalb mit `JsonParseException` ab. Laut und mit klarer Meldung, aber ohne Grund.
- **Fix:** `readString` entfernt ein führendes U+FEFF; Javadoc ergänzt.
- **Reproduktion:** `MainTest#loadConfiguration_jsonFileWithUtf8ByteOrderMark_returnsExpectedConfiguration`
- **Rot-Grün-Nachweis:** ohne Fix rot („JsonParse Unexpected character ('?' (code 65279 / 0xfeff)): expected a valid value“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `MainTest` 18 Tests (6 OpenCL-Tests überspringen sich ohne Gerät, wie zuvor), `ConfigFixturesParseTest` 3/3, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `bde669f`

<a id="f45"></a>
### F45 – BAF: die Gruppenordnung n gilt als gültiger privater Schlüssel – der CPU-Producer stürzt ab, wenn ein Raster n erreicht
- **Zeit:** 2026-10-17 18:55 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/util/PrivateKeyValidator.java:88` (`isOutsidePrivateKeyRange`), `constants/Secp256k1Constants.java:76` (Javadoc), `producer/ProducerJava.java:89` (`createGrid`)
- **Schwere:** mittel
- **Beschreibung:** `MAX_PRIVATE_KEY` ist die Gruppenordnung n (`…D0364141`), seine Javadoc nannte sie aber den „maximum valid private key“, und `isOutsidePrivateKeyRange` ließ n durch (`compareTo(n) > 0`). Gültige Schlüssel sind 1 bis n−1 (SEC 2; das Bitcoin-Wiki, das die Javadoc von `Producer.produceKeys` wiedergibt, nennt als Obergrenze `…4140` = n−1): n·G ist der Punkt im Unendlichen. bitcoinj liefert dafür einen 1-Byte-Public-Key, und `PublicKeyBytes.fromPrivate(n)` wirft `ArrayIndexOutOfBoundsException`. `ProducerJava.createGrid` prüft jeden Rasterschlüssel mit genau diesem Validator; ein Raster, das n erreicht, beendet also den Producer mit einer Exception – zum Beispiel der inkrementelle Producer, dessen voreingestelltes Bereichsende `MAX_PRIVATE_KEY_HEX` ist, wenn er das Ende erreicht. `AbstractProducer.replaceInvalidPrivateKeys` und der Consumer ließen n ebenso durch.
- **Fix:** Die Obergrenze ist exklusiv (`compareTo(n) >= 0` → außerhalb). Javadoc von `MAX_PRIVATE_KEY`/`MAX_PRIVATE_KEY_HEX`, `PrivateKeyValidator.isOutsidePrivateKeyRange` und `Producer.produceKeys` korrigiert. Zwei Tests, die n ausdrücklich als gültig festschrieben (`isOutsidePrivateKeyRange_maxPrivateKey_returnsFalse`, `replaceInvalidPrivateKeys_allValidKeys_keepsOriginalValues`), prüfen jetzt n−1 als gültig und n als ungültig. Der Konstantenname bleibt.
- **Reproduktion:** `ProducerJavaTest#createGrid_gridReachesTheGroupOrder_marksItInvalidAndKeepsTheKeyBelow` (Raster n−1, n, n+1, n+2 mit `batchSizeInBits` 2), dazu `PrivateKeyValidatorTest#isOutsidePrivateKeyRange_groupOrder_returnsTrue`.
- **Rot-Grün-Nachweis:** ohne Fix rot (`java.lang.ArrayIndexOutOfBoundsException: Index 64 out of bounds for length 1` aus `PublicKeyBytes.fromPrivate`), mit Fix grün; Gegenprobe per `git stash` des Validators rot mit derselben Exception, nach `stash pop` grün. Breit: `PrivateKeyValidatorTest` 22/22, `ProducerJavaTest` 9/9, `AbstractProducerTest` 20/20, `PublicKeyBytesTest` 32/32, `KeyUtilityTest` 58 (1 seit langem `@Disabled`), `ConsumerJavaTest` 35 (8 LMDB/OpenCL-abhängig übersprungen), `KeyProducerJavaIncrementalTest` 10/10, `PrivateKeyTooLargeExceptionTest` 11/11, `CKeyProducerJavaIncrementalTest` 9/9, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `fd44de7`

<a id="f46"></a>
### F46 – BAF: abgebrochener LMDB-Export meldet „writeAllAmounts done“
- **Zeit:** 2026-10-17 19:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBToAddressFile.java:58` (`run`)
- **Schwere:** niedrig
- **Beschreibung:** `LMDBPersistence.writeAllAmountsToAddressFile` kehrt bei gelöschtem `shouldRun` (über `interrupt()`, etwa durch den Shutdown-Hook) still zurück und lässt eine angefangene Datei zurück. `LMDBToAddressFile.run` meldete danach trotzdem „writeAllAmounts done“; wer die Datei später wieder importiert, verliert ohne Hinweis alle Adressen hinter dem Abbruch.
- **Fix:** Nach dem Export unterscheidet `run` zwischen fertig („writeAllAmounts done“, INFO) und abgebrochen (WARN „writeAllAmounts interrupted; <Datei> is incomplete“). Ein `interrupt()`, das erst nach der letzten Zeile ankommt, meldet ebenfalls „incomplete“ – konservativ und ohne Änderung des `Persistence`-Interfaces.
- **Reproduktion:** `LMDBToAddressFileTest#run_interruptedBeforeTheExport_doesNotReportTheExportAsDone`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: not a collection containing "writeAllAmounts done" but: was <[writeAllAmounts ..., writeAllAmounts done]>“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `LMDBToAddressFileTest` 13/13, `MainTest` 18 (6 OpenCL übersprungen), `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `a940cee`

<a id="f47"></a>
### F47 – BAF: abgebrochener Adressimport meldet „... import done.“ und „finished“ für halb gelesene Dateien
- **Zeit:** 2026-10-17 19:25 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/AddressFilesToLMDB.java:165` (`run`), `:251` (`readAllFiles`)
- **Schwere:** niedrig
- **Beschreibung:** Musterdurchsicht nach F46. Nach `interrupt()` endet `readFileLines` mitten in der Datei; `readAllFiles` meldete die Datei trotzdem als „finished (n/N files)“, und `run` schloss mit „... import done.“ – obwohl die LMDB nur einen Teil der Adressen enthält. Ein späterer Find-Lauf gegen diese Datenbank übersieht die fehlenden Adressen ohne Hinweis.
- **Fix:** `readAllFiles` meldet einen beim Lesen unterbrochenen Lauf als „interrupted while reading <Datei>“ und hört auf; `run` meldet bei gelöschtem Flag WARN „... import interrupted; the LMDB in <Verzeichnis> is incomplete.“ statt „... import done.“.
- **Reproduktion:** `AddressFilesToLMDBTest#addressFilesToLMDB_interruptedBeforeTheImport_doesNotReportTheImportAsDone`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: not a collection containing "... import done." but: was <[…, Import summary: 0 written, 0 parsed, 0 unsupported, 0 error(s)., ... import done.]>“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `AddressFilesToLMDBTest` 26/26, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `71b202b`
- **Nachtrag (Branch-Review 2026-10-20):** Die `readAllFiles`-Hälfte des Fixes hatte keinen Test: `#addressFilesToLMDB_interruptedBeforeTheImport_doesNotReportTheImportAsDone` ruft `interrupt()` vor `run()`, die Schleife endet am Kopf, bevor „process <Datei>“ geloggt und `readFileLines` betreten wird, und `hasItem(containsString("interrupted"))` erfüllte allein die WARN aus `run`. Neu `#addressFilesToLMDB_interruptedWhileReading_reportsTheFileAsInterruptedNotFinished`: ein Logback-`AppenderBase` am Logger von `AddressFilesToLMDB` ruft `importer.interrupt()` bei der Meldung „process “, die der Leser unmittelbar vor `readFileLines` loggt; `readFileLines` prüft `shouldRun` zuerst, der Leser verlässt die Datei also deterministisch (kein zweiter Thread, keine große Datei, kein Warten). Verlangt werden „process “, die WARN „interrupted while reading “, kein „finished (“ und kein „... import done.“. Der bestehende Test pinnt jetzt mit `startsWith("... import interrupted")` genau die `run`-Meldung. Gegenprobe: ohne den `if (!shouldRun.get())`-Zweig in `readAllFiles` rot („Expected: a collection containing a string starting with "interrupted while reading " but: mismatches were: [was "... import interrupted; the LMDB in …/lmdb-interrupted-reading is incomplete."]“); unverändert `AddressFilesToLMDBTest` 30/30 grün.

<a id="f48"></a>
### F48 – BAF: abgebrochenes LMDB-Delta meldet „... delta done“
- **Zeit:** 2026-10-17 19:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBDelta.java:115` (`run`)
- **Schwere:** niedrig
- **Beschreibung:** Musterdurchsicht nach F46/F47. `writeDelta` verlässt seine Merge-Schleife, sobald `interrupt()` das Flag löscht, und `run` meldete danach „... delta done: N address(es) written“ – die Delta-Datei enthält dann nur einen Teil der Adressen, die in den anderen Datenbanken, aber nicht in der Referenz stehen.
- **Fix:** Bei gelöschtem Flag WARN „... delta interrupted after N address(es); <Datei> is incomplete.“ statt der Fertigmeldung.
- **Reproduktion:** `LMDBDeltaTest#run_interruptedBeforeTheDelta_doesNotReportTheDeltaAsDone`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: not a collection containing a string starting with "... delta done" but: was <[…, ... delta done: 0 address(es) written to …]>“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `LMDBDeltaTest` 13/13, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `7517415`

<a id="f49"></a>
### F49 – BAF: unterbrochener Secrets-Datei-Producer meldet „Finished processing“ und „All secrets files processed.“
- **Zeit:** 2026-10-17 19:55 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerJavaSecretsFiles.java:82` (`produceKeys`)
- **Schwere:** niedrig
- **Beschreibung:** Musterdurchsicht nach F46–F48. `interrupt()` stoppt auch die laufende `SecretsFile`, deren `readFile` dann mitten in der Datei zurückkehrt. `produceKeys` meldete die Datei trotzdem als „Finished processing“ und am Ende „All secrets files processed.“ – im Test nach 28 % der Datei. Wer danach im Log nachsieht, welche Secrets geprüft wurden, hält die Datei für vollständig.
- **Fix:** Nach `readFile` bei gelöschtem Flag WARN „Secrets file processing interrupted in: <Datei>“ mit Fortschrittszeile und Rückkehr; die Schlussmeldung unterscheidet zwischen fertig und abgebrochen.
- **Reproduktion:** `ProducerJavaSecretsFilesTest#produceKeys_interruptedWhileReadingAFile_doesNotReportTheFileOrTheRunAsProcessed` (ein Consumer ruft nach dem ersten Schlüssel `interrupt()`)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: not a collection containing a string starting with "Finished processing" but: was <[…, Finished processing: …/secrets.txt, Progress: … Current File progress: 28.…]>“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` rot, nach `stash pop` grün. `ProducerJavaSecretsFilesTest` 12/12, `AbstractProducerTest` 20/20, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `aa704dc`

<a id="f50"></a>
### F50 – BAF: `ProducerJavaSecretsFiles.interrupt()` zwischen Flagprüfung und Dateistart wird übersehen
- **Zeit:** 2026-10-17 20:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerJavaSecretsFiles.java:79` (`produceKeys`)
- **Schwere:** niedrig
- **Beschreibung:** `produceKeys` prüft das Flag am Anfang jeder Datei, legt dann die `SecretsFile` an und registriert sie erst danach in `currentSecretsFile`. `interrupt()` setzt das Flag und stoppt die registrierte Datei; kam es genau dazwischen, war keine registriert, und die neue `SecretsFile` (mit eigenem, noch gesetztem Flag) las die ganze Datei, obwohl der Producer schon beendet werden sollte – bei großen Wortlisten Minuten bis Stunden, auf die ein Shutdown wartet.
- **Fix:** Nach dem Registrieren das Flag erneut prüfen und die Datei gegebenenfalls sofort stoppen. Dazu verhaltensneutral die Naht `createSecretsFile(File)` eingezogen (Erzeugung der `SecretsFile` in eine paketinterne Methode verschoben), damit ein Test genau im Fenster `interrupt()` aufrufen kann.
- **Reproduktion:** `ProducerJavaSecretsFilesTest#produceKeys_interruptBetweenFlagCheckAndReaderRegistration_doesNotReadTheFile`
- **Rot-Grün-Nachweis:** mit Naht, ohne Fix rot („Expected: is <0> but: was <3>“ – alle drei Secrets gelesen), die übrigen 12 Tests der Klasse grün (Naht verhaltensneutral); mit Fix grün; Gegenprobe (nur die Nachprüfung entfernt, Naht behalten) wieder rot, danach grün. `ProducerJavaSecretsFilesTest` 13/13, `AbstractProducerTest` 20/20, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `72d9152`

<a id="f51"></a>
### F51 – BAF: BIP39-Producer kürzt die abgeleiteten Schlüssel, wenn `privateKeyMaxNumBits` < 256
- **Zeit:** 2026-10-17 20:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaBip39.java:33` (Konstruktor), Ursache in `secret/RandomSecretSupplier` + `BIP39KeyProducer.nextBytes`
- **Schwere:** niedrig
- **Beschreibung:** `KeyProducerJavaBip39` erbt `privateKeyMaxNumBits` aus `CKeyProducerJava` (dort für den Zufallsproducer gedacht, z. B. Puzzle-Bereiche) und reicht es an `new BigInteger(bits, BIP39KeyProducer)` weiter. `BigInteger` holt sich dafür `ceil(bits / 8)` Bytes und maskiert das oberste; `BIP39KeyProducer.nextBytes` liefert die führenden Bytes des nächsten abgeleiteten Schlüssels. Mit 64 Bit war das erste Secret `e284129cc0922579` statt des abgeleiteten Schlüssels `e284129c…222cc372` (in der Sandbox geprüft) – also weder ein BIP39-Schlüssel noch ein gleichverteilter Zufallswert, ohne jeden Hinweis. Die README listet das Feld beim BIP39-Producer nicht.
- **Fix:** Der Konstruktor lehnt `privateKeyMaxNumBits ≠ 256` mit `IllegalArgumentException` ab, die Feld und Wert nennt. Die Prüfung steht in einer statischen Methode im `super(...)`-Argument (sonst `CT_CONSTRUCTOR_THROW`), also noch vor der teuren Seed-Ableitung.
- **Reproduktion:** `KeyProducerJavaBip39Test#constructor_privateKeyMaxNumBitsBelowFullKeySize_throwsIllegalArgumentException`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.“), mit Fix grün; Gegenprobe per `git stash` rot, nach `stash pop` grün. `KeyProducerJavaBip39Test` 2/2, `BIP39KeyProducerTest` 296/296, `KeyProducerJavaTest` 156/156, `FinderTest` 22/22, `ConfigFixturesParseTest` 3/3, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün (der erste Fix-Versuch mit der Prüfung im Konstruktorrumpf war es nicht und wurde nicht gepusht). Die Beispielkonfigurationen setzen für BIP39 kein abweichendes `privateKeyMaxNumBits`.
- **Status:** gefixt
- **Commit:** BAF `73d5d14`

<a id="f52"></a>
### F52 – BAF: ein Producer, dessen Freigabe fehlschlägt, lässt alle folgenden weiterlaufen
- **Zeit:** 2026-10-17 20:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerReleaser.java:60` (`release`)
- **Schwere:** mittel
- **Beschreibung:** `release` unterbricht, erwartet und gibt die Producer nacheinander frei, ohne Fehlerbehandlung. `ProducerOpenCL.releaseProducer` schließt den `OpenCLContext`, dessen `clRelease*`-Aufrufe über `ClErrorChecker` bei jedem OpenCL-Fehlercode `OpenClCallFailedException` werfen (etwa nach einem Treiberfehler). Wirft ein Producer, endet die Schleife: alle Producer dahinter werden weder unterbrochen noch freigegeben, laufen also weiter, und `Finder.interrupt()` (Shutdown-Hook) kehrt mit der Exception zurück. Bei einer gemischten Konfiguration (eine OpenCL-Karte plus CPU-Producer) hängt so der Shutdown an weiterlaufenden CPU-Producern. Der fehlgeschlagene Producer steht zudem schon in `released` und wird bei einem zweiten Aufruf übersprungen.
- **Fix:** Jeder Producer wird in einem eigenen `try` behandelt; ein Fehler wird geloggt, die übrigen werden weiter unterbrochen und freigegeben, und erst danach wird die erste Exception unverändert geworfen (weitere als `suppressed`). Javadoc ergänzt. Die SpotBugs-Meldung `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` für das unverändert weitergeworfene Original ist in die bestehende, begründete Einzelfall-Unterdrückung (spotbugs#3918) aufgenommen – Verpacken lehnt die dortige Begründung ausdrücklich ab.
- **Reproduktion:** `ProducerReleaserTest#release_firstProducerFailsToRelease_releasesTheOthersAndRethrows`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <1> but: was <0>“ – der zweite Producer wurde nie freigegeben), mit Fix grün; Gegenprobe per `git stash` rot, nach `stash pop` grün. `ProducerReleaserTest` 4/4, `FinderTest` 22/22, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün (nach Aufnahme in die Unterdrückung; der Lauf davor war rot und wurde nicht gepusht).
- **Status:** gefixt
- **Commit:** BAF `9c258b5`

<a id="f58"></a>
### F58 – BitcoinAddressFinder: vorzeichenbehafteter Base36-Hash wird als Zweierkomplement importiert
- **Zeit:** 2026-10-17 21:15 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/util/Base36Decoder.java:31` (`decodeBase36ToFixedLengthBytes`)
- **Schwere:** niedrig
- **Beschreibung:** Die Javadoc verspricht eine `NumberFormatException` für Eingaben, die kein gültiges Base36 sind. `new BigInteger(s, 36)` akzeptiert aber ein führendes `-` oder `+`. Eine BitCore-Adresszeile wie `wkh_-1` (Aufrufer `AddressTxtLine.fromLine`) wurde so zu den Zweierkomplement-Bytes von −1 dekodiert und als Hash in die LMDB übernommen, statt als fehlerhafte Zeile gezählt zu werden (der Import fängt die `NumberFormatException` und zählt sie).
- **Fix:** Ein führendes Vorzeichen wirft dieselbe `NumberFormatException` wie jede andere ungültige Ziffer.
- **Reproduktion:** `Base36DecoderTest#decodeBase36ToFixedLengthBytes_leadingMinus_throwsNumberFormatException`, `…_leadingPlus_throwsNumberFormatException`
- **Rot-Grün-Nachweis:** ohne Fix rot (beide: „Expected java.lang.NumberFormatException to be thrown, but nothing was thrown.“, 16 Tests, 2 Fehlschläge), mit Fix grün 16/16; Gegenprobe per `git stash` rot (2 Fehlschläge), nach `stash pop` grün. `AddressTxtLineTest` 291/291, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `ea71a25`

<a id="f66"></a>
### F66 – BAF: Nach dem Shutdown-Timeout werden Batches gegen die geschlossene Datenbank als „geprüft, nichts gefunden“ gemeldet
- **Zeit:** 2026-10-17 23:25 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/consumer/ConsumerJava.java` (`interrupt`, `processBatch`)
- **Schwere:** mittel
- **Beschreibung:** `interrupt()` wartet `awaitQueueEmptySeconds` (Standard 60 s) auf die Worker und schließt danach die Persistenz, auch wenn die Wartezeit abgelaufen ist und ein Worker noch arbeitet (nur „Timeout reached“ wird gewarnt). Seit dem Schutz gegen das Lesen nach dem Schließen antwortet `LMDBPersistence.containsAddress` dann „nicht vorhanden“. Der Worker arbeitet weiter (seit F15 leert er die Queue vor dem Ende) und meldet jeden danach fertigen Batch an die Result-Listener als geprüft, ohne Treffer. Ein Treffer darin geht verloren, ohne Treffer-Log, und ein Client, der einen Bereich abfährt, hält ihn für erledigt – genau die Verwechslung „geprüft, leer“ gegen „nie geprüft“, die der Broadcast laut CLAUDE.md ausschließen soll. Erreichbar, wenn die Queue länger als die Wartezeit zum Leeren braucht (10 Batches à `2^batchSizeInBits`).
- **Fix:** `interrupt()` setzt vor dem Schließen ein Flag; `processBatch` prüft es nach den Abfragen und meldet einen danach fertigen Batch nicht, sondern loggt eine Warnung mit Größe und Secret-Basis.
- **Reproduktion:** `ConsumerJavaTest#interrupt_awaitTimesOut_batchCheckedAfterThePersistenceClosedIsNotReportedAsChecked` (Listener hält Batch 1 fest, Batch 2 mit einer Adresse der Test-DB liegt in der Queue, `awaitQueueEmptySeconds = 1`)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <[]> but: was <[BatchResult[secretBase=2, checkedCount=1, hits=[]]]>“), mit Fix grün, `ConsumerJavaTest` 36/36; Gegenprobe per `git stash` rot, nach `stash pop` grün. Vorprobe: bei offener Datenbank (Freigabe vor `interrupt()`) meldet derselbe Aufbau Batch 2 mit seinem Treffer – der leere Bericht ist also ein verlorener Treffer. `FinderTest` 22/22, `BitcoinAddressFinderArchitectureTest` 17/17, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `0ea5ec9`

<a id="f100"></a>
### F100 – BAF: Socket-Key-Producer mit negativem `timeoutMillis` hängt still statt beim Start zu scheitern
- **Zeit:** 2026-10-18 06:50 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaSocket.java:68` (Konstruktor, `requireNonNegativeTimeout` neu)
- **Schwere:** niedrig
- **Beschreibung:** Vergleich der drei Netzwerk-Key-Producer (Socket, WebSocket, ZeroMQ). WebSocket und ZeroMQ erlauben `timeoutMillis: -1` (blockieren), die CLAUDE.md empfiehlt das für interaktive Läufe. `CKeyProducerJavaSocket` dokumentiert, dass ein negativer Wert hier nicht unterstützt wird und den Start des Producers verhindert. Tatsächlich verhinderte er nichts: `ServerSocket.setSoTimeout(-1)`/`Socket.connect(addr, -1)` werfen `IllegalArgumentException` im Reader-Runnable, dessen `Future` niemand ausliest – keine Logzeile, der Reader ist tot, und `createSecrets` blockiert bei negativem Timeout in `take()` für immer. Wer die WebSocket-Einstellung auf den Socket-Producer überträgt, sieht einen hängenden Lauf ohne Fehlermeldung.
- **Fix:** Der Konstruktor prüft `timeoutMillis` vor `super(...)` (Muster aus F51/BIP39) und wirft eine `IllegalArgumentException` mit dem Wert, wie die Konfiguration es verspricht.
- **Reproduktion:** `KeyProducerJavaSocketTest#constructor_negativeTimeout_isRejectedInsteadOfHangingSilently`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown.“), mit Fix grün (19/19 der Klasse, skipped 0); Gegenprobe per `git stash` rot (1), nach `pop` grün. `FinderTest` 22/22, `AbstractKeyProducerQueueBufferedTest` 11/11, `ConfigFixturesParseTest` 3/3, `BitcoinAddressFinderArchitectureTest` 17/17, `spotless:apply` und `spotbugs:check` grün. Kein Beispiel unter `examples/` nutzt einen negativen Socket-Timeout.
- **Nebenbefund (gefixt als F101):** Auch die `IllegalStateException` „Unable to establish socket connection … after N attempts“ am Ende des Readers geht im unbeobachteten `Future` verloren; der Nutzer sieht nur die einzelnen „Connection attempt N failed“-Warnungen und danach eine `NoMoreSecretsAvailableException` mit „Timeout while waiting for secret“.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `807cef2`

<a id="f101"></a>
### F101 – BAF: Endgültiger Verbindungsfehler des Socket-Key-Producers geht im unbeobachteten `Future` verloren
- **Zeit:** 2026-10-18 07:05 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaSocket.java:156` (`start`, Ende des Reader-Runnables)
- **Schwere:** niedrig
- **Beschreibung:** Nebenbefund aus F100. Scheitern alle `connectRetryCount` Verbindungsversuche, wirft das Reader-Runnable `IllegalStateException` „Unable to establish socket connection to host:port after N attempts“ mit dem letzten `IOException` als Ursache – in den `Future`, den nur `interrupt()` (zum Abbrechen) anfasst. Die Meldung erschien nirgends; der Nutzer sah nur die einzelnen „Connection attempt N failed“-Warnungen und dann eine `NoMoreSecretsAvailableException` mit „Timeout while waiting for secret“, die den Grund nicht nennt.
- **Fix:** Vor dem Werfen wird die Meldung samt Ursache als ERROR geloggt.
- **Reproduktion:** `KeyProducerJavaSocketTest#start_noPeerAfterAllRetries_logsTheFailureInsteadOfLosingItInTheFuture` (Client auf einen freien Port, ein Versuch, LogCaptor)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: a collection containing a string containing "Unable to establish socket connection" but: was empty“), mit Fix grün (20/20 der Klasse, skipped 0); Gegenprobe per `git stash` rot (1), nach `pop` grün. `FinderTest` 22/22, `AbstractKeyProducerQueueBufferedTest` 11/11, `ConfigFixturesParseTest` 3/3, `BitcoinAddressFinderArchitectureTest` 17/17, `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `445f352`

<a id="f119"></a>
### F119 – BitcoinAddressFinder: Ein Client, der während der Gitter-Ankündigung verbindet, erfährt das Gitter nie
- **Zeit:** 2026-10-18 12:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/SocketResultBroadcaster.java:154` (Accept-Schleife, `announceConfiguration`)
- **Schwere:** niedrig
- **Beschreibung:** Die Accept-Schleife las `configurationMessage` (noch `null`) und registrierte den Client erst danach in `clients`. Lief `announceConfiguration` von `Finder.configureProducer` genau dazwischen, traf ihr `broadcast` den Client nicht (noch nicht registriert) und sein Gruß enthielt sie auch nicht (schon gelesen). Der TCP-Transport wiederholt die Konfiguration nicht (anders als ZeroMQ), der Client erhält `batchSizeInBits` also nie und kann den Schritt nicht kennen – laut CLAUDE.md sendet er dann still falsch ausgerichtete Bereiche. Das Fenster liegt genau dort, wo Clients typischerweise verbinden: kurz nach `startKeyProducer()`, vor dem Ende von `configureProducer()`. Der WebSocket-Weg hat das Problem nicht: Java-WebSocket trägt die Verbindung vor `onOpen` in die Menge ein, `greetAll` erreicht sie also.
- **Fix:** Ein Lock ordnet beides: Gruß lesen und Client registrieren geschehen unter `registrationLock`, ebenso Setzen und Senden der Ankündigung. Eine Ankündigung liegt damit entweder vor der Registrierung (der Gruß trägt sie) oder danach (ihr `broadcast` erreicht den Client). `broadcast` ruft nur `offer`, blockiert unter dem Lock also nicht.
- **Reproduktion:** `SocketResultBroadcasterTest#announceConfiguration_whileAClientIsBeingRegistered_theClientStillReceivesIt` – über die neue, verhaltensneutrale Naht `setBeforeClientRegistered` läuft die Ankündigung auf einem zweiten Thread genau im Fenster, bis sie endet oder auf das Lock wartet (`Thread.State.BLOCKED`).
- **Rot-Grün-Nachweis:** gegen den Naht-Stand ohne Fix rot („Expected: is not null but: was null“, kein `config` binnen 3 s), mit Fix grün (7/7 der Klasse, skipped 0); Gegenprobe durch Zurückkopieren des Naht-Stands rot (1), danach wieder grün. Breite Liste (alle drei Broadcaster, `KeyProducerJavaSocketTest`, `FinderTest`, `BitcoinAddressFinderArchitectureTest`) grün, `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `d1b3125`

<a id="f120"></a>
### F120 – BitcoinAddressFinder: BIP39-Mnemonics ohne NFKD-Normalisierung ergeben für nicht-englische Sprachen falsche Schlüssel
- **Zeit:** 2026-10-18 13:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/BIP39KeyProducer.java:112` (Konstruktor)
- **Schwere:** mittel
- **Beschreibung:** BIP39 berechnet den Seed per PBKDF2 aus der **NFKD-Form** von Mnemonic und Passphrase. bitcoinj 0.17.1 normalisiert nicht (`DeterministicSeed.ofMnemonic` → `MnemonicCode.toSeed` → `PBKDF2SHA512.derive`, per `javap` geprüft: kein `Normalizer`), und `BIP39KeyProducer` reichte die konfigurierte Zeichenkette unverändert weiter. Damit leitete `KeyProducerJavaBip39` für jede Mnemonic, deren Text nicht schon NFKD ist, andere Schlüssel ab als jede Standard-Wallet – der Scan prüfte also die falschen Adressen, ohne Fehlermeldung. Betroffen: jede japanische Mnemonic (zusammengesetzte Kana, ideografisches Leerzeichen), jede koreanische (Hangul wird praktisch immer zusammengesetzt geschrieben) und jede eingefügte NFC-Mnemonic mit Akzenten (Französisch, Spanisch, Tschechisch, Portugiesisch, Türkisch) oder kyrillischem `й` (Russisch); ebenso eine Passphrase mit solchen Zeichen. Der bestehende Vektortest normalisierte vor dem Aufruf von bitcoinj selbst und verdeckte die Lücke im Produktionscode.
- **Fix:** Mnemonic und Passphrase werden vor `DeterministicSeed.ofMnemonic` mit `Normalizer.Form.NFKD` normalisiert. SpotBugs meldet dafür `IMPROPER_UNICODE` (FindSecBugs warnt vor Normalisieren nach einer Sicherheitsprüfung); methodengenau in `spotbugs-exclude.xml` unterdrückt, mit Begründung.
- **Reproduktion:** `BIP39KeyProducerTest#nextKey_givenTheVectorMnemonicAsWritten_derivesTheKeyOfTheStandardSeed` und `#nextKey_givenTheVectorMnemonicComposed_derivesTheKeyOfTheStandardSeed` (je über alle 288 Trezor-Vektoren in zwölf Sprachen; Erwartung: der Schlüssel `M/44H/0H/0H/0/0` aus dem Vektor-Seed)
- **Rot-Grün-Nachweis:** ohne Fix rot in 142 Fällen (24 japanische Vektoren wie geschrieben, 118 zusammengesetzte: Französisch 19, Japanisch 24, Koreanisch 24, Russisch 13, Spanisch 20, Türkisch 18), z. B. „Expected: is "8041a494…" but: was "de26cfef…"“; mit Fix grün (872/872 der Klasse, skipped 0); Gegenprobe per `git stash` rot (142), nach `pop` grün. Breite Liste (`BIP39WordlistTest`, `KeyProducerJavaBip39Test`, `KeyUtilityTest`, `ConfigFixturesParseTest`, `BitcoinAddressFinderArchitectureTest`) grün, `spotless:apply` grün, `spotbugs:check` grün nach der Unterdrückung.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `aaea9fc`

<a id="f121"></a>
### F121 – BitcoinAddressFinder: CashAddr in Großbuchstaben wird abgelehnt oder als falscher Hash importiert
- **Zeit:** 2026-10-18 13:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java:143` (`fromLine`)
- **Schwere:** niedrig
- **Beschreibung:** Die CashAddr-Spezifikation erlaubt Klein- oder Großschreibung (nicht gemischt), QR-Codes tragen sie groß. `fromLine` kannte nur die Kleinschreibung: Mit Präfix (`BITCOINCASH:QPM2…`) wurde das Präfix nicht entfernt, die Zeile am Doppelpunkt geteilt und `BITCOINCASH` als Base58 abgelehnt; ohne Präfix (`QPM2…`) verfehlte die Adresse den `q`-Zweig und landete im Base58-Rückfall, der sie – wenn sie zufällig nur Base58-Zeichen enthält, also kein `0`/`l` – **still als falschen hash160 importierte** (`7119debe…` statt `76a04053…` für das Spezifikationsbeispiel). Großgeschriebenes Bech32 (`BC1Q…`) war nicht betroffen, bitcoinj `Bech32.decode` behandelt es selbst.
- **Fix:** Das großgeschriebene Präfix wird vor dem Entfernen auf die Kleinform gebracht, und eine Adresse, die ganz aus CashAddr-Zeichen in Großschreibung besteht und 42 Zeichen lang ist (`[QP]` + 41), wird kleingeschrieben; keine Base58-Adresse ist so lang, Qtum-Adressen mit `Q` (34 Zeichen) bleiben unberührt. Nicht Teil des Fixes und unverändert: CashAddr-`p`-Adressen (P2SH) gehen weiter in den ZCash-Zweig (im Code als „does not work“ vermerkt), und die CashAddr-Prüfsumme wird nicht geprüft.
- **Reproduktion:** `AddressTxtLineTest#fromLine_uppercaseCashAddrWithPrefix_returnsTheHash160OfTheLowercaseForm`, `#fromLine_uppercaseCashAddrWithoutPrefix_returnsTheHash160OfTheLowercaseForm` (Beispiel der Spezifikation, hash160 von `1BpEi6DfDAUFd7GtittLSdBeYJvcoaVggu`)
- **Rot-Grün-Nachweis:** ohne Fix beide rot („Unexpected exception thrown: … invalid Base58 address (input: BITCOINCASH)“ bzw. „Expected: is "76a04053bda0a88bda5177b86a15c3b29f559873" but: was "7119debe7d4714d8bb5357c7f28d4d00f5c8f9a6"“), mit Fix grün (293/293 der Klasse, skipped 0); Gegenprobe per `git stash` rot (2), nach `pop` grün. Breite Liste (`Bech32HelperTest`, `AddressFilesToLMDBTest`, `SeparatorFormatTest`, `BitcoinAddressFinderArchitectureTest`) grün, `spotless:apply` und `spotbugs:check` grün (das großgeschriebene Präfix ist ein Literal, `toUpperCase` auf einem Literal meldet `SPP_CONVERSION_OF_STRING_LITERAL`).
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `6f0fc7d`

<a id="f122"></a>
### F122 – BitcoinAddressFinder: Riecoin-ScriptPubKey in Großbuchstaben wird als falscher Hash importiert
- **Zeit:** 2026-10-18 14:05 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java:163` (`fromLine`, Riecoin-Zweig)
- **Schwere:** niedrig
- **Beschreibung:** Der Riecoin-Zweig erkennt eine ScriptPubKey-Zeile an `startsWith("76a914")` – nur in Kleinschreibung. Hex ist aber üblicherweise unabhängig von der Schreibweise (die Hex-Dekodierung danach akzeptiert beide). Ein großgeschriebenes Skript (`76A914…88AC`) verfehlte den Zweig und landete im Base58-Rückfall; enthält es kein `0`, ist es dort gültig und ergab **still einen falschen hash160** (`21e32e15…` statt `751e76e8…`). Gefunden mit der Probe-Klasse aus F121 (Schreibweisen gültiger Eingaben). Ebenfalls gemessen: `WKH_…` mit großem Präfix wird abgelehnt (laut, nicht falsch; das Präfix ist BitCores Kleinschreibung), Base36-Nutzdaten in beiden Schreibweisen richtig.
- **Fix:** Präfixvergleich mit `regionMatches(true, …)`.
- **Reproduktion:** `AddressTxtLineTest#fromLine_uppercaseRiecoinScriptPubKey_returnsTheHash160OfTheLowercaseForm`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is "751e76e8199196d454941c45d1b3a323f1433bd6" but: was "21e32e15ba0d03cb848d066dccdb18df5745ef83"“), mit Fix grün (294/294 der Klasse, skipped 0); Gegenprobe per `git stash` rot (1), nach `pop` grün. Breite Liste wie F121 grün, `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `b061522`

<a id="f124"></a>
### F124 – BitcoinAddressFinder: Strg-C während des Datenbankladens meldet „Key producer id is unknown“
- **Zeit:** 2026-10-18 15:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java:463` (`configureProducer`), `:243` (`startConsumer`), Aufrufer `cli/Main.run` (Startphasen) und der Shutdown-Hook
- **Schwere:** niedrig
- **Beschreibung:** `Main.run` startet nacheinander Key-Producer, Consumer (lädt die LMDB, der lange Schritt) und Producer. Ein Strg-C ruft aus dem Shutdown-Hook `Finder.interrupt()` auf, das die Key-Producer unterbricht und die Map leert (`freeAllKeyProducers`). Der Main-Thread läuft danach weiter: `configureProducer` sucht die Key-Producer-ID und wirft `KeyProducerIdUnknownException`, `Main` loggt „Fatal error during Main.run“ samt Stacktrace und meldet so einen Konfigurationsfehler, den es nicht gibt. Fällt das Strg-C in den Key-Producer-Start, lädt `startConsumer` die ganze Datenbank noch, obwohl nichts sie mehr nutzt. Es fehlte ein Merker, den die späteren Startschritte prüfen (Muster wie F50).
- **Fix:** `interrupt()` setzt unter dem `interruptLock` ein `volatile`-Flag; `startConsumer` und `configureProducer` kehren dann mit einer Info-Zeile zurück. `initProducer`/`startProducer` finden danach keine Producer.
- **Reproduktion:** `FinderTest#configureProducer_interruptedWhileConsumerStarted_doesNotReportUnknownKeyProducer`, `FinderTest#startConsumer_interruptedBefore_consumerNotStarted`
- **Rot-Grün-Nachweis:** ohne Fix beide rot („Unexpected exception thrown: …KeyProducerIdUnknownException: Key producer id is unknown: exampleId“ bzw. „Expected: is an empty collection but: <[ConsumerJava(…)]>“), mit Fix grün (2/2, skipped 0); Gegenprobe per `git stash` rot (2), nach `pop` grün. Breite Liste: `FinderTest` 24/24, `MainTest` 18 (6 OpenCL übersprungen wie zuvor), `ConsumerJavaTest` 36/36, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `778abcd`

<a id="f126"></a>
### F126 – BitcoinAddressFinder: Ein Tippfehler in der `keyProducerId` eines Producers fällt erst nach dem Datenbankladen auf
- **Zeit:** 2026-10-18 16:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java:159` (`startKeyProducer`), geprüft wurde bisher erst in `configureProducer`
- **Schwere:** niedrig
- **Beschreibung:** `Main.run` startet Key-Producer, dann den Consumer (`initLMDB`: Bloom-/In-Memory-Backend und GPU-Filter bauen sich hier auf, bei großen Datenbanken Minuten), dann die Producer. Ob ein Producer auf einen konfigurierten Key-Producer verweist und ob sein `batchSizeInBits` im Bereich liegt, prüfte erst `configureProducer` – ein Tippfehler in der ID oder `batchSizeInBits: -1` kostete also das ganze Laden, bevor die Meldung kam. Gleiches Muster wie F125.
- **Fix:** `startKeyProducer` prüft nach dem Anlegen der Key-Producer jeden Producer mit denselben Prüfungen wie `configureProducer` (`validateProducerConfiguration`). Der bisherige Test `configureProducer_keyProducerIdIsUnknown_ExceptionThrown` (Consumer vor den Key-Producern gestartet, Fehler erst beim Konfigurieren) ist in `startKeyProducer_producerKeyProducerIdIsNull_ExceptionThrown` aufgegangen.
- **Reproduktion:** `FinderTest#startKeyProducer_producerKeyProducerIdIsNull_ExceptionThrown`, `#startKeyProducer_producerKeyProducerIdHasATypo_ExceptionThrown`, `#startKeyProducer_producerBatchSizeOutOfRange_ExceptionThrown`
- **Rot-Grün-Nachweis:** ohne Fix alle drei rot („Expected …KeyProducerIdUnknownException to be thrown, but nothing was thrown.“ bzw. „Expected java.lang.IllegalArgumentException …“), mit Fix grün (3/3, skipped 0); Gegenprobe per `git stash` rot (3), nach `pop` grün. Breite Liste: `FinderTest` 26/26, `MainTest` 18 (6 OpenCL übersprungen wie zuvor), `ConfigFixturesParseTest` 3/3, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün. Alle `examples/config_Find*.json` außer dem Secrets-Datei-Beispiel bestehen die neue Prüfung (Skript über die JSON-Dateien); zu diesem siehe F127.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `9a35d88`
- **Nachtrag (Branch-Review 2026-10-20):** Die drei Tests konfigurierten nur einen `CProducerJava`; `validateProducerConfiguration` prüft aber auch `producerJavaSecretsFiles` und `producerOpenCL`, und das Streichen einer dieser beiden `addAll`-Zeilen ließ alle Tests grün (für den OpenCL-Producer also wieder der F126-Fehler, F140 reproduzierte ihn genau damit). Die drei Tests laufen jetzt über `FinderTest.ProducerList` (`PRODUCER_JAVA`, `PRODUCER_JAVA_SECRETS_FILES`, `PRODUCER_OPENCL`: legt den Producer-Typ der Liste an und hängt ihn dort ein; ohne OpenCL-Gerät, da vor der Prüfung kein Producer gebaut wird). Gegenprobe: jede der drei `addAll`-Zeilen einzeln entfernt macht genau die drei Zeilen ihrer Liste rot („Expected …KeyProducerIdUnknownException to be thrown, but nothing was thrown.“ bzw. „Expected java.lang.IllegalArgumentException …“), unverändert 9/9 grün.

<a id="f127"></a>
### F127 – BitcoinAddressFinder: Das mitgelieferte Secrets-Datei-Beispiel läuft nicht; der Producer verlangt einen Key-Producer, den er nicht benutzt
- **Zeit:** 2026-10-18 16:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java` (`configureProducer`, `processProducers` mit `getKeyProducer` auch für `producerJavaSecretsFiles`), `examples/config_Find_SecretsFile.json`, `README.md` (Abschnitt „Secrets File Producer“)
- **Schwere:** mittel
- **Beschreibung:** `ProducerJavaSecretsFiles` liest seine Secrets selbst aus den Dateien; sein Konstruktor nimmt einen `KeyProducer`, die Javadoc sagt „unused in this producer“. `configureProducer` löst ihn trotzdem über `getKeyProducer` auf und wirft `KeyProducerIdUnknownException` bei fehlender oder unbekannter ID. Das Beispiel `examples/config_Find_SecretsFile.json` (und `run_Find_SecretsFile.sh/.bat`) setzt für keinen seiner vier Producer eine `keyProducerId` und konfiguriert keinen Key-Producer: es scheitert mit „Key producer id is unknown: null“ – vor F126 nach dem BLOOM-Laden, jetzt sofort. Unbemerkt, weil `MainTest` eine eigene Kopie unter `src/test/resources/testRoundtrip/` benutzt, die einen Zufalls-Key-Producer als Platzhalter trägt. Die README beschreibt `keyProducerId` dort als „Unique identifier for this producer“ und zeigt ein Beispiel mit einer ID ohne passenden Key-Producer.
- **Fix:** Beispiel und README an den Vertrag des Codes angeglichen: `examples/config_Find_SecretsFile.json` bekommt einen Zufalls-Key-Producer und jeder der vier Producer dessen `keyProducerId` (wie die Testkopie); die README beschreibt `keyProducerId` als Verweis auf einen konfigurierten Key-Producer und zeigt ihn im Beispiel. Offene Designfrage für den Nutzer: Secrets-Datei-Producer ganz ohne Key-Producer zulassen (die ID diente dann nur als Bezeichnung in der Statistik; `AbstractProducer` liest `keyProducer` dort für den Strategienamen, `validateProducerConfiguration` aus F126 müsste folgen).
- **Reproduktion:** `ConfigFixturesParseTest#secretsFileExample_everyProducerNamesAConfiguredKeyProducer` (lädt die mitgelieferte Datei und ruft `Finder.startKeyProducer`)
- **Rot-Grün-Nachweis:** ohne Fix rot („Unexpected exception thrown: …KeyProducerIdUnknownException: Key producer id is unknown: null“), mit Fix grün (1/1, skipped 0); Gegenprobe per `git stash` der Beispieldatei rot (1), nach `pop` grün. `ConfigFixturesParseTest` 4/4, `BitcoinAddressFinderArchitectureTest` 17/17, `spotless:apply` ohne weiteren Diff; kein Produktionscode geändert.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `2cdea44`

<a id="f129"></a>
### F129 – BitcoinAddressFinder: Der OpenCL-Producer verfehlt die Schlüssel 2 … keysPerWorkItem−1, weil das erste Work-Item der Basis 0 bei 0·G verankert ist
- **Zeit:** 2026-10-18 18:10 UTC
- **Datei:** `src/main/resources/inc_ecc_secp256k1custom.cl:1058` (Anker `P0 = (baseK0 | base_offset)·G` per `point_mul_xy_comb`, Kommentar „Degenerate case … impossible for the supported aligned sequential / random ranges“ darüber), Aufrufer `producer/ProducerOpenCL`, Auswertung `opencl/OpenCLGridResult.readEntry`
- **Schwere:** niedrig
- **Beschreibung:** Jedes Work-Item berechnet seine `keysPerWorkItem` Schlüssel als `P0 + m·G` aus einem gemeinsamen affinen Anker `P0`. Für das erste Work-Item eines Rasters mit Basis 0 ist der Anker der Skalar 0, also der Punkt im Unendlichen, der affin nicht darstellbar ist: alle Schlüssel dieses Work-Items werden falsch berechnet. `OpenCLGridResult.readEntry` ersetzt nur den Schlüssel 0 selbst durch `INVALID_KEY_ONE`, die übrigen Einträge 2 … keysPerWorkItem−1 tragen falsche Koordinaten und Hashes; im kompakten Filtermodus verwirft schon der GPU-Filter sie. Basis 0 ist kein Sonderfall: der inkrementelle Key-Producer beginnt standardmäßig bei `MIN_VALID_PRIVATE_KEY` = 2, und das Raster wird auf 0 abgerundet – jeder solche Scan auf der GPU überspringt still die kleinsten Schlüssel, die der CPU-Pfad findet. Praktisch betroffen sind nur die winzigen Schlüssel (die Puzzles 2–4 liegen unter 16, sind aber längst gelöst). *Nachtrag 2026-10-18 18:40 UTC:* Die ursprünglich mitbehauptete Entartung am oberen Ende (`k0 + m ≡ 0 (mod n)`) ist gemessen nicht erreichbar: `OpenClTask.setSrcPrivateKeyChunk` weist jede Basis über `PrivateKeyValidator.getMaxPrivateKeyForBatchSize` mit `PrivateKeyTooLargeException` ab (ERROR, die Charge entfällt), sodass die GPU kein Raster rechnet, das n überschreitet; mit ausgerichteten Basen bliebe nur `batchSizeInBits` 1 (dort lässt die Grenze `n − (2^b − 1)` den Schlüssel n als letzte Rasterzahl zu, nach F45 eins zu weit). Gemessen auf pocl mit Start auf einer ausgerichteten Basis und `endPrivateKey` = n+190: die CPU findet alle 113 Ziele n−1 … n−299, die GPU (mit `keysPerWorkItem` 16 wie 1) alle außer den 35 im obersten Raster n−65 … n−1, das sie wegen dieser Grenze verwirft – ein dokumentierter Unterschied der Rastermodi, kein weiterer Fehler.
- **Historie:** Der Fehler besteht seit dem ersten Kernel mit mehreren Schlüsseln je Work-Item, `88dba0f` (2025-05-25, `loopCount`): Schlüssel 0 per `point_mul_xy`, alle weiteren durch `+G` – bei Basis 0 schon der Startpunkt falsch. `c026e30` (2026-06-14, Montgomery-Batch) und `9fa94fa` (2026-06-15, heutiger affiner Anker) haben ihn übernommen und die Annahme „never occurs for the supported … ranges“ bzw. „same practical assumption as the previous path“ weitergegeben.
- **Fix:** Entscheidung des Nutzers (Variante A, im Kernel). Nach dem Comb prüft jedes Work-Item, ob sein Anker-Skalar 0 ist (`anchor_is_zero`, ODER über die 8 Wörter). Für dieses eine Work-Item ist jeder Schlüssel `m·G`, also genau `iG_table`-Eintrag m−1; er wird in Pass B für m > 0 direkt aus der Tabelle übernommen, in beiden Feld-Varianten (2^32: Wörter direkt; `USE_REDUCED_RADIX_FIELD`: Tabelle liegt in 2^26, daher `fe10x26_normalize` + `fe10x26_to_u32x8`). Schlüssel 0 ersetzt weiter der Host. Kommentar „Degenerate case … impossible“ ersetzt durch die beiden tatsächlichen Fälle.
- **Kosten:** ein ODER über 8 Wörter je Work-Item und ein einheitlich falscher Zweig je Schlüssel; abweichend rechnet nur ein Work-Item eines einzigen Rasters.
- **Reproduktion:** `OpenCLZeroAnchorTest#createKeys_gridBaseZero_everyKeyOfTheFirstWorkItemIsCorrect` (Basis 0, `batchSizeInBits` 8, `keysPerWorkItem` 16, beide Feld-Varianten): Schlüssel 1 muss 1·G sein (`INVALID_KEY_ONE.getUncompressed()`), jeder Schlüssel ab 2 muss `runtimePublicKeyCalculationCheck()` bestehen.
- **Rot-Grün-Nachweis:** ohne Fix 2/2 rot (schon Schlüssel 1 falsch, in beiden Varianten), mit Fix 2/2 grün (pocl). Ende zu Ende über die CLI (682 Ziele, jeder dritte Schlüssel 3…2046, inkrementell 2…0x7ff, `batchSizeInBits` 8): alter Kernel (nur in `target/classes` getauscht) 3× 677 Treffer, es fehlen genau 3, 6, 9, 12, 15; mit Fix `keysPerWorkItem` 16 volle Übertragung 10× 682, FUSE_16 682, `keysPerWorkItem` 1 voll und FUSE_16 je 682.
- **Regression:** alle OpenCL-nahen Tests auf pocl (`*OpenCL*`, `Probe*`, `ProducerOpenCLTest`, `TuneConfigurationTest`, `FinderGpuFilterTypeTest`): 22 Klassen, 262 Tests, 0 Fehler; 23 übersprungen aus bestehenden Gründen (`@Disabled`, Rastergröße über dem pocl-Gerät, Annahme eines GPU-Geräts).
- **CI-Bedarf:** der `test-opencl`-Job (pocl) und eine Laufzeitmessung des Kernels auf echter GPU (erwartet: nicht messbar).
- **Status:** gefixt
- **Commit:** 97a2234

<a id="f130"></a>
### F130 – BitcoinAddressFinder: `SocketResultBroadcaster.close()` verwirft Ergebnisse, die schon für einen verbundenen Client bereitliegen
- **Zeit:** 2026-10-18 19:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/SocketResultBroadcaster.java` (`close`, Writer in `startWriter`)
- **Schwere:** mittel
- **Beschreibung:** Jeder Client hat eine Warteschlange und einen Writer-Thread. `close()` setzte `shouldStop`, schloss sofort die Client-Sockets und rief `writerExecutor.shutdownNow()`; der Writer prüfte `shouldStop` vor jeder Nachricht. Was beim Schließen noch in der Warteschlange stand, ging verloren. Genau dann stehen dort die letzten Batches eines endlichen Laufs: der Finder schließt die Broadcaster unmittelbar nach dem Scan, und das Ergebnis je Batch ist laut Entwurf das Abschlusssignal für den Client („range checked, nothing there“). Ende zu Ende über die CLI gemessen: ein TCP-Client, die ganze Zeit verbunden, bekam von 8 Batches (2..0x7ff, `batchSizeInBits` 8) nur 2; der Consumer hatte alle 8 vor dem Schließen geprüft.
- **Fix:** Der Writer liest mit `poll(50 ms)` und hört nach einem Stopp erst auf, wenn seine Warteschlange leer ist; `close()` schließt den Server, wartet bis zu `CLOSE_DRAIN_MILLIS` (2 s) auf die Writer und schließt erst danach die Client-Sockets. Ein Client, der nicht mehr liest, hält den Shutdown höchstens diese Frist auf. Dazu zwei paketinterne Nähte (`setBeforeWrite`, `setAfterStopRequested`), verhaltensneutral.
- **Reproduktion:** `SocketResultBroadcasterTest#close_batchesStillQueued_aConnectedClientReceivesAllOfThem` – der Writer wird vor dem ersten Schreiben festgehalten, bis `close()` den Stopp angefordert hat; zehn Batches liegen dann sicher in der Warteschlange.
- **Rot-Grün-Nachweis:** gegen den Naht-Stand ohne Fix rot (dreimal „Expected: is <10> but: was <0>“, die übrigen 7 Tests der Klasse grün), mit Fix grün in 3 von 3 Läufen (8/8 der Klasse, skipped 0); Gegenprobe durch Zurückkopieren des Naht-Stands rot („but: was <1>“), danach wieder grün. Breite Liste: `WebSocketResultBroadcasterTest` 4/4, `ZmqResultBroadcasterTest` 4/4, `KeyProducerJavaSocketTest` 20/20, `FinderTest` 26/26, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün. Zwei Vorfassungen des Tests ohne Naht waren nicht deterministisch (439/500 nur im ersten von drei Läufen, mit 4000 Nachrichten rot in 4 von 5) und wurden nicht übernommen.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `2b8ab58`

<a id="f131"></a>
### F131 – BitcoinAddressFinder: `Finder.shutdownAndAwaitTermination` schließt die Result-Broadcaster, bevor der Consumer die letzten Batches geprüft hat
- **Zeit:** 2026-10-18 19:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java` (`shutdownAndAwaitTermination`: erst `interrupt()`, dann `consumerJava.interrupt()`)
- **Schwere:** mittel
- **Beschreibung:** Nach dem Ende der Producer ruft `shutdownAndAwaitTermination` zuerst `interrupt()`, das die Key-Producer unterbricht (beim WebSocket-Key-Producer mit `endpoint.stop()` auch dessen Broadcast-Server) und die ZeroMQ- und Socket-Broadcaster schließt, und erst danach `consumerJava.interrupt()`, das die noch eingereihten Batches prüft (Drain in `consumeKeysRunner`). Was der Consumer in diesem Fenster prüft, meldet er an geschlossene Broadcaster – die Ergebnisse dieser Batches erreichen keinen Client, auch nach F130. Ende zu Ende gemessen (Socket-Broadcaster, Logging der 682 Treffer verlangsamt den Consumer): `interrupt called` bei 51,425 s, der Consumer prüfte noch bis 52,587 s; der Client erhielt 2 von 8 Batches.
- **Fix:** Sind die Producer beendet, lässt `shutdownAndAwaitTermination` zuerst den Consumer leeren (`releaseConsumer`) und ruft danach `interrupt()`. Läuft die Wartezeit `awaitTerminateSeconds` dagegen ab, bleibt die alte Reihenfolge: dann laufen Producer noch und füllen die Warteschlange; ein zuerst gestoppter Consumer ließe sie an der vollen Warteschlange hängen, und `interrupt()` wartete deren ganze `shutdownTimeoutSeconds` (so lief der erste Fix-Versuch in den Fork-Timeout von `FinderTest`, über `shutdownAndAwaitTermination_producersSetAndInitialized_shutdownCalledAndAwaitTermination`; nicht gepusht). Dazu zwei paketinterne Nähte im Finder (`addResultListener`, `setBeforeResultBroadcastersClosed`), verhaltensneutral.
- **Reproduktion:** `FinderTest#shutdownAndAwaitTermination_consumerStillDraining_everyBatchIsReportedBeforeTheBroadcastersClose` – vier Producer mit je einem Batch, ein Consumer-Thread, ein Test-Listener hält jeden Batch 200 ms; die Naht hält fest, wie viele Batches beim Schließen der Broadcaster schon gemeldet waren.
- **Rot-Grün-Nachweis:** gegen den Naht-Stand ohne Fix rot („Expected: is <4> but: was <0>“, die übrigen 26 Tests der Klasse grün), mit Fix grün in 3 von 3 Läufen; Gegenprobe durch Zurückkopieren des Naht-Stands rot („but: was <0>“), danach wieder grün. Breite Liste: `FinderTest` 27/27, `ConsumerJavaTest` 36/36, die drei Broadcaster-Tests, `ConfigFixturesParseTest` 4/4, `BitcoinAddressFinderArchitectureTest` 17/17; `MainTest` 18/18 mit `POCL_MAX_WORK_GROUP_SIZE=256` (ohne die Variable stirbt seine OpenCL-Roundtrip-Fork auf pocl, auch gegen den unveränderten Stand – siehe die pocl-Beobachtung in BUGHUNT-CHECKED.md); `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `b4bcdb1`
- **Nachtrag (Branch-Review 2026-10-20):** Die Zusicherung galt nur auf dem Abschlusspfad. Nach einem Ctrl-C schloss der Shutdown-Hook über `interrupt()` die Broadcaster (und stoppte den WebSocket-Endpunkt), während der Main-Thread den Consumer noch leeren ließ; behoben als [F206](#f206): die Broadcaster schließen jetzt auf jedem Pfad erst nach `releaseConsumer()`.

<a id="f132"></a>
### F132 – BitcoinAddressFinder: `CompactLMDB` löscht die `data.mdb` einer anderen Datenbank im Zielverzeichnis und scheitert danach
- **Zeit:** 2026-10-18 20:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBCompact.java` (`run`: Löschen von `data.mdb` vor `compactTo`)
- **Schwere:** mittel
- **Beschreibung:** `run()` löscht eine vorhandene `data.mdb` im Ziel („replace a previous compaction“) und ruft dann `Env.copy(..., MDB_CP_COMPACT)`, das ein Verzeichnis ohne Dateien verlangt. Eine frühere Kompaktierung ist eine einzelne `data.mdb` (die prüfende Öffnung danach ist read-only mit `MDB_NOLOCK` und legt keine Lock-Datei an) und wird ersetzt. Liegt im Ziel dagegen eine beschreibbar geöffnete Datenbank – `data.mdb` plus `lock.mdb`, etwa ein früheres Import-Ziel –, wird ihre `data.mdb` gelöscht und der Lauf scheitert anschließend mit `Env$InvalidCopyDestination: Path must contain no files`; zurück bleibt nur `lock.mdb`. Ende zu Ende über die CLI gemessen (Exit 1, Ziel danach nur `lock.mdb`). Dasselbe gilt für jede andere Datei im Ziel.
- **Fix:** Vor dem Löschen prüft `run()` den Inhalt des Ziels; jede Datei außer `data.mdb` bricht mit `IllegalStateException` ab, die die Datei nennt, bevor etwas gelöscht wird. Eine frühere Kompaktierung wird wie bisher ersetzt. README-Abschnitt „compact the finished database“ ergänzt.
- **Reproduktion:** `LMDBCompactTest#compactLMDB_targetHoldsAnotherDatabase_keepsItsDataFile` – das Ziel erhält `data.mdb` und `lock.mdb` der Testdatenbank; erwartet wird, dass `data.mdb` danach unverändert vorhanden ist.
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <true> but: was <false>“, die übrigen 5 Tests der Klasse grün), mit Fix 6/6 (skipped 0). Der Schutztest `compactLMDB_targetHoldsAPreviousCompaction_isReplaced` (zweiter Lauf auf dasselbe Ziel) ist in beiden Ständen grün. `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:check` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `4a05732`

<a id="f135"></a>
### F135 – BitcoinAddressFinder: `TuneConfiguration` erweitert nicht, wenn `keysPerWorkItem` das Raster füllt, obwohl `batchSizeInBits` am Rand liegt
- **Zeit:** 2026-10-18 21:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java:1530` (`nextExtensionArm`)
- **Schwere:** niedrig
- **Beschreibung:** Liegt der Gewinner auf dem größten gemessenen `keysPerWorkItem`, prüft `nextExtensionArm` nur die Verdopplung; passt sie nicht ins Raster (`keysPerWorkItem > 2^batchSizeInBits`), gibt die Methode `null` zurück und prüft die Achse `batchSizeInBits` gar nicht mehr, auch wenn der Gewinner dort ebenfalls am Rand unterhalb von 24 liegt. Die Javadoc von `winnerBoundaryNote` sagt dagegen, jeder andere Rand werde jetzt automatisch erweitert. Ende zu Ende über die CLI auf pocl gemessen: `batchSizeInBitsCandidates` [8], `keysPerWorkItemCandidates` [256] meldet ohne Fix den einzigen Arm (8, 256) als Gewinner, ohne Erweiterung und ohne Hinweis; mit Fix misst der Lauf (9, 256) und danach (9, 512). Mit den mitgelieferten Listen (18..24 Bits, höchstens 256 × 2^4) tritt der Fall nicht auf, nur mit eigenen kleinen Listen.
- **Fix:** Passt die Verdopplung nicht ins Raster, fällt die Methode zur Prüfung von `batchSizeInBits` durch, statt `null` zurückzugeben.
- **Reproduktion:** `TuneConfigurationTest#nextExtensionArm_keysPerWorkItemFillsTheGridAtTheLargestBatchSize_continuesByOneBit`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is not null but: was null“, die übrigen Tests der Klasse grün), mit Fix 50 Tests, 0 Fehler (1 übersprungen: `run_noProducerConfiguredButGpuPresent_sweepsTheDetectedDevices`, in beiden Ständen), Gegenprobe per `git stash` rot, danach wieder grün; `nextExtensionArm_doublingWouldExceedTheGrid_doesNotContinue` (Gewinner nicht am Bit-Rand) bleibt grün. `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:check` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `8c36e8c`

<a id="f136"></a>
### F136 – BitcoinAddressFinder: `TuneConfiguration` misst mit `sweepFilterTypes` auf dem ersten Gerät das Raster des globalen Gewinners und schreibt es in dessen Producer
- **Zeit:** 2026-10-18 22:05 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java` (`resolveFilterType` → `measureFilterType(templates.get(0), …, winner)`)
- **Schwere:** mittel
- **Beschreibung:** Nach dem Sweep bekommt jedes Gerät seinen eigenen Gewinner (`applyWinnerTo`, mit dem Kommentar, ein gemeinsamer Gewinner gäbe der langsameren Karte die Werte der schnelleren). Mit `sweepFilterTypes` misst `measureFilterType` die beiden Filterbreiten aber auf dem Producer des ersten Geräts mit dem globalen Gewinner (`winner` = schnellster Arm über alle Geräte). `runArm` schreibt dessen `batchSizeInBits`/`keysPerWorkItem` in den Producer des ersten Geräts, und die ausgegebene Konfiguration trägt für dieses Gerät dann das Raster eines anderen Geräts – genau der Fehler, den `applyWinnerTo` verhindern soll; zudem wird die Filterbreite an einem Raster gemessen, das auf diesem Gerät nie gewann oder gar scheiterte. Nur mit mehreren Geräten und `sweepFilterTypes`.
- **Fix:** Die Filterbreiten werden am eigenen Gewinner des ersten Geräts gemessen (`filterSweepArm`); hat es keinen, wird die Breite abgeleitet. Die Naht `filterSweepArm` wurde zuerst verhaltensneutral eingezogen (globaler Gewinner über alle Geräte-Arme, wie `pickWinner(results)`).
- **Reproduktion:** `TuneConfigurationTest#filterSweepArm_twoDevices_usesTheFirstDevicesOwnWinner`
- **Rot-Grün-Nachweis:** gegen den Naht-Stand rot („Expected: is sameInstance(<ArmResult[batchSizeInBits=22, keysPerWorkItem=256, …]>) but: was <ArmResult[batchSizeInBits=24, keysPerWorkItem=2048, …]>“, die übrigen Tests der Klasse grün), mit Fix 51 Tests, 0 Fehler (1 übersprungen, in beiden Ständen), Gegenprobe durch Zurückkopieren des Naht-Stands rot, danach wieder grün. Ende zu Ende über die CLI auf pocl mit `sweepFilterTypes` (ein Gerät): beide Filterarme laufen am Gewinner (10, 4), Empfehlung „MEASURED empirically“. `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:check` und `spotbugs:check` grün.
- **CI-Bedarf:** Ein Lauf mit zwei echten Geräten ist hier nicht möglich (pocl stellt eines).
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `7aa8f4c`

<a id="f139"></a>
### F139 – BitcoinAddressFinder: Scheitert der Find-Start nach dem Consumer-Start, beendet sich die JVM nie
- **Zeit:** 2026-10-18 23:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/cli/Main.java` (`run`, Fall `Find`), `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java` (`interrupt` gibt den Consumer nicht frei)
- **Schwere:** mittel
- **Beschreibung:** `Main.run` startet Key-Producer, Consumer (LMDB laden, Consumer-Threads, Statistik-Timer) und dann die Producer. Wirft ein Schritt nach `consumerJava.startConsumer()`, ruft der Fehlerpfad nur `interrupt()` auf, und `Finder.interrupt()` gibt Key-Producer, Broadcaster und Producer frei, nicht den Consumer – das geschieht sonst in `shutdownAndAwaitTermination`, das dieser Pfad nie erreicht. Die Consumer-Threads (Nicht-Daemon, fester Pool) laufen weiter, und da `Main` kein `System.exit` ruft, hängt der Prozess nach der Fehlermeldung unbegrenzt. Ende zu Ende über die CLI gemessen, je 60–120 s bis zum Timeout-Abbruch: `producerOpenCL` mit `deviceIndex` 5 („Invalid device index: 5“, aus `configureProducer`) und `printStatisticsEveryNSeconds` 0 (aus `startStatisticsTimer`, nach `startConsumer`). Skripte und Dienste, die auf das Prozessende warten, hängen damit bei jedem Konfigurations- oder Gerätefehler dieser Phase.
- **Fix:** `Main` fängt einen Fehler der Startschritte ab, ruft das neue `Finder.abortStart()` (erst `interrupt()`, dann der Consumer – dieselbe Reihenfolge wie bei abgelaufener Wartezeit in `shutdownAndAwaitTermination`) und wirft weiter; der äußere Fehlerpfad bleibt unverändert.
- **Reproduktion:** `MainTest#run_findStartFailsAfterTheConsumerStarted_leavesNoNonDaemonThreadBehind` (Find mit Test-LMDB und `printStatisticsEveryNSeconds` 0; danach darf binnen 10 s kein neuer Nicht-Daemon-Thread mehr leben)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is an empty collection but: <[Thread[#33,pool-6-thread-2,5,main], Thread[#32,pool-6-thread-1,5,main]]>“), mit Fix grün; Gegenprobe per `git stash` beider Produktionsdateien rot, danach wieder grün. Breit: `MainTest` 19/19 (mit pocl und `POCL_MAX_WORK_GROUP_SIZE=256`, 0 übersprungen), `FinderTest` 27/27, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:check` und `spotbugs:check` grün. Ende zu Ende mit Fix: beide CLI-Fälle enden nach 1–2 s mit Exit 1.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `021c672`

<a id="f140"></a>
### F140 – BitcoinAddressFinder: `TuneConfiguration` hängt nach einer unbekannten Key-Producer-ID (Folge von F126)
- **Zeit:** 2026-10-18 23:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java` (`run`: `consumer.startConsumer()` und `finder.startKeyProducer()` vor dem `try`)
- **Schwere:** mittel
- **Beschreibung:** `run()` startete die Consumer-Threads und `finder.startKeyProducer()` vor dem `try`, dessen `finally` Finder, Consumer und Producer-Executor abräumt. Seit F126 prüft `startKeyProducer` die Key-Producer-IDs und `batchSizeInBits` aller Producer und wirft dabei; vorher scheiterte eine falsche ID erst im einzelnen Arm (innerhalb des `try`, als gescheiterter Arm berichtet). Jetzt endet `run()` mit `KeyProducerIdUnknownException`, ohne den Consumer zu stoppen, und dessen Nicht-Daemon-Thread hält die JVM am Leben. Ende zu Ende über die CLI auf pocl: `producerOpenCL.keyProducerId` „typo“ → Fehlermeldung, dann hängt der Prozess bis zum Timeout-Abbruch (60 s). Dasselbe Muster wie F139, hier durch den eigenen Fix F126 ausgelöst.
- **Fix:** `consumer.startConsumer()` und `finder.startKeyProducer()` laufen im `try`, sodass das vorhandene `finally` auch diesen Fehler abräumt.
- **Reproduktion:** `TuneConfigurationTest#run_unknownKeyProducerId_leavesNoNonDaemonThreadBehind`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is an empty collection but: <[Thread[#27,pool-3-thread-1,5,main]]>“), mit Fix grün; Gegenprobe per `git stash` rot, danach wieder grün. `TuneConfigurationTest` 52 Tests, 0 Fehler (1 übersprungen, wie zuvor), `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:check` und `spotbugs:check` grün. Ende zu Ende mit Fix: Exit 1 nach 2 s.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `1c27475`

<a id="f141"></a>
### F141 – BitcoinAddressFinder: `TRUNCATED_LONG_64` meldet Kollisionen als Treffer; die versprochene Auflösung über LMDB gibt es nicht
- **Zeit:** 2026-10-19 00:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/persistence/inmemory/TruncatedLong64SortedArrayPresence.java` (Klassen-Javadoc, `requiresBackend()`), `configuration/AddressLookupBackend.java` (Javadoc zu `TRUNCATED_LONG_64`), Verdrahtung in `consumer/ConsumerJava.java:657` und `:460`
- **Schwere:** niedrig
- **Beschreibung:** Die Klasse speichert je Adresse nur Byte 0 (Bucket) und die Bytes 1–8 (72 Bit). Beide Javadocs versprechen, eine Kollision werde „disambiguated by the LMDB backend that this lookup sits in front of“ bzw. „the enclosing chain must fall through to the LMDB delegate“. Der Code tut das Gegenteil: `requiresBackend()` ist `false`, `ConsumerJava` verdrahtet die Klasse ohne Accelerator (anders als Bloom/Fuse) und schließt LMDB nach dem Aufbau. Eine Adresse, die in den ersten 9 Byte mit einem Eintrag übereinstimmt, gilt als Treffer und wird als „hit: Found the address“ gemeldet. Gemessen mit einer Probe: ein Eintrag, Abfrage mit geänderten Bytes 12 und 19 → `containsNonMember=true`, `requiresBackend=false`. Dazu sind die genannten Wahrscheinlichkeiten widersprüchlich: Klasse „N / 2^64“ (≈ 7,5·10⁻¹¹), Enum „N/2⁴⁴“ und „~10⁻¹¹“; bei 72 Bit wären es N/2⁷² ≈ 3·10⁻¹³ je Abfrage bei 1,47 Mrd. Einträgen. Praktisch selten, aber ein falscher Treffer meldet einen Schlüssel als Fund, der keine Adresse der Datenbank besitzt.
- **Historie (vollständig, nach `git fetch --unshallow`):**
  - `78e4d7c` (2026-05-29 21:43) führt `requiresBackend()` ein: Decorators (Bloom) `true`, Ersatz-Backends (HashSet, SortedArray) `false`, damit der Orchestrator LMDB schließen kann.
  - `ba51113` (23:06) verdrahtet das in `ConsumerJava`: `if (!chain.requiresBackend()) lmdb.close()`.
  - `7edcb25` (23:52) fügt `TRUNCATED_LONG_64` als Ersatz-Backend hinzu, `requiresBackend()` von Anfang an `false`, im README-Tabelle „LMDB stays open? **no**“. Im selben Commit behaupten aber Klassen-Javadoc, Enum-Javadoc, README-Absatz und Commit-Nachricht „any collision falls through to the LMDB delegate“; die Rate steht als N/2⁶⁴ (≈7,5·10⁻¹¹) neben „72 effective bits“, das Enum zusätzlich als N/2⁴⁴. Das Verhalten (ungeprüfter Treffer) gab es also vom ersten Commit an, der Widerspruch auch.
  - `2befeed` (2026-06-15) korrigiert das README („surfacing such a collision as an unverified hit is harmless (LMDB is closed after population)“) und die Javadocs von Fuse-8/-16, übersieht aber die Javadocs von `TruncatedLong64SortedArrayPresence` und `AddressLookupBackend`; die Rate N/2⁶⁴ bleibt.
  - `b4f7a2f` (2026-06-15) macht Fuse zum prüfenden Decorator und hält ausdrücklich fest: „keeping the TRUNCATED_LONG_64 wording, which IS legitimately self-contained“. Damit ist Richtung (a) die dokumentierte Entscheidung.
  - Seitdem hat kein Commit die beiden Javadoc-Sätze berührt.
- **Fix:** Richtung (a), Doku an das gewollte Verhalten angepasst: Klassen-Javadoc (neuer Abschnitt „False positives are reported as hits“), Enum-Liste und Enum-Konstante, README-Absatz und `TODO.md` nennen jetzt N/2⁷² ≈ 3·10⁻¹³ je Abfrage und sagen, dass eine Kollision ungeprüft als Treffer gemeldet wird. `BloomFilterAccelerator` (N/2⁶⁴) ist richtig, dort sind es 8 Byte.
- **Reproduktion / Verhaltenstests (grün, halten das Verhalten fest):**
  - `TruncatedLong64SortedArrayPresenceTest#nonMemberDifferingInOneOfTheFirstNineBytes_isAbsent` (Byte 0–8, je ein Fall)
  - `…#nonMemberDifferingOnlyAfterByteEight_isReportedAsPresent` (Byte 9–19, je ein Fall)
  - `…#nonMemberDifferingInEveryByteAfterByteEight_isReportedAsPresent`
  - `ConsumerJavaTest#consumeOneCycle_addressCollidingInTheFirst72Bits_isAnUnverifiedHitForTruncatedLong64` (LMDB geschlossen, `hits=1`)
  - `ConsumerJavaTest#consumeOneCycle_addressCollidingInTheFirst72Bits_isNoHitForAnExactBackend` (LMDB_ONLY, BLOOM, HASHSET, BINARY_FUSE_8/16, BLOCKED_BLOOM: `hits=0`)
  - End to End vorab über die CLI: Datenbank mit einer konstruierten Adresse (`12vieiAHxBe4qH5DDVbEKgPV6EQ8NsJjxi`, gleiche 9 Byte wie die Adresse von Schlüssel 12345) → `LMDB_ONLY` `hits=0`, `TRUNCATED_LONG_64` `hits=1` mit „hit: Found the address … 12vieiAHxBe4qCUrwvfb2kRkDuc8kQ2VZ2“.
- **Gegenprobe:** `TRUNCATED_LONG_64` testweise auf `HashSetAddressPresence` verdrahtet → der Consumer-Test wird rot (`Expected: is <1L> but: was <0L>`); zurückgesetzt.
- **Rot-Grün-Nachweis:** – (Doku-Korrektur; Tests halten das Verhalten fest, Freigabe des Nutzers für Doku ohne roten Test)
- **Status:** gefixt (Doku)
- **Commit:** e57f3e0

<a id="f142"></a>
### F142 – BitcoinAddressFinder: Javadoc von `putNewAmountWithAutoIncrease` beschreibt noch den Zustand vor F27
- **Zeit:** 2026-10-19 00:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/persistence/lmdb/LMDBPersistence.java:483` (Javadoc von `putNewAmountWithAutoIncrease`)
- **Schwere:** niedrig
- **Beschreibung:** Die Javadoc sagt, nach `MapFullException` werde die Map um `increaseSizeInMiB` vergrößert „and the insert is retried once“, und „if the second attempt also fails, the configured increase size is too small“. Seit F27 wächst die Map in einer Schleife über `growMapOrRethrow`, bis der Eintrag passt; geworfen wird nur noch bei `increaseMapAutomatically = false` oder `increaseSizeInMiB <= 0`. Ende zu Ende bestätigt: Import von 50 000 Adressen mit `initialMapSizeInMiB` 1 und `increaseSizeInMiB` 1 läuft durch (Exit 0, „50000 written“, `data.mdb` auf 5 MiB gewachsen). Reine Doku-Abweichung, das Verhalten ist das bessere.
- **Historie:** Die Javadoc kam mit `b46c6ca` (2025-11-23) und stimmte damals: einmal vergrößern, einmal wiederholen, beim zweiten `MapFullException` ein Konfigurationsproblem. Veraltet ist sie durch meinen eigenen F27-Fix `8260810` (2026-10-17), der beide Pfade auf die Schleife über `growMapOrRethrow` umgestellt und die Javadoc von `CLMDBConfigurationWrite.increaseSizeInMiB` angepasst, diese private Javadoc aber übersehen hat. Fließtext außerhalb des Codes (README, docs) beschreibt das Wachstum nicht; `examples/config_AddressFilesToLMDB.json` (`increaseSizeInMiB: 1`) passt.
- **Auswirkung:** keine für Nutzer. Die Methode ist `private`, ihre Javadoc steht nicht in der veröffentlichten API; die öffentliche Konfigurations-Javadoc ist richtig.
- **Fix:** Javadoc beschreibt die Wiederholung „as often as needed“ und das Werfen ohne Vergrößerung bei `increaseMapAutomatically = false` oder `increaseSizeInMiB <= 0`.
- **Lücke geschlossen:** Der dokumentierte Vertrag „`0` disables growth“ hatte keinen Test. Neu: `LMDBPersistenceTest#putNewAmount_increaseSizeZero_fullMapThrowsWithoutResizing` und `#putNewAmounts_increaseSizeZero_fullMapThrowsWithoutResizing` (`MapFullException`, `getIncreasedCounter() == 0`, Map bleibt 1 MiB, Timeout 20 s).
- **Gegenprobe:** `|| config.increaseSizeInMiB <= 0` testweise aus `growMapOrRethrow` entfernt → beide Tests rot (Endlosschleife, Timeout); zurückgesetzt. Die Prüfung trägt also das Verhalten.
- **Bestehend abgedeckt:** `#putNewAmounts_batchLargerThanOneIncreaseStep_mapGrowsUntilTheBatchFits` (Schleife, Batch), `#putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_increaseDatabaseSizeAndNoExceptionThrown` (Wachstum, Einzelpfad), `#putNewAmount_initialLMDBSetTo1MiB_fillWithTooMuchValues_exceptionThrown` (`increaseMapAutomatically = false`).
- **Rot-Grün-Nachweis:** – (Doku-Korrektur; die neuen Tests halten den Vertrag fest)
- **Status:** gefixt (Doku)
- **Commit:** bb40b64

<a id="f144"></a>
### F144 – BitcoinAddressFinder: Javadoc von `CProducerOpenCL.enableGpuFilter` bindet den GPU-Filter noch an `BINARY_FUSE_8`
- **Zeit:** 2026-10-18 18:30 UTC
- **Schwere:** niedrig
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/configuration/CProducerOpenCL.java` (Javadoc von `enableGpuFilter`)
- **Beschreibung:** Die Javadoc sagt, der Filter werde nur hochgeladen, „when … the consumer's address-lookup backend is `BINARY_FUSE_8`“, und nennt nur Fuse-8. Das stammt aus `e720fb9` (2026-06-11). Seit `5323c04` (2026-06-15, „decouple the GPU pre-filter from the CPU lookup“) baut `ConsumerJava.initLMDB()` den Filter für jedes Backend aus dem offenen LMDB (`Finder.isGpuFilterRequested()` prüft nur `enableGpuFilter && !transferAll` und Vanity), und seit `2bb0a0b` gibt es Fuse-16. README Zeile 672 und `CLMDBConfigurationReadOnly` sagen es richtig.
- **Fix:** Javadoc beschreibt Breite über `gpuFilterType`, Unabhängigkeit vom Backend und dass die Überlebenden vom Backend des Consumers geprüft werden.
- **Reproduktion:** Lesen gegen `Finder.isGpuFilterRequested`, `ConsumerJava.initLMDB`; bestehend abgedeckt durch `ConsumerJavaTest#gpuFilter_selfContainedBackend_builtBeforeLmdbClosed` (HASHSET) und die LMDB_ONLY-Fälle.
- **Rot-Grün-Nachweis:** – (Doku-Korrektur, Freigabe des Nutzers für Doku ohne roten Test)
- **Status:** gefixt (Doku)
- **Commit:** ee958e3

<a id="f145"></a>
### F145 – BitcoinAddressFinder: README nennt die Empfehlung „Defaults“
- **Zeit:** 2026-10-18 18:30 UTC
- **Schwere:** niedrig
- **Datei:** `README.md` („Which filter to pick“ und die Log-Erklärung zu `gpuFilterType`)
- **Beschreibung:** „There are two independent knobs, and the defaults are:“ leitet eine Tabelle mit der Spalte „Recommended“ ein (`FUSE_16` + `BINARY_FUSE_8`); fünf Zeilen davor sagt das README selbst, der Default sei `LMDB_ONLY`. Ebenso „the recommended default `FUSE_16`“. Im Code: `enableGpuFilter = false`, `gpuFilterType = FUSE_8`, `addressLookupBackend = LMDB_ONLY`. Wer die Tabelle als Defaults liest, glaubt, ohne Konfiguration mit `FUSE_16` zu laufen, und bleibt auf schnellen Karten bei der FUSE_8-Grenze (~0,5–0,7 G Schlüssel/s auf gemieteten Hosts). Den Default selbst auf `FUSE_16` zu ändern ist eine offene Entscheidung in `TODO.md` (Option c des Tuner-Eintrags), hier nicht angefasst.
- **Fix:** beide Sätze nennen die Code-Defaults und die Empfehlung getrennt.
- **Reproduktion:** Lesen gegen `CProducerOpenCL` und `CLMDBConfigurationReadOnly`.
- **Rot-Grün-Nachweis:** – (Doku-Korrektur, Freigabe des Nutzers für Doku ohne roten Test)
- **Status:** gefixt (Doku)
- **Commit:** ee958e3

<a id="f146"></a>
### F146 – BitcoinAddressFinder: `TRUNCATED_LONG_64` wird an vier Stellen „exakt“ genannt
- **Zeit:** 2026-10-18 18:30 UTC
- **Schwere:** niedrig
- **Datei:** Javadoc von `BinaryFuse8AddressPresence` und `BinaryFuse16AddressPresence` („Unlike the exact snapshots (`HASHSET`, `TRUNCATED_LONG_64`)“), `CLMDBConfigurationReadOnly.addressLookupBackend` („the exact snapshots“), `docs/filter-selection.md` („reference point for exact in-RAM storage“)
- **Beschreibung:** `TRUNCATED_LONG_64` vergleicht 72 von 160 Bit und meldet Kollisionen ungeprüft (F141); exakt ist unter den Snapshots nur `HASHSET`. Gemeint war an allen Stellen „self-contained“ (LMDB geschlossen).
- **Fix:** „self-contained snapshots“ bzw. „in-RAM snapshots `HASHSET` and `TRUNCATED_LONG_64`“; in `filter-selection.md` „72 of its 160 bits, so not exact“.
- **Reproduktion:** die Verhaltenstests von F141.
- **Rot-Grün-Nachweis:** – (Doku-Korrektur, Freigabe des Nutzers für Doku ohne roten Test)
- **Status:** gefixt (Doku)
- **Commit:** ee958e3

<a id="f147"></a>
### F147 – BitcoinAddressFinder: GPU-Eintrag in `TODO.md` empfiehlt noch `TRUNCATED_LONG_64` als ersten GPU-Filter
- **Zeit:** 2026-10-18 18:30 UTC
- **Schwere:** niedrig
- **Datei:** `TODO.md` („GPU-side no-false-negatives address filter with per-variant flag bitmask“)
- **Beschreibung:** Der Eintrag (aus `7bf7e74`/`5758808`, 2026-05-30) beschreibt die sortierten TRUNCATED-Buckets im GPU-Speicher, „Today the GPU kernel returns raw hash160 bytes for every candidate“, und empfiehlt „use TRUNCATED_LONG_64 … for the first GPU implementation“. Gebaut wurde stattdessen Binary Fuse: Fuse-8-Kernel mit Compact-Ausgabe `62f831d` (2026-06-11), Fuse-16 `2bb0a0b` (2026-07-19), entkoppelt vom CPU-Backend `5323c04`. TRUNCATED wurde nie auf die GPU gebracht, das Flags-Byte je Variante nicht gebaut (der Kernel verknüpft beide Lookups mit ODER).
- **Fix:** Status-Hinweis am Anfang des Eintrags (was wirklich gebaut ist, Verweis auf den offenen Tuner-Eintrag) und der Empfehlungs-Punkt als „Decision (taken differently)“; der übrige Entwurf bleibt als Aufzeichnung.
- **Reproduktion:** `src/main/resources/inc_ecc_secp256k1custom.cl` (`fuse8_contains`/`fuse16_contains`, kein Truncated-Probe), echte Kernel-Tests `OpenCLFilterHitSetIntegrationTest` und `OpenCLCompactOutputIntegrationTest` auf pocl grün.
- **Rot-Grün-Nachweis:** – (Doku-Korrektur, Freigabe des Nutzers für Doku ohne roten Test)
- **Status:** gefixt (Doku)
- **Commit:** ee958e3

<a id="f148"></a>
### F148 – BitcoinAddressFinder: Nach dem Kernel-Bau mit pocl gehört der SIGSEGV-Handler LLVM; gleichzeitige Safepoints töten die JVM ohne `hs_err`
- **Zeit:** 2026-10-18 19:20 UTC
- **Datei:** keine im Projekt; betroffen sind alle Starts mit `producerOpenCL` auf pocl (`examples/run_Find_1OpenCLDevice.sh`, `run_Find_1OpenCLDeviceAnd2CPUProducer.sh`, `run_OpenCLInfo.sh`, der CI-Job `test-opencl`)
- **Schwere:** mittel (Absturz der JVM ohne Diagnose; ausgelöst durch Laufzeitverhalten, nicht durch Eingaben)
- **Beschreibung:** Beim Bau des Kernels lädt pocl 5.0 `libLLVM-16.so.1`, das die Handler für SIGSEGV, SIGBUS, SIGFPE und SIGILL durch eigene ersetzt (`-Xcheck:jni`: „SIGSEGV handler modified!“, Handler `in libLLVM-16.so.1`, Flags `SA_ONSTACK|SA_RESETHAND|SA_NODEFER`). HotSpot braucht SIGSEGV selbst (implizite Null-Prüfungen, Safepoint-Polls kompilierter Schleifen über eine geschützte Seite). Ein einzelnes solches Signal geht gut: LLVMs Handler setzt die alten Handler zurück, das Signal kommt erneut und landet bei der JVM (100 provozierte NPEs nach dem Kernel-Bau überlebt). Faulten aber mehrere Threads gleichzeitig, trifft der zweite wegen `SA_RESETHAND` den Default-Handler, bevor LLVM zurückgesetzt hat, und der Prozess stirbt mit SIGSEGV (Exit 139), ohne `hs_err` (die JVM sieht das Signal nie). Genau das war der eine Absturz bei F129: 8 Consumer-Threads mitten in der Trefferverarbeitung, als `Finder.interrupt()` den Abbau begann.
- **Reproduktion:** Probe `SigProbe3` (Scratchpad `sigprobe/`): 8 Threads in heißen Schleifen, danach 2000-mal `System.gc()` + `Thread.getAllStackTraces()` (gleichzeitige Safepoints). Ohne Kernel-Bau 5/5 überlebt, nach `OpenCLContext.init()` auf pocl 5/5 SIGSEGV, mit `LD_PRELOAD=$JAVA_HOME/lib/libjsig.so` (Signalverkettung des JDK) 5/5 überlebt. Normale `Find`-Läufe treffen das Fenster selten: 1 von 70 (danach 60 in Folge sauber).
- **Nicht geprüft:** echte GPU-Treiber. Ob NVIDIA/AMD/Intel-Treiber ebenfalls Handler setzen, ist offen; AMDs OpenCL nutzt LLVM (comgr) im Prozess und ist ein Kandidat. Windows ist nicht betroffen (keine POSIX-Signale), macOS hätte `libjsig.dylib`.
- **Fix:** Entscheidung des Nutzers (Variante A). Die drei Linux-Startskripte für OpenCL (`run_Find_1OpenCLDevice.sh`, `run_Find_1OpenCLDeviceAnd2CPUProducer.sh`, `run_OpenCLInfo.sh`) laden `libjsig.so` des verwendeten JDK vor, wenn es existiert; der CI-Job `test-opencl` setzt `LD_PRELOAD=$JAVA_HOME/lib/libjsig.so` (geerbt von den Surefire-Forks) und prüft, dass die Datei da ist; `OpenCLContext.init()` warnt vor dem Kernel-Bau auf Linux, wenn `libjsig` nicht vorgeladen ist (`SignalChainingCheck`, reine Entscheidung über `os.name`, `LD_PRELOAD`, `java.home`); README „Known Issues → OpenCL on Linux: signal chaining (`libjsig`)“. Nebenbei korrigiert: der CI-Kommentar „the known pocl SIGSEGV in the secp256k1 kernel“ meinte den in `bc9d922` behobenen Lesezugriff. `TODO.md`: „Check whether GPU OpenCL drivers replace the JVM's signal handlers (pocl does)“.
- **Reproduktion / Tests:** `SignalChainingCheckTest` (5: Linux ohne/mit fremdem/mit `libjsig`-Preload, Windows, macOS). Vorher rot (Klasse fehlt); Gegenprobe über die Logik: `libjsig`-Prüfung entfernt → `warning_linuxWithLibjsigPreloaded_isEmpty` rot. PIT gezielt: 4/4 Mutanten getötet. Ende zu Ende: `Find` ohne Preload loggt die Warnung einmal, mit Preload nicht, beide 682/682 Treffer; die Probe mit dem `LD_PRELOAD`, das der Skript-Block setzt, 3/3 überlebt.
- **Regression:** OpenCL-nahe Tests mit vorgeladenem `libjsig` auf pocl: 289 Tests, 0 Fehler (23 übersprungen aus bestehenden Gründen); Architektur-, `JvmModuleFlagConsistencyTest`, `ExampleRunScriptJarVersionTest`, SpotBugs, Release-Javadoc grün; `check-run-scripts.py`, `check-shared-files.py` und YAML-Prüfung der Workflows grün.
- **CI-Bedarf:** der `test-opencl`-Job mit dem neuen `LD_PRELOAD`; Messung auf echten GPU-Treibern laut `TODO.md`.
- **Rot-Grün-Nachweis:** siehe Tests.
- **Status:** gefixt
- **Commit:** 9975403
- **Nachtrag (Branch-Review 2026-10-20):** Die Ende-zu-Ende-Aussage „loggt die Warnung einmal“ galt nur für einen `Find` mit einem Producer; der Tuner (ein Kernel je Arm) und ein `Find` mit mehreren OpenCL-Producern loggten sie je Kontext, und `run_TuneConfiguration.sh` lud `libjsig` nicht vor – behoben als [F210](#f210). Die Prüfung entscheidet seit [F209](#f209) über `/proc/self/maps` statt über den `LD_PRELOAD`-String.

<a id="f150"></a>
### F150 – BitcoinAddressFinder: Der `HexHash`-Export lässt sich nicht wieder importieren; 7,6 % seiner Zeilen werden zu falschen Hashes
- **Zeit:** 2026-10-18 20:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java` (`fromLine`)
- **Schwere:** niedrig (betrifft nur den Re-Import eines Exports; aber still: falsche Hashes statt einer Ablehnung)
- **Historie:** `HexHash` gibt es seit `978a6d8` (2021). Die README beschreibt den Zweck als Ansicht („Best viewed in fixed-width hex viewers (e.g., HxD)“, die Download-Hinweise „open with HxD, set 42 bytes each line“); einen Re-Import verspricht sie nicht. Der „Roundtrip“-Test `f872389` (`MainTest#testRoundtrip_…FindSecretsFile`) geht Import → Export `HexHash` → Find, liest den Export also nie wieder ein. Einen Test, der eine exportierte Datei wieder importiert, gab es für kein Format.
- **Beschreibung:** `AddressTxtLine.fromLine` kannte kein reines Hex-hash160 (nur das Riecoin-ScriptPubKey `76a914…`). Eine 40-stellige Hex-Zeile fiel bis zum Base58-Fallback durch: enthält sie eine `0` (nicht im Base58-Alphabet), wird sie verworfen, sonst (Anteil (15/16)^40 ≈ 7,6 %) zu einem **falschen** hash160 dekodiert und importiert, weil Prüfsummenfehler laut Bulk-Import-Vertrag angenommen werden. Ein Export im Format `HexHash` ergab beim Import also eine fast leere Datenbank mit falschen Einträgen.
- **Fix:** Entscheidung des Nutzers. Ein Zweig vor der Multisig-Prüfung liest genau 40 Hex-Zeichen (Groß/Klein egal) als hash160, mit optionalem Betrag wie jede Zeile. Kein anderes angenommenes Format besteht aus 40 Hex-Zeichen: Base58-Adressen sind höchstens 35 Zeichen lang, CashAddr 42, das Riecoin-Skript 46, und Bech32 trägt Zeichen außerhalb von Hex (eine Bech32-Adresse aus 40 Zeichen hätte einen leeren HRP). Die Herkunft eines Hashes (P2PKH, P2SH, P2WPKH) steckt nicht im Format, er wird als `P2PKH_OR_P2SH` eingetragen; die Datenbank speichert ohnehin nur den Hash. README: „Bare hash160 in hex“ unter den Sonderformaten, Hinweis beim Export.
- **Reproduktion / Tests:** `LMDBToAddressFileTest#exportThenImport_everyFormat_roundTripsTheHash160Set` (neu, über alle drei Exportformate: Export einer Datenbank, Import der Datei in eine neue, Vergleich der hash160-Mengen, beim Format mit Betrag auch der Beträge) – der erste Round-Trip-Test des Exports. `AddressTxtLineTest#fromLine_hexHash160_isImportedAsThatHash160` (4 Zeilen: 40 Einsen – die Base58-fähige Zeile, die vorher zu einem falschen Hash wurde –, eine echte Hash-Zeile klein und groß, mit Betrag).
- **Rot-Grün-Nachweis:** ohne Fix rot: Round-Trip für `HexHash` „but: was <[]>“ (die beiden Base58-Formate grün), Hex-Zeilen 4/4 rot („1111…“ → „but: was "0000…"“, die übrigen „invalid Base58 address“). Mit Fix grün; Gegenprobe per `git stash` der Produktionsdatei wieder rot, danach grün. Breit: `AddressTxtLineTest` 298/298, `LMDBToAddressFileTest` 16/16, `AddressFilesToLMDBTest` 26/26, `SeparatorFormatTest` 83/83, `BitcoinAddressFinderArchitectureTest` 17/17, `MainTest` 18/18 einzeln je Methode (die OpenCL-Roundtrip-Methode mit `POCL_MAX_WORK_GROUP_SIZE=256`, siehe die pocl-Beobachtung in `BUGHUNT-CHECKED.md`); `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** b5ddcec

<a id="f151"></a>
### F151 – BitcoinAddressFinder: `getMaxPrivateKeyForBatchSize` lässt ein OpenCL-Raster zu, das auf der Gruppenordnung n endet
- **Zeit:** 2026-10-18 21:05 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/util/PrivateKeyValidator.java` (`getMaxPrivateKeyForBatchSize`), Javadoc von `constants/Secp256k1Constants.MIN_VALID_PRIVATE_KEY`
- **Schwere:** niedrig (ein Rasterplatz mit einem ungültigen Schlüssel; kein Absturz, kein verpasster Schlüssel)
- **Historie:** Die Methode stammt aus `979ae5f` (Auslagerung aus `KeyUtility`, in `f4ab38c` nach `PrivateKeyValidator`). Ihr Kommentar „baseKey + (2^bits − 1) ≤ MAX_PRIVATE_KEY“ zeigt die Absicht: jeder Schlüssel des Rasters soll gültig sein. Geschrieben wurde das, als `MAX_PRIVATE_KEY` (= n) noch als größter gültiger Schlüssel galt; F45 (`fd44de7`) hat die Obergrenze exklusiv gemacht, diese Rechnung aber nicht nachgezogen. Damit wurde aus „≤ n“ ein Raster, dessen letzter Schlüssel n ist. Dieselbe vor-F45-Lesart stand noch in der Javadoc von `MIN_VALID_PRIVATE_KEY` („`[0x1, MAX_PRIVATE_KEY]`“).
- **Beschreibung:** `maxSafeKey = n − 2^b + 1`; `OpenClTask.setSrcPrivateKeyChunk` nahm also die Basis `n − 2^b + 1` an, deren Raster mit n endet. Der Kernel liefert für n·G Koordinaten, die zu keinem Eintrag passen – es wird nur ein sinnloser Schlüssel geprüft. Bei `batchSizeInBits` 0 und 1 ist das die letzte ausgerichtete Basis selbst (n−1 bzw. das Raster {n−1, n}).
- **Fix:** Entscheidung des Nutzers. `maxSafeKey = n − 2^b`: das Raster ab dem Maximum endet auf n−1. Javadoc der Methode und von `MIN_VALID_PRIVATE_KEY` (`[0x1, MAX_PRIVATE_KEY)`) korrigiert. Die drei Tests, die die alte Grenze festschrieben (`batchSize0` = n, `batchSize1` = n−1, `maxAllowedBitSize` = n − 2^255 + 1), erwarten jetzt n−1, n−2 und n − 2^255.
- **Reproduktion / Tests:** `PrivateKeyValidatorTest#getMaxPrivateKeyForBatchSize_gridFromTheMaximum_endsOnTheLastValidKey` (b = 0, 1, 2, 8, 20, 255: letzter Schlüssel des Rasters ab dem Maximum ist n−1 und gültig); `ProbeAddressesOpenCLTest#setSrcPrivateKeyChunk_gridWouldReachTheGroupOrder_throwsException` (pocl, b = 8: Basis n − 2^8 angenommen, n − 2^8 + 1 abgelehnt).
- **Rot-Grün-Nachweis:** ohne Fix rot: der neue Validator-Test 6/6 („Expected: is <…494336> but: was <…494337>“, also n statt n−1), der OpenCL-Test „Expected PrivateKeyTooLargeException to be thrown, but nothing was thrown“. Mit Fix grün; Gegenprobe per `git stash` des Validators: 9 Validator-Tests (6 neue + 3 angepasste) und der OpenCL-Test wieder rot, danach grün. Breit: `PrivateKeyValidatorTest` 28/28, `PrivateKeyTooLargeExceptionTest` 11/11, `ProducerOpenCLTest` 11/11, `KeyProducerJavaIncrementalTest`, `ProducerJavaTest`, `BitcoinAddressFinderArchitectureTest` grün; `spotless:apply`, `spotbugs:check`, Release-Javadoc grün.
- **Status:** gefixt
- **Commit:** 1498910

<a id="f152"></a>
### F152 – BitcoinAddressFinder: `TuneConfiguration.templatesForDevices` übernimmt die Gerätetyp-Maske des Prototyps, die erkannten Indizes zählen aber alle Geräte
- **Zeit:** 2026-10-18 21:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java` (`templatesForDevices`)
- **Schwere:** niedrig (latent: der einzige Produktionsaufrufer übergibt `autoDiscoveryPrototype` mit der Standardmaske `CL_DEVICE_TYPE_ALL`)
- **Historie:** `523127e` („sweep every GPU, and by default every one detected“) führte die Geräteerkennung und die Vorlagen ein. Die Erkennung zählt die Geräte einer Plattform über alle Typen hinweg (`DetectedDevice.deviceIndex` = Position unter allen Geräten), die Vorlage setzt diesen Index, kopiert aber den Rest – auch `deviceType` – per JSON-Rundreise vom Prototyp. `OpenCLPlatformSelector` zählt dagegen nur die Geräte, die die Maske treffen. Gemeint war laut Javadoc „each addressing its own device“; mit einer engeren Maske geht genau das verloren.
- **Beschreibung:** Mit einem Prototyp mit GPU-Maske auf einer Plattform, die eine CPU vor der GPU listet (pocl neben einem GPU-Treiber), adressiert die Vorlage der GPU (Index 1) ein Gerät, das es unter den GPUs nicht gibt: `Invalid device index: 1 for deviceType: 4`. Dieselbe Maske stünde auch in der ausgegebenen Konfiguration und träfe im `Find`-Lauf dasselbe Gerät nicht.
- **Fix:** Entscheidung des Nutzers. Jede Vorlage setzt `deviceType = CL_DEVICE_TYPE_ALL`, passend zum Index der Erkennung; Javadoc ergänzt.
- **Reproduktion / Tests:** `TuneConfigurationTest#templatesForDevices_prototypeWithGpuMask_templateSelectsTheDetectedDevice` (Plattform [CPU, GPU] aus Mockito-Geräten, Prototyp mit GPU-Maske, erkannte GPU an Index 1; die Vorlage wird durch den echten `OpenCLPlatformSelector` aufgelöst, wie `OpenCLContext` es tut).
- **Rot-Grün-Nachweis:** ohne Fix rot (`IllegalArgumentException: Invalid device index: 1 for deviceType: 4`), mit Fix grün; Gegenprobe per `git stash` der Produktionsdatei wieder rot, danach grün. Breit: `TuneConfigurationTest` 53 (1 übersprungen, bestehend), `OpenCLPlatformSelectorTest`, `BitcoinAddressFinderArchitectureTest` grün; `spotless:apply`, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** c2688f4

<a id="f153"></a>
### F153 – BitcoinAddressFinder: Ergebnis-Clients erfahren das größte Gitter aller Producer, auch solcher, die ihre Basen nie bekommen
- **Zeit:** 2026-10-18 21:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java` (`announceGridConfigurationToBroadcasters`), `CLAUDE.md` („The grid configuration is announced late“)
- **Schwere:** mittel (stille Lücken: ein Teil jedes Schritts eines systematischen Durchlaufs bleibt ungeprüft, ohne Meldung)
- **Historie:** Eingeführt mit der Ergebnis-Übertragung in `5479507` (2026-08-02). Die Commit-Nachricht nennt den Zweck: der Client leitet seinen Schritt aus der Ankündigung ab, „which is what stops a systematic sweep from silently rescanning one block“, und ein unbeaufsichtigter from/to-Durchlauf soll sicher sein. Die Regel „When producers disagree on the grid size the largest is announced, which is the only choice that cannot make ranges overlap“ wurde über **alle** Producer gebildet; dass ein Producer an einem anderen Key-Producer die Basen dieses Clients nie sieht, kommt darin nicht vor, und der Fall „mehrere Producer am selben Key-Producer“ wurde nur von der Überlappungsseite betrachtet.
- **Beschreibung:** Ein `producerJava` mit `keyProducerJavaRandom` und `batchSizeInBits` 8 neben einem Producer mit 4 Bit am WebSocket-Key-Producer: der WebSocket-Client erfuhr 8 und schritt um 2^8 voran, der Producer, der seine Basen bekommt, prüft ab der auf 2^4 abgerundeten Basis nur 2^4 Schlüssel – 15/16 jedes Schritts blieben ungeprüft. Dasselbe bei zwei Producern verschiedener Gitter am **selben** Key-Producer: jede Basis geht an genau einen von ihnen, der Schritt des größeren lässt beim kleineren Lücken, während der kleinere Schritt beim größeren (der die Basis abrundet) nur doppelt prüft.
- **Fix:** Entscheidung des Nutzers. Jeder Broadcaster trägt die Id seines Key-Producers und meldet nur das Gitter der Producer mit dieser `keyProducerId`; weichen sie ab, das kleinste, mit einer Warnung (doppelte Prüfung statt Lücke). Speist der Key-Producer keinen Producer, wird nichts angekündigt (Warnung). `batchUsePrivateKeyIncrement` wird ebenso nur über diese Producer gebildet. Javadoc und `CLAUDE.md` angepasst.
- **Reproduktion / Tests:** `FinderTest#configureProducer_producersWithDifferentGrids_webSocketAnnouncesTheGridItsBasesAreExpandedTo` (Ende zu Ende: echter WebSocket-Key-Producer mit `broadcastResults`, LMDB-Testdatenbank, `startKeyProducer` → `startConsumer` → `configureProducer`, dann ein echter WebSocket-Client, der den Gruß liest; Fälle: zweiter Producer mit 8 Bit an `keyProducerJavaRandom` bzw. am selben WebSocket-Key-Producer, erwartet jeweils 4).
- **Rot-Grün-Nachweis:** ohne Fix rot in beiden Fällen („Expected: is <4> but: was <8>“), mit Fix grün; Gegenprobe per `git stash` von `Finder.java` wieder 2/2 rot, danach grün. Breit: `FinderTest` 29/29, `WebSocketResultBroadcasterTest` 4/4, `ZmqResultBroadcasterTest` 4/4, `SocketResultBroadcasterTest` 8/8, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply`, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** b07e54a
- **Nachtrag (Branch-Review 2026-10-20):** Der dritte Ausgang („Speist der Key-Producer keinen Producer, wird nichts angekündigt (Warnung)“) hatte keinen Test. `FinderTest#configureProducer_broadcasterWhoseKeyProducerFeedsNoProducer_announcesNothingAndWarns`: WebSocket-Key-Producer `ws` mit `broadcastResults`, der einzige Producer an `random`; der Client verbindet vor `configureProducer`, der Test wartet, bis der Server `onOpen` ausgeführt hat (die Verbindung ist dann registriert, eine Ankündigung erreichte sie über `greetAll`), danach sendet er auf derselben Verbindung eine Markierung – sie muss die erste Meldung sein (statt auf das Ausbleiben eines `config` zu warten), und genau eine Warnung „no producer draws from it“ für `ws` muss geloggt sein. Gegenprobe am Produktionscode: Ankündigung eines Standardgitters im Zweig rot („but: was "{\"type\":\"config\",\"batchSizeInBits\":0,…}"“), Warnung entfernt rot („Expected: is <1L> but: was <0L>“), Zweig entfernt rot (`NoSuchElementException` aus `TreeSet.first()`); unverändert grün.

<a id="f154"></a>
### F154 – BitcoinAddressFinder: `TuneConfiguration` gibt für ein Gerät ohne erfolgreichen Arm das Raster seines letzten, gescheiterten Arms aus
- **Zeit:** 2026-10-18 22:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java` (`applyWinnerTo`, Geräteschleife in `run`)
- **Schwere:** niedrig (die ausgegebene Konfiguration enthält für das gescheiterte Gerät ungemessene, oft zu große Werte, die wie gemessene aussehen)
- **Historie:** `523127e` („sweep every GPU“) führte die Werte je Gerät ein; der Kommentar dort: „Each device keeps its own optimum“, die Javadoc von `applyWinnerTo`: die ausgegebene Konfiguration „describes what was measured on that device“. `runArm` schreibt jedes Arm-Raster in den Producer („mutated in place … the very one serialised into the recommendation“), `applyWinnerTo(template, null)` tat nichts. Der Test `applyWinnerTo_deviceWithoutAUsableArm_leavesTheProducerUntouched` bestand nur, weil er einen frischen Producer nahm – im Lauf hat die Schleife ihn zu diesem Zeitpunkt längst verändert.
- **Beschreibung:** Scheitern auf einem Gerät alle Arme, während ein anderes gewinnt, enthält die paste-fertige Konfiguration für das gescheiterte Gerät das Raster des letzten Arms (bei der Kandidatenliste meist das größte) statt der Werte, die der Nutzer angegeben hatte – im Bericht steht zwar „no arm produced a usable measurement“, in der Konfiguration aber nichts.
- **Fix:** Entscheidung des Nutzers. Vor dem Durchlauf eines Geräts wird sein konfiguriertes Raster festgehalten (`ConfiguredGrid`); ohne Gewinner schreibt `applyWinnerTo` es zurück und warnt („No arm succeeded on …; its entry in the recommended configuration keeps the configured values …, which were not measured.“).
- **Reproduktion / Tests:** `TuneConfigurationTest#run_noArmSucceedsOnOneDevice_emittedConfigurationKeepsThatDevicesConfiguredValues` (pocl, Ende zu Ende: zwei OpenCL-Producer, der zweite mit `deviceIndex` 99, `batchSizeInBits` 3, `keysPerWorkItem` 2; die ausgegebene Konfiguration wird mit `Main.fromJson` zurückgelesen); der bisherige Einheitstest heißt jetzt `applyWinnerTo_deviceWithoutAUsableArm_restoresTheConfiguredGrid` und verändert den Producer vorher wie die Schleife.
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <3> but: was <4>“: das Raster des gescheiterten Arms). Mit Fix grün; Gegenprobe durch Entfernen nur der Rückschreibung: beide Tests rot („was <4>“, „was <20>“), danach grün. Breit: `TuneConfigurationTest` 54 (1 übersprungen, bestehend); `spotless:apply`, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** f123f9f

<a id="f155"></a>
### F155 – BitcoinAddressFinder: Socket-Key-Producer mit `port` = `resultPort` endet als regulärer Lauf ohne einen geprüften Schlüssel
- **Zeit:** 2026-10-18 22:15 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaSocket.java` (Konstruktor), Javadoc von `CKeyProducerJavaSocket.resultPort`, README-Tabelle des Socket-Key-Producers
- **Schwere:** niedrig (Konfigurationsfehler; aber still: Exit 0 ohne Arbeit)
- **Historie:** `resultPort` kam mit der Ergebnis-Übertragung (`5479507`); deren Begründung: der eingehende Weg bedient einen Partner mit fester Satzbreite, die Ergebnisse gehen zeilenweise an alle – „so they cannot share the stream“, daher ein eigener Port. Eine Prüfung, dass die beiden Ports verschieden sind, gab es nie. Der Konstruktor prüft bereits einen negativen `timeoutMillis` vorab (F100), genau aus demselben Grund: was im Reader-Future scheitert, sieht niemand rechtzeitig.
- **Beschreibung:** Ende zu Ende über die CLI (SERVER, `port` = `resultPort` = 5591, `broadcastResults`): der Ergebnis-Server bindet den Port zuerst, der Key-Producer scheitert mit „Connection attempt n failed: Address already in use“ (WARN), dann endet der Producer nach `timeoutMillis` mit „No more keys in secret factory“ und Find mit Exit 0 – ohne einen einzigen Schlüssel.
- **Fix:** Entscheidung des Nutzers. Der Konstruktor weist im Modus SERVER mit `broadcastResults` gleiche Ports ab (`IllegalArgumentException` mit Key-Producer-Id und Port), bevor ein Teil-Objekt existiert; ohne `broadcastResults` wird `resultPort` nicht gebunden und bleibt erlaubt. Im Modus CLIENT ist `port` der des entfernten Partners, also kein Widerspruch. Javadoc und README ergänzt.
- **Reproduktion / Tests:** `KeyProducerJavaSocketTest#constructor_serverPortIsTheResultPort_isRejectedInsteadOfEndingWithoutAKey`, Gegenstück `…WithoutBroadcast_isAccepted`. Ende zu Ende mit Fix: dieselbe CLI-Konfiguration endet nach 1 s mit Exit 1 und „port and resultPort of the socket key producer s must differ in server mode while broadcastResults is set, but both are 5591“.
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected java.lang.IllegalArgumentException to be thrown, but nothing was thrown“), das Gegenstück grün; mit Fix beide grün; Gegenprobe per `git stash` der Produktionsdatei wieder rot, danach grün. Breit: `KeyProducerJavaSocketTest` 22/22, `SocketResultBroadcasterTest` 8/8, `FinderTest` 29/29; `spotless:apply`, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** 6c7aace

<a id="f156"></a>
### F156 – BitcoinAddressFinder: `ProducerReleaser` gibt einen Producer frei, dessen Warten abgelaufen ist und der noch läuft
- **Zeit:** 2026-10-18 22:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerReleaser.java` (`release`)
- **Schwere:** niedrig (nur nach einem Shutdown-Timeout; dann aber Use-after-free von OpenCL-Handles im Treiber)
- **Historie:** `ProducerReleaser` entstand in `5479507` (die Freigabe „genau einmal“ als Objekt), `9c258b5` (F52) ließ die übrigen Producer weiterlaufen, wenn einer scheitert. Die Javadoc legt die Absicht fest: „Each producer is interrupted, awaited and then released, in that order: releasing the resources of a producer that is still running would pull them out from under it.“ `AbstractProducer.waitTillProducerNotRunning` kehrt nach `shutdownTimeoutSeconds` (300 s) aber auch ohne bestätigtes Ende zurück („Continuing shutdown without confirming this producer stopped“) – die Freigabe folgte trotzdem.
- **Beschreibung:** Läuft das Warten ab, ruft `release` `releaseProducer()` dennoch und `ProducerOpenCL` gibt Kernel, Queue, `dstMem` und Kontext frei, während der Producer noch laufen kann; ist er gerade in `OpenClTask.executeKernel`, benutzt er freigegebene Handles (undefiniert im Treiber).
- **Fix:** Entscheidung des Nutzers. Meldet der Producer nach dem Warten noch `RUNNING` (Timeout oder Unterbrechung), wird er nicht freigegeben; ein ERROR nennt ihn („Producer is still running after the wait; its resources are not released“), die übrigen werden wie gewohnt freigegeben. Er bleibt als behandelt markiert, ein zweiter Aufruf (Shutdown-Hook) wartet also nicht noch einmal 300 s. Javadoc ergänzt.
- **Reproduktion / Tests:** `ProducerReleaserTest#release_producerStillRunningAfterTheWait_isNotReleasedAndTheOthersAre` (Testdouble, das nach dem Warten `RUNNING` meldet: keine Freigabe, die beiden anderen je einmal, ERROR-Log). Den Schaden selbst (Freigabe im laufenden Kernel) zeigt kein Mock; der Test belegt die Reihenfolge, die ihn ausschließt.
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <0> but: was <1>“), mit Fix grün; Gegenprobe per `git stash` der Produktionsdatei wieder rot, danach grün. Breit: `ProducerReleaserTest` 5/5, `FinderTest` 29/29, `ProducerOpenCLTest` 11/11, `ProducerJavaTest` 9/9, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply`, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** b80bf76

<a id="f157"></a>
### F157 – BitcoinAddressFinder: Socket-Key-Producer mit belegtem Server-Port endet als regulärer Lauf ohne einen geprüften Schlüssel (Rest nach F155)
- **Zeit:** 2026-10-18 23:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaSocket.java` (`start`, neu `bindServerSocket`), `spotbugs-exclude.xml`, README-Tabelle
- **Schwere:** niedrig (Konfigurations-/Umgebungsfehler; aber still: Exit 0 ohne Arbeit)
- **Historie:** Der Reader bindet den Port seit jeher im Hintergrund und wiederholt jeden `IOException` bis `connectRetryCount`; ein Bind-Fehler wurde damit wie ein ausbleibender Client behandelt. F101 sorgte dafür, dass der endgültige Fehler wenigstens geloggt wird, doch ist `timeoutMillis` kürzer als die Wartezeiten (Standard 3000 < 5 × 1000), endet der Producer vorher mit „No more keys in secret factory“.
- **Beschreibung:** Hält ein fremder Prozess den Port, scheitert jeder Versuch mit „Address already in use“ (WARN), und Find endet nach `timeoutMillis` mit Exit 0, ohne einen Schlüssel geprüft zu haben.
- **Vergleich mit üblicher Server-Software (Entscheidung des Nutzers: Variante (a)):** Ein nicht bindbarer Port ist überall ein Startfehler: Apache httpd beendet sich mit „no listening sockets available, shutting down“, Spring Boot bricht mit `PortInUseException` („Web server failed to start“) ab, nginx versucht es einige Male im Sekundenabstand und beendet sich dann mit „still could not bind()“. Keiner endet regulär. Die ursprünglich beschlossene Regel „`timeoutMillis` < Versuche × Wartezeit abweisen“ ist verworfen: sie träfe die Standardwerte und `examples/config_Find_1OpenCLDevice.json`.
- **Fix:** Im Modus SERVER bindet `start()` den Port auf dem aufrufenden Thread, bevor der Reader startet; scheitert das (belegt, nicht erlaubt), wirft `start()` eine `IllegalStateException` mit Key-Producer-Id, Port und Ursache, und `Finder.startKeyProducer` lässt den Start scheitern. Die Wiederholungen bleiben für das, wofür sie gedacht sind (kein Client innerhalb der Frist, Verbindungsabbrüche). SpotBugs `EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS` begründet unterdrückt (`Startable.start()` deklariert projektweit keine geprüfte Ausnahme). README ergänzt.
- **Reproduktion / Tests:** `KeyProducerJavaSocketTest#start_serverPortHeldByAnotherProcess_failsTheStartInsteadOfEndingWithoutAKey` (ein `ServerSocket` hält den Port). Ende zu Ende über die CLI, Port 5591 von einem Python-Prozess gehalten: mit Fix Exit 1 nach 1 s, „The socket key producer s cannot listen on port 5591: Address already in use“.
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected java.lang.IllegalStateException to be thrown, but nothing was thrown“), mit Fix grün; Gegenprobe per `git stash` der Produktionsdatei wieder rot, danach grün. Breit: `KeyProducerJavaSocketTest` 23/23, `FinderTest` 29/29, `SocketResultBroadcasterTest` 8/8, `BitcoinAddressFinderArchitectureTest`, `ConfigFixturesParseTest` grün; `spotless:apply`, `spotbugs:check` grün.
- **Nachtrag (Branch-Review 2026-10-20):** Das Javadoc von `start()` sagte im Absatz zur Idempotenz weiter, ein zweiter Aufruf reihe einen zweiten Reader hinter dem ersten ein; mit dem Bind auf dem aufrufenden Thread scheitert ein zweiter Aufruf im Modus SERVER aber mit `IllegalStateException`, solange der erste Reader den Port hält, und reicht nichts ein. Der Absatz beschreibt jetzt beide Modi und verspricht kein sicheres Scheitern (nach einem gescheiterten Accept bindet der Reader neu, Port `0` bindet jedes Mal einen neuen ephemeren Port). Reine Doku-Korrektur, kein Test.
- **Status:** gefixt
- **Commit:** 6db153a

<a id="f159"></a>
### F159 – BitcoinAddressFinder: `websocket_scanner.html` rundet „To“ ab und meldet „Range complete.“, obwohl der Rest ungeprüft blieb
- **Zeit:** 2026-10-19 00:25 UTC
- **Datei:** `examples/websocket_scanner.html` (`start()`, neu `alignUp`)
- **Schwere:** niedrig (Beispielseite; aber genau das stille Auslassen, vor dem der Kommentar über `alignDown` warnt)
- **Historie:** Die Seite kam mit der Ergebnis-Übertragung (`5479507`); ihr Kommentar begründet das Ausrichten damit, dass sonst „the rest of the range is never touched — silently“. Für „From“ ist Abrunden richtig, für das (exklusive) Ende nicht.
- **Beschreibung:** „To“ wurde mit `alignDown` abgerundet, die Schleife sendet nur `nextBase < toBase`. Ist „To“ kein Vielfaches von 2^batchSizeInBits, blieben die Schlüssel zwischen dem abgerundeten Wert und „To“ ungeprüft, und „Range complete.“ kam trotzdem.
- **Fix:** Entscheidung des Nutzers. „To“ wird mit dem neuen `alignUp` aufgerundet; für ausgerichtete Werte (auch den Standardwert) ändert sich nichts.
- **Reproduktion / Tests:** Kein Java-Code, kein Testrahmen für die Seite; mit Node nachgerechnet (Raster 16, From 0x10, To 0x35): vorher letzter geprüfter Schlüssel 0x2f (0x30 … 0x34 fehlen), nachher 0x3f; From 0x10, To 0x40: gleiche Blockzahl wie vorher.
- **Status:** gefixt
- **Commit:** 7630dd1

<a id="f160"></a>
### F160 – BitcoinAddressFinder: Inkrementeller Scan mit expliziten Schlüsseln lässt den Rest vor `endPrivateKey` aus
- **Zeit:** 2026-10-19 12:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaIncremental.java` (`createSecrets`), `producer/AbstractProducer.java` (`produceKeys`, Längenprüfung), README „incremental scanning“
- **Schwere:** niedrig bis mittel (still: bis zu `overallWorkSize − 1` Schlüssel eines Bereichs werden nie geprüft)
- **Historie:** Eingeführt in `86db751` (2025-07-19, #27). README und Testmatrix legten es ausdrücklich fest („partial batches are not allowed by default“, „the process stops with an exception“, Rat: `endPrivateKey` passend höher setzen). `endPrivateKey` ist zugleich als *inklusive* Grenze dokumentiert.
- **Beschreibung:** Mit `batchUsePrivateKeyIncrement=false` warf der letzte, nur teilweise vor `endPrivateKey` passende Batch `NoMoreSecretsAvailableException`; die Schlüssel davor wurden nie geprüft (Beispiel: Bereich 2 … 7, Batch 4 → 6 und 7 fehlten).
- **Fix:** Entscheidung des Nutzers (Bug-Hunt-Punkt 2, 2026-10-19). Der letzte Batch ist der kürzere Rest bis einschließlich `endPrivateKey`; erst der Aufruf danach wirft. `AbstractProducer.produceKeys` nimmt dafür im Modus expliziter Schlüssel 1 … `overallWorkSize` Schlüssel an statt genau `overallWorkSize`. Der Grid-Modus ist unverändert (er liefert nur die Startzahl; das letzte Raster ragt über das Ende hinaus und prüft diese Schlüssel mit). README und Testmatrix beschreiben jetzt beide Modi; das oberste Raster vor n lässt die GPU bewusst aus (Entscheidung vom selben Tag, `TODO.md`).
- **Reproduktion / Tests:** `ProducerJavaTest#produceKeys_incrementalRangeEndingInsideABatch_checksEveryKeyUpToTheEnd` (Ende zu Ende über `ProducerJava`, Bereich 2 … 7, Batch 4: der Consumer bekommt 2 … 7); `KeyProducerJavaIncrementalTest#createSecrets_batchExceedsEnd_returnsTheKeysUpToTheEnd` und `#createSecrets_endPrivateKeyInclusive_partialBatch_returnsTheRestThenThrows` (vorher `…_throwsException`, schrieben die alte Regel fest).
- **Rot-Grün-Nachweis:** ohne Fix rot („no item was <6>“, die beiden Key-Producer-Tests mit `NoMoreSecretsAvailableException`), mit Fix grün; Gegenprobe per `git stash` beider Produktionsdateien wieder rot (3 Tests), danach grün. Breit: `KeyProducerJavaIncrementalTest` 10/10, `ProducerJavaTest` 10/10, `AbstractProducerTest` 20/20, `FinderTest` 29/29; `spotless:apply`, `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** 1d9b065

<a id="f161"></a>
### F161 – BitcoinAddressFinder: `WebSocketEndpoint.greetingSupplier` wird nach dem Serverstart ohne Happens-before-Kante geschrieben
- **Zeit:** 2026-10-19 12:10 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/WebSocketEndpoint.java` (Feld `greetingSupplier`)
- **Schwere:** niedrig (nach dem Java-Speichermodell möglich, auf HotSpot/x86 nicht beobachtbar)
- **Beschreibung:** `Finder.startKeyProducer()` startet den Server, `startConsumer()` baut danach den `WebSocketResultBroadcaster`, dessen Konstruktor `setGreetingSupplier` ruft. Gelesen wird das Feld in `onOpen` auf dem Worker-Thread von Java-WebSocket; ohne `volatile` fehlt die Happens-before-Kante, ein Client könnte den Gruß dauerhaft nicht bekommen. `messageHandler` wird dagegen vor `start()` gesetzt (`Thread.start` ordnet).
- **Fix:** Entscheidung des Nutzers: `volatile`, ausdrücklich ohne Rot-Test (eine Sichtbarkeitslücke ist auf HotSpot/x86 deterministisch nicht herstellbar, auch jcstress zeigt sie dort nicht). Begründung als Javadoc am Feld.
- **Tests:** keiner neu; `WebSocketResultBroadcasterTest`, `KeyProducerJavaWebSocketTest`, `FinderTest` grün.
- **Status:** gefixt (ohne Rot-Test, vom Nutzer so entschieden)
- **Commit:** 6f118fb

<a id="f162"></a>
### F162 – BitcoinAddressFinder: Unlesbare oder negative Betragsspalte einer Adressdatei wird still übernommen
- **Zeit:** 2026-10-19 13:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java` (`getCoinIfPossible`), `src/main/java/net/ladenthin/bitcoinaddressfinder/command/AddressFilesToLMDB.java`
- **Schwere:** niedrig (der Betrag ist für die Suche nur Begleitinformation)
- **Historie:** Die Betragsspalte stammt aus dem ursprünglichen Importer; `Long.parseLong` mit stillem Rückfall auf `DEFAULT_COIN`, eine Einheit ist nirgends festgelegt.
- **Beschreibung:** `1.5` (BTC statt Satoshi), `abc`, ein Wert über `Long.MAX_VALUE` und eine leere zweite Spalte wurden ohne Warnung zu 1 Satoshi; ein negativer Betrag (`-5`) wurde übernommen.
- **Fix:** Entscheidung des Nutzers: warnen, negative ablehnen. `AddressTxtLine` bekommt einen Hörer für Zeilen, deren vorhandene Betragsspalte keine nicht-negative ganze Satoshi-Zahl ist; die Adresse bleibt mit `DEFAULT_COIN` erhalten (ein verworfenes Suchziel wäre der größere Schaden). `AddressFilesToLMDB` zählt diese Zeilen und warnt **einmal je Import** mit Anzahl und erster Zeile. Abweichung vom Vorschlag „einmal je Datei“: die Parser des Imports lesen die Zeilen aller Dateien aus einer gemeinsamen Warteschlange, eine Zeile kennt ihre Datei dort nicht mehr.
- **Tests:** `AddressTxtLineTest#fromLine_unusableAmount_takesTheDefaultAndReportsTheLine` (5 Fälle), `#fromLine_validAmountOrNone_isNotReported`, `AddressFilesToLMDBTest#addressFilesToLMDB_unusableAmounts_warnsOnceAndStoresTheDefault`.
- **Rot-Grün-Nachweis:** mit der alten Auswertung rot (`AddressTxtLineTest` 5 von 6, `AddressFilesToLMDBTest` 1 von 1), mit dem Fix grün (304 + 27, `LMDBToAddressFileTest` 16).
- **Status:** gefixt
- **Commit:** 34a8da1

<a id="f163"></a>
### F163 – BitcoinAddressFinder: `ZmqResultBroadcaster` verwirft beim Schließen, was ein zurückliegender Abonnent noch nicht bekommen hat
- **Zeit:** 2026-10-19 13:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/ZmqResultBroadcaster.java` (`LINGER_MILLIS`)
- **Schwere:** niedrig (Zustellung ist best-effort, das Log bleibt maßgeblich)
- **Historie:** Linger 0 seit Einführung des Broadcasters, begründet mit „blocking a shutdown to flush them would be the wrong trade“; der Socket-Broadcaster liefert seit F130 bis zu 2 s nach.
- **Beschreibung:** Die letzten Batch-Meldungen sind das Abschlusssignal eines Laufs. Liegt ein Abonnent beim Herunterfahren zurück, verwarf `close()` mit Linger 0 alles, was der `PUB`-Socket noch nicht geschrieben hatte – genau das Ende des Laufs.
- **Fix:** Entscheidung des Nutzers: Linger 2000 ms, wie der Nachlauf des Socket-Broadcasters. Ohne verbundenen Abonnenten wartet `close()` nicht.
- **Tests:** `ZmqResultBroadcasterTest#close_whileTheSubscriberIsBehind_subscriberStillReceivesTheLastBatch`: der Abonnent liest während eines Bursts von 800 Meldungen (~20 MB) nicht (Empfangs-HWM 1, kleiner Kernel-Puffer), TCP staut zurück, `close()` läuft auf einem anderen Thread, während der Test leert.
- **Rot-Grün-Nachweis:** mit Linger 0 rot (5 von 5 Läufen, die letzte Meldung kam nie an), mit 2000 ms grün (5 von 5, ganze Klasse ~1,7 s). Eine erste Fassung ohne Rückstau war nur in 1 von 3 bzw. 1 von 6 Läufen rot und wurde verworfen.
- **Status:** gefixt
- **Commit:** ff215e5
- **Nachtrag (Branch-Review 2026-10-20):** Der Test hing an einer Zeitreserve: grün hieß, der Test-Thread holt die ~20 MB über Loopback und parst 800 Meldungen innerhalb der ausgelieferten 2 s ab `close()`; auf einem ausgelasteten Runner konnte er ohne Defekt rot werden (beobachtet ist das nicht). Neu ist ein package-privater `@VisibleForTesting`-Konstruktor `ZmqResultBroadcaster(String, BatchResultJsonFormatter, int lingerMillis)`, an den der öffentliche mit `LINGER_MILLIS` delegiert (die Konstante bleibt privat; reine Test-Naht ohne Verhaltensänderung, daher keine eigene Fund-Id). `#close_whileTheSubscriberIsBehind_subscriberStillReceivesTheLastBatch` baut den Broadcaster mit 15 s Linger und pinnt nur noch „close() verwirft nichts“ (über die Naht mit 0 rot: „Expected: is <true> but: was <false>“). Den ausgelieferten Wert pinnt `#close_subscriberStoppedReading_waitsForTheShippedLingerAndThenReturns` über seine Schranke statt über die Konstante: öffentlicher Konstruktor, ein Abonnent, der nicht mehr liest, `close()` muss nach mindestens 1 s und innerhalb von 15 s zurückkehren – Linger 0 im öffentlichen Konstruktor rot („Expected: is a value equal to or greater than <1000L> but: <6L> was less than <1000L>“), −1 rot (`TimeoutException`), unverändert grün (~2,3 s). Der Burst-Aufbau beider Tests liegt in `connectSubscriberAndFallBehind`. `ZmqResultBroadcasterTest` 7/7 in drei Läufen, `spotbugs:check` und `BitcoinAddressFinderArchitectureTest` grün.

<a id="f164"></a>
### F164 – BitcoinAddressFinder: Tuner-Erweiterung lässt die zweite Achse aus, wenn der erste Zusatz-Arm verliert
- **Zeit:** 2026-10-19 14:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java` (`extendSweepBeyondTheCandidates`, jetzt `extendSweep`)
- **Schwere:** niedrig (der gemeldete Gewinner kann eine Untergrenze sein, die Meldung sagt das Gegenteil)
- **Historie:** Die Erweiterung (nach den ersten Läufen eines Mitwirkenden, die am Listenende von `keysPerWorkItem` gewannen) beendete sich beim ersten Arm, der den Gewinner nicht schlug; `nextExtensionArm` folgt bewusst nur der Zeile des Gewinners.
- **Beschreibung:** Liegt der Gewinner auf beiden Rändern (Listen [8, 10] × [1, 4], Gewinner (10, 4)), wurde nur `keysPerWorkItem`=8 gemessen; verlor der, blieb `batchSizeInBits` unerweitert, und das Log meldete „The optimum was inside range.“ auch für diese Achse.
- **Fix:** Entscheidung des Nutzers: nach einem gescheiterten Arm den anderen Rand probieren. Ein verlierender Arm beendet nur seine Achse – sie ist dann über den Gewinner hinaus gemessen, also liefert `nextExtensionArm` den anderen Rand oder nichts. Die Meldung „inside range“ kommt erst, wenn kein Rand mehr übrig ist. Die Schleife ist dafür als `static extendSweep(…, Function<SweepExtension, ArmResult> measure)` herausgelöst (ohne Gerät testbar); `runArm` selbst bleibt ohne Seam (Abschnitt 3). `docs/tuning-your-gpu.md` angepasst.
- **Tests:** `TuneConfigurationTest#extendSweep_firstAxisArmLoses_stillTriesTheOtherEdge`, `#extendSweep_extraArmWins_continuesFromTheNewWinner`, `#extendSweep_maxExtensionArmsReached_stops`.
- **Rot-Grün-Nachweis:** mit der herausgelösten alten Schleife rot (2 von 3; der `maxExtensionArms`-Test ist beidseitig grün), mit dem Fix grün; ganze Klasse 57 Tests grün (1 OpenCL-Test übersprungen, kein Gerät).
- **Status:** gefixt
- **Commit:** c0af424

<a id="f169"></a>
### F169 – BitcoinAddressFinder: Die Vorgabe für `endPrivateKey` ist die Gruppenordnung n, die kein Schlüssel ist
- **Zeit:** 2026-10-19 18:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/configuration/CKeyProducerJavaIncremental.java` (`endPrivateKey`), `constants/Secp256k1Constants.java`
- **Schwere:** niedrig (Doku und eine doppelt geprüfte Ersatz-Taste, kein Schlüssel geht verloren)
- **Historie:** Die Vorgabe `MAX_PRIVATE_KEY_HEX` stammt aus `86db751` (2025-07-19, „Implement keyProducerJavaIncremental, see #27“). Damals nannte die Javadoc der Konstante n den „maximum valid private key“, die Vorgabe war also folgerichtig. Erst F45 (2026-10-17) legte n als exklusive Grenze fest (n·G ist der Punkt im Unendlichen) – seitdem passte die Vorgabe nicht mehr zur dokumentierten inklusiven Obergrenze.
- **Beschreibung:** Im Modus mit expliziten Schlüsseln erreichte ein Lauf bis zur Vorgabe n; `replaceInvalidPrivateKeys` ersetzte ihn durch `INVALID_PRIVATE_KEY_REPLACEMENT` (2), geprüft wurde also 2 noch einmal statt eines Schlüssels. Im Rastermodus ohne Wirkung: das letzte Raster enthält n so oder so und wird auf der GPU übersprungen. README-Tabelle und Beispiele nannten n als Vorgabe.
- **Fix:** Neue Konstante `Secp256k1Constants.MAX_VALID_PRIVATE_KEY` / `_HEX` = n − 1 (Gegenstück zu `MIN_VALID_PRIVATE_KEY`), Vorgabe darauf; README-Tabelle, README-Beispiel, `examples/config_Find_1OpenCLDevice.json` und CHANGELOG angepasst.
- **Tests:** `CKeyProducerJavaIncrementalTest#getEndPrivateKey_defaultEndAddress_returnsTheLastValidPrivateKey` (ersetzt `…_returnsMaxPrivateKey`, das die alte Vorgabe festschrieb): Vorgabe = n − 1, gültig, und n − 1 + 1 ungültig.
- **Rot-Grün-Nachweis:** mit der alten Vorgabe rot (1 von 9), mit der neuen grün; dazu `KeyProducerJavaIncrementalTest`, `ConfigFixturesParseTest`, `BitcoinAddressFinderArchitectureTest` grün (40 Tests).
- **Status:** gefixt
- **Commit:** 46d2515

<a id="f170"></a>
### F170 – BitcoinAddressFinder: `TuneConfiguration.runArm` gibt den Producer eines Arms frei, auch wenn er noch läuft
- **Zeit:** 2026-10-19 19:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/TuneConfiguration.java` (`runArm`, `finally`)
- **Schwere:** niedrig (nur wenn das Warten eines Arms abläuft, `shutdownTimeoutSeconds`, Vorgabe 300 s; dann aber Use-after-free von OpenCL-Handles im Treiber)
- **Historie:** `runArm` stammt aus `aabe5a7` (2026-07-19, „TuneConfiguration run mode“) mit `interrupt()`, `waitTillProducerNotRunning()`, `releaseProducer()` ohne Blick auf den Zustand – dieselbe Form wie `ProducerReleaser` vor F156. Gefunden in der Musterdurchsicht nach F156 (2026-10-18).
- **Beschreibung:** Läuft das Warten ab, gab `runArm` Kernel, Queue und Puffer eines Producers frei, der sie noch benutzen kann. Schlimmer als beim Herunterfahren: danach startet der nächste Arm auf demselben Gerät, während der alte Producer womöglich weiterläuft – seine Messung wäre verfälscht.
- **Fix:** Entscheidung des Nutzers: der Arm-Producer geht über einen `ProducerReleaser` (eine Stelle für die Regel aus F156: ein noch laufender Producer wird nicht freigegeben, ERROR im Log). Der neue Helfer `releaseArmProducer` meldet, ob freigegeben wurde; wenn nicht, beendet `runArm` den Sweep (`shouldRun`) mit einem ERROR, der den Arm nennt, statt den nächsten Arm neben dem alten zu starten. Die bis dahin gemessenen Arme bleiben im Bericht.
- **Tests:** `TuneConfigurationTest#releaseArmProducer_stillRunningAfterTheWait_isNotReleasedAndReportsIt` (Mock mit `RUNNING` nach dem Warten: unterbrochen, gewartet, nicht freigegeben, `false`), `#releaseArmProducer_stoppedProducer_isReleased`.
- **Rot-Grün-Nachweis:** mit dem bisherigen Ablauf im Helfer (immer freigeben) rot (1 von 2), mit dem Fix grün; `TuneConfigurationTest` 59 (1 übersprungen, braucht ein GPU-Gerät), auch auf pocl mit echten OpenCL-Armen grün; `ProducerReleaserTest` 5, `BitcoinAddressFinderArchitectureTest` 17. Die Verdrahtung in `runArm` (Abbruch des Sweeps) braucht einen hängenden Producer im Arm und ist nur übersetzt, nicht ausgelöst.
- **Status:** gefixt
- **Commit:** 03f195a

<a id="f171"></a>
### F171 – BitcoinAddressFinder: Der OpenCL-Kernel rechnet mit einer nicht ausgerichteten Basis still andere Schlüssel, und das oberste Raster ist ein ERROR
- **Zeit:** 2026-10-19 20:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/OpenCLContext.java` (`createKeys`), `util/PrivateKeyValidator.java` (`isAlignedToBatchSize`), `producer/ProducerOpenCL.java` (`processSecretBase`)
- **Schwere:** niedrig (Härtung; die Producer richten jede Basis aus, `AbstractProducer.createSecretBase`)
- **Historie:** Der Kernel leitet den Schlüssel eines Work-Items als `k[0] = baseK0 | base_offset` ab (ODER ins niedrigste Wort), der Host meldet einen Treffer als Basis + Offset (`KeyUtility.CALCULATE_SECRET_KEY_USE_OR = false`). `setSrcPrivateKeyChunk` prüfte nur die Obergrenze. Bei der Prüfung des Vorschlags „Raster vorher einmal kappen“ (2026-10-19) gefunden; das Kappen selbst ist mit dem ODER unmöglich (TODO.md).
- **Beschreibung:** Eine nicht ausgerichtete Basis lief durch und der Kernel prüfte andere Schlüssel als die gemeldeten – still. Fünf bestehende Tests taten genau das (`OpenCLContextGpuFilterWidthTest` mit Basis 1, `OpenCLContextTest` mit `0x12345678`, `ProbeAddressesOpenCLTest` mit `n − 2^8` als „letzter erlaubter Basis“), ebenso die beiden JMH-Benchmarks (zufällige 256 Bit). Das oberste Raster `alignDown(n−1)`, das n enthält und erwartet übersprungen wird, kam als ERROR mit Stacktrace ins Log.
- **Fix:** Entscheidung des Nutzers: eine Prüfung je Raster vor dem Dispatch, nichts je Work-Item. `PrivateKeyValidator.isAlignedToBatchSize`, aufgerufen in `createKeys`; eine nicht ausgerichtete Basis wirft `IllegalArgumentException` („not aligned … Refusing to dispatch“). `processSecretBase` fängt `PrivateKeyTooLargeException` eigens: ein WARN mit der Basis, weiter als letzter Fehler des Producers festgehalten, nichts an den Consumer. Bestehende Tests und Benchmarks auf ausgerichtete Basen umgestellt; README („logged warning“), TODO, CHANGELOG.
- **Tests:** `PrivateKeyValidatorTest#isAlignedToBatchSize_*` (4), `ProbeAddressesOpenCLTest#createKeys_lastAlignedGridBelowTheGroupOrder_computesEveryKeyCorrectly` (Basis n − 0x141, alle 256 Schlüssel gegen die CPU), `#createKeys_gridContainingTheGroupOrder_isRefused`, `#createKeys_unalignedBase_isRefusedBeforeTheKernelRuns`, `ProducerOpenCLTest#processSecretBase_gridContainingTheGroupOrder_isSkippedWithAWarningAndRecorded`, `#processSecretBase_unalignedBase_isAnErrorAndNotComputed`.
- **Rot-Grün-Nachweis:** ohne den Fix (Validator-Stub, keine Prüfung in `createKeys`, kein eigener Catch) rot (6 Fehlschläge), mit dem Fix grün auf pocl; die fünf bestehenden Tests mit nicht ausgerichteten Basen wurden durch die Prüfung rot und laufen ausgerichtet wieder grün; voller `mvn test` auf pocl grün.
- **Status:** gefixt
- **Commit:** 345c49a
- **Nachtrag (Branch-Review 2026-10-20):** `#createKeys_unalignedBase_isRefusedBeforeTheKernelRuns` pinnte die Hälfte „vor dem Dispatch“ nicht: die Basis `0x10001` liegt weit unter der Obergrenze, Typ und Meldung blieben gleich, wenn die Prüfung hinter `setSrcPrivateKeyChunk` oder hinter `executeKernel` wanderte. Der Test liest jetzt das Quellargument des Kernels (`getOpenClTask().orElseThrow().getPrivateKeySourceArgument().getByteBuffer()`, absolut über die ganze Kapazität): nach `init()` ganz null, nach der Ablehnung weiter ganz null – `executeKernel` lädt es erst hoch, nachdem `setSrcPrivateKeyChunk` es geschrieben hat. Neu `#createKeys_unalignedBaseAboveTheMaximum_isRefusedAsUnaligned` mit der Basis n (`MAX_PRIVATE_KEY + 1`, ungerade, also bei 8 Bit nicht ausgerichtet und über der Obergrenze): die Ablehnung muss „not aligned“ sein und keine `PrivateKeyTooLargeException`, die `processSecretBase` als erwartetes Überspringen nur als WARN meldet. Gegenprobe: Prüfung hinter `setSrcPrivateKeyChunk` verschoben macht beide rot (der bestehende „Expected: is [<0b>, <0b>, …] but: was [<1b>, <0b>, <1b>, <0b>, …]“, der neue „Expected: is not an instance of …PrivateKeyTooLargeException but: was <…PrivateKeyTooLargeException: Private key exceeds maximum allowed range for chunked grid mode: …>“); unverändert `ProbeAddressesOpenCLTest` 50/50 grün auf pocl (22 übersprungen wie zuvor).

<a id="f191"></a>
### F191 – BitcoinAddressFinder: `SocketResultBroadcasterTest` zählt seine eigene Port-Probe als Client (flaky, rund 1 von 20 Forks)
- **Zeit:** 2026-10-20 00:10 UTC
- **Datei:** `src/test/java/net/ladenthin/bitcoinaddressfinder/keyproducer/SocketResultBroadcasterTest.java`
- **Schwere:** niedrig (reiner Testcode; CI-Job `Test OpenCL (pocl CPU)` auf PR #387, #388, #389 rot, ohne Bezug zur Änderung)
- **Historie:** Im TODO als „Flaky CI tests seen on PR #389“ notiert (`SocketTimeoutException: Read timed out` in `onBatchChecked_twoConnectedClients_bothReceiveTheSameLine`, Lauf 38698599185). Auf HEAD in frischen JVMs nachgestellt (je Lauf ein `java`-Prozess, die Klasse über den JUnit-Launcher, `argLine` wie in `pom.xml`): **2 von 40 Läufen rot**, einmal in `onBatchChecked_batchWithHit_clientReceivesTheKeyMaterial`, einmal in einem anderen Test derselben Klasse — der Fehler hängt also nicht an einem Test, sondern am gemeinsamen Muster.
- **Beschreibung:** Jeder Test startete mit `broadcaster.start()` und danach `ConnectionUtils.waitUntilTcpPortOpen(…)`. Diese Probe öffnet eine echte TCP-Verbindung, und für den reinen TCP-Broadcaster ist sie ein vollwertiger Client: angenommen, registriert, bis zum ersten fehlgeschlagenen Schreiben in `clients`. `awaitClientCount(n)` zählte sie mit und kehrte zurück, sobald Probe plus n−1 echte Clients registriert waren; der Batch, den der Test danach schickte, ging am letzten echten Client vorbei, und dessen `readLine` lief nach 15 s ab. Das Log des roten Laufs zeigt es direkt: zwei `Result listener connected` in einem Test, der einen Client verbindet. Beim WebSocket-Broadcaster ist die Probe harmlos (sie schließt keinen Handshake ab); dort ist nichts zu ändern. Produktionscode ist nicht betroffen — `start()` bindet den `ServerSocket` synchron, und dass ein Client, der nach einem Batch registriert wird, ihn nicht bekommt, ist das dokumentierte Best-Effort-Verhalten.
- **Fix:** Die Probe entfällt; alle Tests starten über einen Helfer `start(broadcaster)`, dessen Javadoc sagt, warum keine Probe nötig (der Port ist nach `start()` offen) und warum sie falsch ist.
- **Tests:** neu `onBatchChecked_registrationIsSlow_theOnlyClientStillReceivesTheBatch`: verzögert über den bestehenden Test-Hook `setBeforeClientRegistered` jede Registrierung um 300 ms und macht das Rennen so zur Gewissheit.
- **Rot-Grün-Nachweis:** Mit dem Helfer noch mit Probe ist der neue Test deterministisch rot (`but: was null`, der Batch ging an die Probe), ohne Probe grün (9/9). Schleife über frische JVMs: vorher 2/40 rot, nachher 0 von 150.
- **Status:** gefixt
- **Commit:** 2dcbfc0

<a id="f192"></a>
### F192 – BitcoinAddressFinder: F131 lässt einen endlichen GPU-Lauf seine letzten Batches verlieren (Regression des Branches)
- **Zeit:** 2026-10-20 06:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/AbstractProducer.java` (`run`, neu `awaitPendingBatches`), `producer/ProducerOpenCL.java` (`awaitPendingBatches`)
- **Schwere:** hoch (stiller Datenverlust: der letzte Batch eines endlichen OpenCL-Laufs wird nicht geprüft, ein Treffer darin geht ohne Meldung verloren)
- **Historie:** Gefunden beim Review des ganzen Branches am 2026-10-20. F131 (`Finder.shutdownAndAwaitTermination`: erst den Consumer freigeben, wenn alle Producer beendet sind) nahm an, dass ein beendeter Producer alle seine Batches schon in die Consumer-Queue gelegt hat. Für `ProducerJava` und den Secrets-File-Producer stimmt das, für `ProducerOpenCL` nicht.
- **Beschreibung:** `ProducerOpenCL.processSecretBase` gibt jedes Raster an einen Result-Reader-Thread (bis zu `maxResultReaderThreads`, Vorgabe 4) und kehrt zurück; `run()` endet, während die Reader noch zurücklesen. Der Finder gibt daraufhin den Consumer frei, der Worker leert seine Queue einmal und endet, und der Reader legt seinen Batch danach in eine Queue, die niemand mehr liest. `releaseProducer()` schloss außerdem den OpenCL-Kontext, während ein Reader noch lesen konnte. Gemessen auf pocl über den echten Finder (`runOnce`, Raster 2^18, `transferAll`): 0 gemeldete Batches in 4 von 5 Läufen, mit der alten Reihenfolge 5 von 5 korrekt.
- **Fix:** Ein Producer gilt erst als beendet, wenn alle seine Batches beim Consumer sind. `AbstractProducer.run()` ruft vor `signalNotRunning()` den neuen Hook `awaitPendingBatches()` auf (Vorgabe: nichts zu tun), auch nach einer Exception. `ProducerOpenCL` überschreibt ihn: Er holt alle `submitSlot`-Permits (ein Reader hält einen, bis er fertig ist) und gibt sie wieder frei, begrenzt durch `shutdownTimeoutSeconds`; ein danach noch beschäftigter Reader wird als ERROR gemeldet statt ewig abgewartet.
- **Tests:** `ProducerOpenCLTest#run_readerStillBusyAfterTheLastGrid_runReturnsOnlyAfterTheBatchReachedTheConsumer` (ein langsamer Consumer hält den Reader 1 s auf, das Rennen wird zur Gewissheit), `#run_readerBusyLongerThanTheShutdownTimeout_runEndsAndLogsTheBusyReader` (Timeout-Zweig), `AbstractProducerTest#run_loopEnds_pendingBatchesAwaitedOnceWhileStillRunning`, `#run_produceKeysThrows_pendingBatchesStillAwaited`, `FinderTest#shutdownAndAwaitTermination_openClProducerFinished_everyBatchIsReported` (Ende zu Ende über den Finder).
- **Rot-Grün-Nachweis:** Der erste ProducerOpenCL-Test ist ohne Fix deterministisch rot (`Expected: is <1> but: was <0>`), mit Fix grün; der Timeout-Test ohne Fix rot (keine Meldung). Der Finder-Test mit abgeschaltetem Warten 2 von 3 Läufen rot (`but: was <0>`), mit Fix 3 von 3 grün. Producer-Testklassen 60/60 grün auf pocl.
- **Status:** gefixt
- **Commit:** ef96fad

<a id="f193"></a>
### F193 – BitcoinAddressFinder: Die Betragswarnung des Imports (F162) zählt abgelehnte Zeilen und nennt den falschen Betrag
- **Zeit:** 2026-10-20 06:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java` (`fromLine`, neu `fromAddress`, `getCoinIfPossible`), `command/AddressFilesToLMDB.java` (`logSummary`)
- **Schwere:** mittel (jeder Import einer Blockchair-TSV mit der Vorgabekonfiguration loggt eine falsche Warnung mit falschem Text)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20; die Warnung kam mit F162 auf diesen Branch.
- **Beschreibung:** `getCoinIfPossible` meldete die Zeile an den Listener, bevor die Adresse geprüft war. Die Kopfzeile `address<TAB>balance` einer Blockchair-Datei und jede nicht unterstützte Adresse (P2TR, P2MS) mit Text in der zweiten Spalte zählten so als „Zeile mit unbrauchbarem Betrag“, obwohl sie abgelehnt und nicht gespeichert werden. Der Text „stored with the default amount of 1 satoshi“ war außerdem bei `useStaticAmount = true` falsch, der Vorgabe: dann wird die Betragsspalte gar nicht verwendet und `staticAmount` (0) gespeichert. Gemessen vom Reviewer: Kopfzeile, `42` und `-5` mit der Vorgabe ergaben „2 line(s) … 1 satoshi. First: 'address balance'“, gespeichert wurde 0.
- **Fix:** `getCoinIfPossible` meldet nichts mehr und liefert `null` für einen unbrauchbaren Betrag; der Adressteil von `fromLine` ist in `fromAddress` ausgelagert, und `fromLine` meldet die Zeile erst, wenn die Adresse angenommen wurde. `logSummary` warnt nur noch ohne `useStaticAmount`, dort stimmt der Text.
- **Tests:** `AddressTxtLineTest#fromLine_rejectedLineWithTextInTheAmountColumn_isNotReported` (Kopfzeile, P2MS-Zeile), `AddressFilesToLMDBTest#addressFilesToLMDB_blockchairHeaderLine_noAmountWarning`, `#addressFilesToLMDB_unusableAmountWithStaticAmount_noWarningAndTheStaticAmountIsStored`; die bestehenden F162-Tests (`fromLine_unusableAmount_*`, `addressFilesToLMDB_unusableAmounts_warnsOnceAndStoresTheDefault`) bleiben grün. Nebenbei das Javadoc von `fromLine_hexHash160_isImportedAsThatHash160` an seinen Test zurückgesetzt (es stand über einem anderen).
- **Rot-Grün-Nachweis:** die drei neuen Tests (vier Fälle) gegen den alten Code rot (die Kopfzeile bzw. die `-5`-Zeile in der Warnung, `[address balance]` beim Listener), mit dem Fix grün; `AddressTxtLineTest` + `AddressFilesToLMDBTest` 335/335.
- **Status:** gefixt
- **Commit:** 2094bb1

<a id="f194"></a>
### F194 – BitcoinAddressFinder: Secrets-File ohne Increment: Schlüssel außerhalb des gültigen Bereichs werfen oder werden still als k−n gerechnet
- **Zeit:** 2026-10-20 07:00 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerJava.java` (`processSecrets`)
- **Schwere:** niedrig (keine Regression: vor F08 prüfte dieser Modus gar nichts; aber Schlüssel 1 wurde nicht geprüft, und ein Wert über n wurde unter falschem Schlüssel gemeldet)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20. F08 machte den Secrets-File-Modus mit `batchUsePrivateKeyIncrement = false` funktionsfähig; dieser Weg übergibt `SecretsFile`-Zeilen direkt an `consumeSecrets` und überspringt `replaceInvalidPrivateKeys`, das `produceKeys` aufruft.
- **Beschreibung:** `processSecrets` rief `PublicKeyBytes.fromPrivate` mit dem Rohwert auf. Gemessen vom Reviewer: 0, 1 und n+1 werfen `IllegalArgumentException`, n eine `ArrayIndexOutOfBoundsException` (je ein ERROR mit Stacktrace und `recordFailure`), ein Wert in (n+1, 2^256) ergibt still den öffentlichen Schlüssel von k−n, während k gemeldet wird. Im Raster (`createGrid`) steht für einen Schlüssel außerhalb des Bereichs `PublicKeyBytes.INVALID_KEY_ONE`, also wird Schlüssel 1 dort geprüft.
- **Fix:** `processSecrets` wendet dieselbe Regel an wie das Raster: ein Schlüssel außerhalb des gültigen Bereichs wird durch `INVALID_KEY_ONE` ersetzt, ohne Fehler. Die anderen Wege sind unverändert (sie ersetzen vorher schon durch 2).
- **Tests:** `ProducerJavaTest#processSecrets_keyOutsideTheValidRange_isReplacedByKeyOneAsInAGrid` (0, 1, n, n+1, n+5, 2^256−1), `#processSecrets_validKeys_areComputedUnchanged` (2 und n−1), `ProducerJavaSecretsFilesTest#produceKeys_withoutPrivateKeyIncrementKeysZeroAndOne_checkedAsInAGridWithoutAnError` (Ende zu Ende über eine Datei mit `0` und `1`).
- **Rot-Grün-Nachweis:** gegen den alten Code 7 Fehlschläge (ERROR-Logs bei 0, 1, n, n+1; falsche Instanz bei n+5 und 2^256−1; zwei ERRORs im Datei-Test), mit dem Fix grün; Producer- und Consumer-Testklassen 105/105.
- **Status:** gefixt
- **Commit:** 45a33dc
- **Nachtrag (Branch-Review 2026-10-20):** Die beiden `processSecrets`-Tests und ihre Quelle `keysOutsideTheValidRange()` standen zwischen `@ToStringTest` und dem toString-Test, den der Marker kennzeichnet; der Marker lag damit auf dem Bereichstest (vor dessen Javadoc, die so nicht mehr als Doc-Kommentar galt), und `toString_whenCalled_containsClassNameAndConfig` trug keinen mehr. Die drei Mitglieder stehen jetzt in einem eigenen `processSecrets`-Abschnitt vor dem toString-Abschnitt, der toString-Test trägt wieder `@ToStringTest` direkt über `@Test`. Nur verschoben, Namen und Verhalten unverändert (`ProducerJavaTest` grün).

<a id="f195"></a>
### F195 – BitcoinAddressFinder: Schlägt der Abbruch eines fehlgeschlagenen Starts fehl, verschwindet der Startfehler und der Consumer läuft weiter
- **Zeit:** 2026-10-20 07:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/cli/Main.java` (neu `startFinder`), `engine/Finder.java` (`abortStart`, neu `isConsumerRunning`)
- **Schwere:** niedrig (nur wenn der Start fehlschlägt und danach auch das Aufräumen; dann aber ein falscher Fehler und eine JVM, die nicht endet)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20; der Catch-Block kam mit F139 auf diesen Branch.
- **Beschreibung:** Bei einem Startfehler rief `Main` `finder.abortStart()` und warf dann den Fehler weiter. Warf `abortStart()` selbst — `interrupt()` lässt `ProducerReleaser` werfen, was ein `releaseProducer()` warf —, ging der eigentliche Startfehler verloren, und `abortStart()` übersprang `releaseConsumer()`, sodass die Nicht-Daemon-Threads des Consumers die JVM am Leben hielten, genau was F139 verhindern sollte.
- **Fix:** `Finder.abortStart()` gibt den Consumer in einem `finally` frei, auch wenn `interrupt()` wirft. Die Startsequenz steht in `Main.startFinder(Finder)`; ein Fehler des Abbruchs wird dem Startfehler als suppressed angehängt, der Startfehler bleibt der geworfene.
- **Tests:** `MainStartFinderTest#startFinder_startFails_abortsOnceAndRethrowsTheStartFailure`, `#startFinder_startAndAbortFail_theStartFailureIsThrownWithTheAbortFailureSuppressed`, `FinderTest#abortStart_consumerStarted_consumerIsReleased`, `#abortStart_interruptThrows_consumerIsReleasedAnyway`. Bis dahin gab es für `abortStart` keinen Test.
- **Rot-Grün-Nachweis:** mit den beiden Fixes einzeln zurückgenommen rot (der Teardown-Fehler statt des Startfehlers; der Consumer nicht freigegeben), mit ihnen grün; `MainTest` + `MainStartFinderTest` + `FinderTest` 53/53 (mit `LD_PRELOAD=libjsig`, wie CI: ohne stirbt der `MainTest`-Fork mit seinem OpenCL-Test auf pocl ohne `hs_err`, auch auf dem unveränderten Stand).
- **Nachtrag (Branch-Review 2026-10-20):** Die Datei-Zeile nannte den Testzugriff `getConsumerJava()`, den 2189cc1 einführte; a550fa2 („Clear the SpotBugs findings of F192, F193 and F195“) ersetzte ihn durch `isConsumerRunning()`, weil die Rückgabe des Consumers selbst SpotBugs `EI_EXPOSE_REP` auslöste, und die beiden `FinderTest`-Fälle fragen seitdem `is(true)`/`is(false)`. Die Zeile nennt jetzt die vorhandene Methode; reine Doku-Korrektur.
- **Status:** gefixt
- **Commit:** 2189cc1

<a id="f196"></a>
### F196 – BitcoinAddressFinder: Ein während `close()` angenommener Client des `SocketResultBroadcaster` bleibt verbunden
- **Zeit:** 2026-10-20 07:50 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/SocketResultBroadcaster.java` (Accept-Schleife in `start`)
- **Schwere:** niedrig (ein Socket bleibt offen, eine Exception verschwindet ungesehen; nur bei einem Verbindungsaufbau genau während des Schließens)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20; die Form ist älter, F20/F130 (Writer je Client, Drain beim Schließen) haben das Fenster etwas vergrößert.
- **Beschreibung:** Die Accept-Schleife registriert einen angenommenen Client und startet seinen Writer. Läuft `close()` zwischen `accept()` und der Registrierung, fegt es die Client-Menge, bevor der neue Client darin steht; er wird danach registriert, und niemand schließt seinen Socket. `startWriter` auf dem schon heruntergefahrenen Writer-Executor wirft eine `RejectedExecutionException`, die in der Accept-Aufgabe ungesehen stirbt und die Schleife beendet.
- **Fix:** Nach der Registrierung prüft die Schleife `shouldStop` (das `close()` vor dem Fegen setzt) und wirft einen solchen Client wieder hinaus; eine `RejectedExecutionException` beim Start des Writers (das Fenster zwischen dieser Prüfung und dem Start) wirft ihn ebenfalls hinaus.
- **Tests:** `SocketResultBroadcasterTest#close_whileAnAcceptedClientIsBeingRegistered_theClientIsDisconnected`: über den bestehenden Hook `setBeforeClientRegistered` läuft das ganze `close()`, während der Accept-Thread einen angenommenen, noch nicht registrierten Client hält; der Client muss danach das Ende des Streams lesen. Der `RejectedExecutionException`-Zweig ist nur durch Lesen geprüft (ein deterministischer Test bräuchte einen weiteren Seam).
- **Rot-Grün-Nachweis:** gegen den alten Code rot (der Client liest nach 3 s nichts statt EOF), mit dem Fix grün; die Klasse 10/10 dreimal hintereinander, dazu 60 frische JVMs (siehe Commit-Nachricht).
- **Status:** gefixt
- **Commit:** fb9ea84

<a id="f206"></a>
### F206 – BitcoinAddressFinder: Nach einem Ctrl-C meldet der Consumer die beim Herunterfahren geprüften Batches an schon geschlossene Broadcaster, ohne ein Wort
- **Zeit:** 2026-10-20 20:07 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java` (`interrupt`, `shutdownAndAwaitTermination`, `abortStart`), `keyproducer/KeyProducerJavaWebSocket.java` (`interrupt`), die drei Result-Broadcaster
- **Schwere:** niedrig bis mittel (nur auf dem Abbruchpfad; Treffer stehen weiter im Log, verloren geht das Abschlusssignal der zuletzt geprüften Batches)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20 (Fund B1-1). F131 hatte die Reihenfolge nur auf dem Abschlusspfad korrigiert (Consumer zuerst, dann `interrupt()`); der Shutdown-Hook ruft `interrupt()` aber unabhängig vom Main-Thread.
- **Beschreibung:** Nach einem Ctrl-C ruft der Shutdown-Hook `Finder.interrupt()`, während der Main-Thread noch in `shutdownAndAwaitTermination` steht. `interrupt()` unterbrach die Key-Producer – beim WebSocket-Key-Producer mit `endpoint.stop()` auch den Server, auf dem der WebSocket-Broadcaster sendet – und schloss die ZeroMQ- und Socket-Broadcaster. Erst danach ließ der Main-Thread den Consumer die eingereihten Batches prüfen (`releaseConsumer`), und dessen Meldungen fielen ohne Log-Zeile weg: `WebSocketEndpoint.broadcast` an einen gestoppten Server, `ZmqResultBroadcaster.send` kehrte bei `closed` zurück, `SocketResultBroadcaster.broadcast` lief über die geleerte Client-Liste. Ein Client, der die Bereiche geschickt hatte, erhielt für diese Batches nie das „geprüft“, das die Ergebnis-Übertragung je Batch zusichert.
- **Fix:** Der Abbau ist getrennt in Signal, Drain und Schließen. `interrupt()` weckt und stoppt nur noch die Quellen (Key-Producer, Producer) und lässt die Broadcaster offen; ein neues, unter `interruptLock` laufendes und idempotentes `closeBroadcasters()` schließt WebSocket-, ZeroMQ- und Socket-Broadcaster und leert die Ankündigungsliste. `shutdownAndAwaitTermination` (beide Zweige, im `finally`) und `abortStart` (im `finally` nach `releaseConsumer`) rufen es, nachdem der Consumer freigegeben ist. Der WebSocket-Key-Producer übergibt seinen Endpunkt mit `shareEndpoint()` an den Broadcaster; sein `interrupt()` stoppt den Endpunkt dann nicht mehr, das tut `WebSocketResultBroadcaster.close()`. Eine Meldung, die nach dem Schließen ankommt, schreibt jetzt je Broadcaster einmal eine WARN-Zeile (ZeroMQ „already closed“, Socket „already closed“, WebSocket-Endpunkt „already stopped“), statt still zu verschwinden.
- **Tests:** `FinderTest#interrupt_whileConsumerStillDraining_everyBatchIsReportedBeforeTheBroadcastersClose` (vier Producer mit je einem Batch, ein Consumer-Thread, 200-ms-Listener; ein zweiter Thread ruft nach der ersten Meldung `interrupt()` wie der Hook, der Main-Thread steht in `shutdownAndAwaitTermination`; die Naht `setBeforeResultBroadcastersClosed` sitzt jetzt in `closeBroadcasters()`), `FinderTest#interrupt_whileConsumerStillDraining_webSocketClientReceivesEveryBatch` (dasselbe über den echten WebSocket-Transport: ein Client muss alle vier `batch`-Nachrichten erhalten), `ZmqResultBroadcasterTest#onBatchChecked_afterClose_warnsOnceThatTheResultIsNotPublished`, `SocketResultBroadcasterTest#onBatchChecked_afterClose_warnsOnceThatTheResultIsNotSent`, `WebSocketEndpointTest#broadcast_afterStop_warnsOnceThatTheMessageIsNotSent`. Der F34-Test heißt jetzt `FinderTest#teardown_calledFromTwoThreadsAtOnce_neitherFails` und läuft über `abortStart()`, weil `interrupt()` die Broadcaster-Listen nicht mehr durchläuft.
- **Rot-Grün-Nachweis:** gegen den alten Code rot: Naht-Test „Expected: is <4> but: was <1>“, WebSocket-Test „Expected: is <4> but: was <2>“, die drei Log-Tests je „Expected: a collection with size <1> but: collection size was <0>“; mit dem Fix grün, die beiden Finder-Tests dreimal hintereinander. Breite Liste: `FinderTest` 34/34 (mit `POCL_MAX_WORK_GROUP_SIZE=256`, ohne stirbt die OpenCL-Fork auf pocl wie schon bei F131 notiert), `ZmqResultBroadcasterTest` 6/6, `SocketResultBroadcasterTest` 11/11, `WebSocketEndpointTest` 2/2, `WebSocketResultBroadcasterTest` 4/4, `KeyProducerJavaWebSocketTest` 5/5, `MainTest` 19/19, `MainStartFinderTest` 2/2, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** 92839f5

<a id="f207"></a>
### F207 – BitcoinAddressFinder: Der WebSocket-Key-Producer startet „erfolgreich“, wenn sein Port nicht gebunden werden kann, und wartet dann ewig
- **Zeit:** 2026-10-20 20:16 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/WebSocketEndpoint.java` (`start`, `onError`, `onStart`)
- **Schwere:** niedrig bis mittel (Konfigurations-/Umgebungsfehler wie F157; mit dem empfohlenen `timeoutMillis: -1` aber ein dauerhaftes Hängen)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20 (Fund B2-1). F157 legte die Regel fest „ein nicht bindbarer Port ist überall ein Startfehler“, setzte sie aber nur für den Socket-Key-Producer um.
- **Beschreibung:** Java-WebSocket 1.6.0 bindet den Port auf seinem eigenen Selector-Thread (`doSetupSelectorAndServerThread`) und meldet einen Fehlschlag nur über `handleFatal` → `onError(null, ex)`, das der Endpunkt bloß loggte. `WebSocketEndpoint.start()` kehrte nach `localServer.start()` sofort normal zurück, also auch `KeyProducerJavaWebSocket.start()`; der Finder registrierte den Key-Producer, und `createSecrets` wartete mit `timeoutMillis: -1` für immer in `take()` (mit positivem Timeout endete der Lauf wie ein regulärer, ohne einen Schlüssel geprüft zu haben).
- **Fix:** `start()` legt je Start ein `CompletableFuture` an, das `onStart()` erfüllt und `onError(conn == null, ex)` scheitern lässt (spätere Server-Fehler treffen ein schon erfülltes Future und bleiben wirkungslos, das Log bleibt), und wartet nach `localServer.start()` darauf (höchstens 10 s). Ein Bind-Fehler wird zur `IllegalStateException` „The WebSocket key producer cannot listen on port <port>: <Ursache>“ mit der Ursache; läuft die Wartezeit ab oder wird das Warten unterbrochen (Flag wiederhergestellt), wird der Server gestoppt und der Start ebenfalls mit einer `IllegalStateException` abgebrochen – nie als Erfolg gewertet. README: Zeile `port` des WebSocket-Abschnitts sagt das jetzt. SpotBugs: dieselbe begründete `EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS`-Unterdrückung wie bei F157, für `WebSocketEndpoint.awaitListening`.
- **Tests:** `WebSocketEndpointTest#start_portHeldByAnotherProcess_failsTheStartInsteadOfLoggingAndReturning` und `KeyProducerJavaWebSocketTest#start_portHeldByAnotherProcess_failsTheStartInsteadOfWaitingForever` (ein `ServerSocket` hält den Port; die Meldung muss den Port nennen).
- **Rot-Grün-Nachweis:** gegen den alten Code rot, beide mit „Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.“; mit dem Fix grün. Breite Liste: `WebSocketEndpointTest` 3/3, `KeyProducerJavaWebSocketTest` 6/6, `WebSocketResultBroadcasterTest` 4/4, `FinderWebSocketRoundtripTest` 1/1, `FinderTest` 34/34 (mit `POCL_MAX_WORK_GROUP_SIZE=256`), `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** fd12fa5

<a id="f208"></a>
### F208 – BitcoinAddressFinder: Ein nicht bindbarer `resultPort` des Socket-Key-Producers wird zu „continuing without it“ herabgestuft
- **Zeit:** 2026-10-20 20:26 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java` (`addSocketBroadcasters`, `createResultListeners`, `startConsumer`)
- **Schwere:** niedrig (Konfigurations-/Umgebungsfehler mit stillem Ausgang)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20 (Fund XB-2). Das Verhalten ist älter als der Branch (5479507); der Branch hat mit F157 aber die Regel „ein nicht bindbarer Port ist überall ein Startfehler“ gesetzt und mit F155 den Fall `port == resultPort` abgelehnt.
- **Beschreibung:** `addSocketBroadcasters` fing die `IOException` aus `SocketResultBroadcaster.start()` (`new ServerSocket(resultPort)`), loggte „Could not serve results on port {}; continuing without it.“ und lief weiter. Hielt ein fremder Prozess den `resultPort`, erhielt kein Ergebnis-Client eine einzige Batch-Meldung, und der Lauf endete trotzdem mit Exit 0. Der ZeroMQ-Broadcaster bindet dagegen im Konstruktor und lässt den Start an der `ZMQException` scheitern; der `resultPort` wird in beiden Modi (SERVER und CLIENT) gebunden.
- **Fix:** Die `IOException` wird mit Kontext weitergereicht: „The socket key producer <id> cannot serve results on port <resultPort>: <Ursache>“, die Ursache angehängt. `addSocketBroadcasters` und `createResultListeners` deklarieren `throws IOException`, `startConsumer()` warf ohnehin `Exception` – eine geprüfte Ausnahme, daher keine weitere SpotBugs-Unterdrückung. `Main.startFinder` bricht den Start über `abortStart()` ab, das die schon gestarteten Result-Server schließt; der gescheiterte hält keinen Socket. README: Zeile `resultPort` nennt den Startfehler.
- **Tests:** `FinderTest#startConsumer_resultPortHeldByAnotherProcess_failsTheStart` (ein `ServerSocket` hält den Port, ein Socket-Key-Producer im SERVER-Modus mit freiem `port` und diesem `resultPort`; `startConsumer` muss eine `IOException` werfen, die Key-Producer-Id und Port nennt, und es läuft kein Consumer).
- **Rot-Grün-Nachweis:** gegen den alten Code rot („Expected java.io.IOException to be thrown, but nothing was thrown.“), mit dem Fix grün. Breite Liste: `FinderTest` 35/35 (mit `POCL_MAX_WORK_GROUP_SIZE=256`), `MainStartFinderTest` 2/2, `KeyProducerJavaSocketTest` 23/23, `SocketResultBroadcasterTest` 11/11, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** 4b244a4
- **Nachtrag (Verifikation 2026-10-20):** Die Id-Assertion des Tests war wirkungslos: die Id `socket` steht ohnehin in „The socket key producer“. Der Test nutzt jetzt die eindeutige Id `heldResultPortProducer`, sodass er wirklich prüft, dass die Meldung den Key-Producer nennt. Rot/Grün der drei Fixe F206–F208 unabhängig nachvollzogen (Produktionsdateien auf den Elternstand gesetzt: rot mit den genannten Assertions; zurückgesetzt: grün).

<a id="f209"></a>
### F209 – BitcoinAddressFinder: Die libjsig-Warnung glaubt dem `LD_PRELOAD`-String; ein nicht ladbarer Eintrag lässt sie schweigen
- **Zeit:** 2026-10-20 21:26 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/SignalChainingCheck.java` (`warning`), `README.md` (Abschnitt „OpenCL on Linux: signal chaining (`libjsig`)“)
- **Schwere:** niedrig (die Warnung fehlt genau in der Fehlkonfiguration, vor der sie schützen soll; `ld.so` schreibt immerhin eine eigene ERROR-Zeile auf stderr)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20 (Fund B3-1). Die Prüfung kam mit F148 und entschied rein über `os.name`, `LD_PRELOAD`, `java.home`.
- **Beschreibung:** `SignalChainingCheck.warning` gab leer zurück, sobald der `LD_PRELOAD`-String „libjsig“ enthielt. glibcs `ld.so` überspringt einen Preload-Eintrag, den es nicht öffnen kann („ERROR: ld.so: object '…' from LD_PRELOAD cannot be preloaded …: ignored.“), und startet die JVM ohne Signalverkettung. Genau dahin führte die README-Zeile `LD_PRELOAD="$JAVA_HOME/lib/libjsig.so" java …`: mit nicht gesetztem `JAVA_HOME` (Java nur über `PATH`, der häufige Fall) wird daraus `/lib/libjsig.so`, das es nicht gibt – die JVM lief ungeschützt, und BAFs eigene Warnung vor dem Kernel-Bau blieb aus. Umgekehrt warnte die Prüfung, wenn `libjsig` über `/etc/ld.so.preload` oder einen Launcher geladen war, obwohl die Verkettung aktiv war.
- **Fix:** Die Entscheidung fällt jetzt über das, was tatsächlich gemappt ist: die neue Überladung `warning(osName, ldPreload, javaHome, Supplier<Optional<Boolean>> libjsigMapped)`; die bisherige Dreier-Überladung (Aufruf in `OpenCLContext.init`) liefert den Supplier, der `/proc/self/maps` liest (`libjsigMapped(Path)`: eine Zeile mit „/libjsig.“). Regeln: nicht Linux → leer; gemappt → leer; nicht gemappt und `LD_PRELOAD` nennt libjsig → Warnung „LD_PRELOAD names libjsig (…), but it was not loaded: the dynamic linker printed "cannot be preloaded ... ignored" …“ mit dem Pfad aus `java.home`; nicht gemappt sonst → die bisherige Warnung; Mapping nicht lesbar (Leseausnahme, DEBUG-Log mit Ursache) → die bisherige String-Entscheidung, und die Warnung sagt „/proc/self/maps could not be read, so this was decided from LD_PRELOAD alone“. README: der Aufruf leitet den Pfad wie die `run_*OpenCL*.sh`-Skripte aus dem laufenden `java` ab statt aus `JAVA_HOME`, und ein Absatz erklärt den stillen Fehlschlag von `ld.so` und dass der Finder das Mapping prüft.
- **Tests:** `SignalChainingCheckTest#warning_linuxLibjsigNamedInLdPreloadButNotMapped_warns`, `#warning_linuxLibjsigMappedWithoutLdPreload_isEmpty`, `#warning_linuxMappingUnreadable_fallsBackToLdPreload` (beide `LD_PRELOAD`-Werte), `#libjsigMapped_mapsListLibjsig_isTrue`, `#libjsigMapped_mapsWithoutLibjsig_isFalse`, `#libjsigMapped_unreadableMaps_isUnknown` (Maps-Datei im `@TempDir`). Die fünf bisherigen Fälle laufen über die neue Überladung mit festem Mapping, die Windows-/macOS-Fälle mit einem Supplier, der wirft – sonst hinge ihr Ergebnis vom Prozess ab (der CI-Job `test-opencl` lädt `libjsig` vor). Ende zu Ende mit einer kleinen Probe über die Dreier-Überladung: ohne Preload die alte Warnung, mit `LD_PRELOAD=/lib/libjsig.so` die ld.so-ERROR-Zeile und die neue Warnung, mit dem echten Pfad keine Warnung.
- **Rot-Grün-Nachweis:** gegen den alten Entscheidungscode (neue Überladung als Stub, die den Supplier ignoriert und an die alte Logik delegiert; `libjsigMapped` als Stub „leer“) rot: `warning_linuxLibjsigNamedInLdPreloadButNotMapped_warns` „Expected: is <true> but: was <false>“, `warning_linuxLibjsigMappedWithoutLdPreload_isEmpty` „Expected: is <false> but: was <true>“, `warning_linuxMappingUnreadable_fallsBackToLdPreload` „Expected: a string containing "could not be read"“, die beiden Maps-Tests „Expected: is <Optional[true]>/<Optional[false]> but: was <Optional.empty>“; mit dem Fix grün, `SignalChainingCheckTest` 11/11, `BitcoinAddressFinderArchitectureTest` 17/17; `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** e406105

<a id="f210"></a>
### F210 – BitcoinAddressFinder: Der Tuner wiederholt die libjsig-Warnung je Arm, und sein Startskript lädt libjsig nicht vor
- **Zeit:** 2026-10-20 21:38 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/OpenCLContext.java` (`init`), `opencl/SignalChainingCheck.java`, `examples/run_TuneConfiguration.sh`
- **Schwere:** niedrig (Log-Rauschen im Tuner-Log, das der Nutzer nach dem Gewinner lesen soll; dazu das Absturzrisiko von F148 für den Tuner auf pocl/Intel-CPU)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20 (Fund B3-4). Die Warnung kam mit F148; dessen Ende-zu-Ende-Prüfung lief nur mit einem `Find` mit einem Producer („loggt die Warnung einmal“).
- **Beschreibung:** `OpenCLContext.init()` legte bei jedem Aufruf eine neue `SignalChainingCheck` an und loggte deren Warnung. `TuneConfiguration.runArm` baut je Arm einen neuen Producer, dessen `initProducer` einen neuen `OpenCLContext` initialisiert; ein Standard-Sweep (7 Rastergrößen × 5 `keysPerWorkItem`, dazu Erweiterungs- und Filter-Arme) auf Linux ohne Preload schrieb dieselbe WARN-Zeile 35- bis 45-mal, ein `Find` mit mehreren OpenCL-Producern einmal je Producer. Die Bedingung kann sich im Prozess nicht ändern. Dazu lud `examples/run_TuneConfiguration.sh` – anders als die drei `run_*OpenCL*.sh` – `libjsig` nicht vor, obwohl der Tuner beim Tunen eines OpenCL-Producers je Arm einen Kernel baut.
- **Fix:** `SignalChainingCheck.warning` bleibt eine reine Entscheidung. Neu ist das Tor `warningOnce` (Dreier-Überladung über `/proc/self/maps`, Vierer-Überladung mit Mapping-Supplier, siehe F209): es gibt die Warnung beim ersten Auftreten je Instanz zurück (`AtomicBoolean.compareAndSet`), danach leer; eine leere Antwort verbraucht das Tor nicht. `OpenCLContext` hält eine geteilte Instanz `private static final SignalChainingCheck SIGNAL_CHAINING_CHECK` und ruft `warningOnce` unmittelbar vor `buildProgram`, also einmal je JVM. `run_TuneConfiguration.sh` hat jetzt denselben Linux-Block wie die OpenCL-Skripte (`readlink -f $(command -v java)`, `export LD_PRELOAD` nur, wenn die Datei existiert). SpotBugs: die Klasse hat mit dem Tor einen Zustand und trägt `@ToString`; `IMC_IMMATURE_CLASS_NO_EQUALS` ist begründet unterdrückt (ein Tor hat Identitätssemantik).
- **Tests:** `SignalChainingCheckTest#warningOnce_linuxWithoutPreload_secondCallIsEmpty`, `#warningOnce_libjsigLoaded_doesNotConsumeTheGate`, `#warning_afterWarningOnceOfTheSameInstance_stillAnswers` (frische Instanzen); `OpenCLContextTest#signalChainingCheck_oneInstanceSharedByEveryContext` pinnt die Verdrahtung über die Deklaration (genau ein `static final`-Feld vom Typ `SignalChainingCheck`), weil `init()` ein Gerät braucht. Ende zu Ende auf pocl ohne Preload: zwei `OpenCLContext`-`init()` hintereinander in einer JVM loggen die Warnung einmal (vorher zweimal).
- **Rot-Grün-Nachweis:** gegen den alten Code (`warningOnce` als Stub ohne Tor, `OpenCLContext` ohne geteilte Instanz) rot: `warningOnce_linuxWithoutPreload_secondCallIsEmpty` „Expected: is <false> but: was <true>“, `signalChainingCheck_oneInstanceSharedByEveryContext` „Expected: a collection with size <1> but: collection size was <0>“; mit dem Fix grün. Breite Liste: `SignalChainingCheckTest` 14/14, `OpenCLContextTest` 33/33 (auf pocl), `ExampleRunScriptJarVersionTest` 1/1, `JvmModuleFlagConsistencyTest` 4/4, `BitcoinAddressFinderArchitectureTest` 17/17; `bash -n` des Skripts, `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** 001c669

<a id="f211"></a>
### F211 – BitcoinAddressFinder: Auf pocls CPU-Gerät stirbt die JVM bei Standardeinstellungen; die Work-Group-Grenze stand nur im CI
- **Zeit:** 2026-10-20 21:59 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/OpenClTask.java` (`executeKernel`), `opencl/OpenCLContext.java` (`init`, `logKernelResourceUsage`), `opencl/binding/ClApi.java` / `LwjglClApi.java` (`enqueueNDRangeKernel`), neu `opencl/OpenClLocalWorkSize.java`; `README.md`, die vier OpenCL-Startskripte
- **Schwere:** mittel (nativer JVM-Absturz ohne Java-Meldung auf einer Plattform, die das README als unterstützt und getestet führt; betrifft den CPU-OpenCL-Betrieb über pocl)
- **Historie:** Gefunden beim Review des Branches am 2026-10-20 (Fund XB-1). Beobachtet schon am 2026-10-18 (`BUGHUNT-CHECKED.md`, `OpenCLContext` + Kernel: SIGSEGV in `_pocl_kernel_generateKeysKernel_grid_workgroup`, Guard-Page des 8-MB-Stacks) und als B2 in `BUGHUNT-MEASUREMENTS.md` gemessen (5 von 5 Abstürzen ohne Grenze); damals nur im CI-Job `test-opencl` mit `POCL_MAX_WORK_GROUP_SIZE=256` umgangen (794d2b5) und als „Testumgebung, kein Produktfehler“ abgelegt – beide Notizen verweisen jetzt hierher.
- **Beschreibung:** `LwjglClApi.enqueueNDRangeKernel` übergab nie eine Local-Work-Size; der Treiber wählt. pocls CPU-Gerät wählt sein Maximum (`CL_KERNEL_WORK_GROUP_SIZE` 4096, hier auf 4 Kernen wie zuvor auf 16 gemessen), und die Work-Group-Funktion des Schlüssel-Kernels läuft über den 8-MB-Stack von pocls Worker-Thread: die JVM stirbt mit Exit 139/134. Die drei `run_*OpenCL*.sh` und `run_TuneConfiguration.sh` setzten die Variable nicht, der README-Abschnitt „PoCL support“ erwähnte die Grenze nicht, und die Laufzeit sagte nichts. Ein Java-Prozess kann `POCL_MAX_WORK_GROUP_SIZE` für sich selbst nicht setzen, bevor pocl initialisiert.
- **Fix:** Neue reine Wahlfunktion `OpenClLocalWorkSize.choose(globalWorkSize, kernelWorkGroupSize, poclCpuDevice)`: leer (der Treiber wählt, wie bisher) für jedes Gerät außer pocls CPU-Gerät; dort die größte Zweierpotenz ≤ min(256, `CL_KERNEL_WORK_GROUP_SIZE`, größte Zweierpotenz, die die globale Größe teilt) – teilt die globale Größe also immer (OpenCL 1.x verlangt das; der Kernel wird als CL1.2 gebaut), und ein vom Nutzer gesetztes `POCL_MAX_WORK_GROUP_SIZE` steckt schon im abgefragten Kernel-Limit. Nicht positive Größen werden mit `IllegalArgumentException` abgelehnt. `isPoclCpuDevice(platformName, deviceVersion, deviceType)` erkennt pocl am Plattformnamen „Portable Computing Language“ oder an „PoCL“ in `CL_DEVICE_VERSION` und verlangt den Gerätetyp CPU – nicht am `CL_DEVICE_VENDOR`, der bei pocls CPU-Gerät die CPU nennt (hier „GenuineIntel“); pocls CUDA- und andere Backends bleiben beim Treiber. Neue `ClApi`-Überladung `enqueueNDRangeKernel(queue, kernel, global, local, profiling)` (lehnt eine andere Dimensionszahl vor dem Treiberaufruf ab); `OpenClTask` wählt pro Launch und ruft die alte Überladung, wenn die Wahl leer ist, sodass GPUs unverändert laufen. `OpenCLContext.init` übergibt das abgefragte Kernel-Limit (`logKernelResourceUsage` gibt es jetzt zurück) und die Erkennung, und loggt auf pocls CPU-Gerät eine INFO-Zeile mit der Grenze und dem Treiber-Default. Doku: README „PoCL support“ nennt die Grenze und die Erkennung, der pocl-Absatz im libjsig-Abschnitt beschreibt das neue Verhalten; die vier OpenCL-Startskripte exportieren `POCL_MAX_WORK_GROUP_SIZE=${POCL_MAX_WORK_GROUP_SIZE:-256}` mit Kommentar; der CI-Kommentar in `test-opencl` sagt, wofür die Variable dort noch steht. Nicht abgedeckt: die Benchmark-Haken (`runBenchInvMod`, `runBenchFeMul`, `runBenchFilterProbe`) und die Test-Kernel starten weiter ohne Local-Size; für sie bleibt die Variable (Launcher, CI).
- **Tests:** `OpenClLocalWorkSizeTest` (12: Erkennung über Plattformname, über die Geräteversion bei fremdem Plattformnamen, pocl-GPU-Backend nein, Intel-CPU-Runtime nein, NVIDIA nein; Wahl: kein pocl → leer, 4096 → 256, Kernel-Limit 128 → 128, 200 → 128, globale Größe 64 → 64, 96 → 32, nicht positive Größen abgelehnt), `OpenClTaskLocalWorkSizeTest` (2, Mock-`ClApi`: auf pocls CPU-Gerät die Überladung mit `{256}` bei globaler Größe 4096 und nie die alte, sonst die alte und nie die neue), `LwjglClApiKernelLaunchTest` (3: andere Dimensionszahl abgelehnt, ohne Treiberaufruf; auf dem Gerät: eine nicht teilende Local-Size endet mit `CL_INVALID_WORK_GROUP_SIZE`, eine teilende läuft jedes Work-Item – damit ist belegt, dass die Größe wirklich beim Treiber ankommt). Ende zu Ende auf pocl ohne `POCL_MAX_WORK_GROUP_SIZE` und ohne libjsig: `init()` plus drei Raster 2^14 – vor dem Fix 2 von 2 Läufen SIGSEGV (Exit 139), nach dem Fix 3 von 3 durch, mit der INFO-Zeile „local work size of at most 256 (driver default here: 4096)“.
- **Rot-Grün-Nachweis:** gegen den alten Code (Wahlfunktion als Stub „leer/false“, `OpenClTask` ruft die alte Überladung, `LwjglClApi`-Überladung ignoriert die Local-Size) rot: `choose_poclCpuDeviceDefaultLimit_isCappedAt256` „Expected: is <OptionalLong[256]> but: was <OptionalLong.empty>“ (ebenso die übrigen Wahl- und zwei Erkennungstests, 8 von 12), `executeKernel_poclCpuDevice_launchesWithTheCappedLocalWorkSize` „Argument(s) are different! Wanted: …“, `enqueueNDRangeKernel_localSizeNotDividingTheGlobalSize_isRefusedByTheDriver` „Expected …OpenClCallFailedException to be thrown, but nothing was thrown.“; der Dimensionstest ließ gegen den Stub die Fork-JVM in `clEnqueueNDRangeKernel` mit den Attrappen-Handles abstürzen (die Prüfung fehlte). Mit dem Fix grün: 12/12, 2/2, 3/3. Breite Liste, ohne `POCL_MAX_WORK_GROUP_SIZE` und ohne libjsig auf pocl (vorher brauchten `FinderTest` und `MainTest` die Variable, siehe F206): `MainTest` 19/19, `FinderTest` 35/35, `ProducerOpenCLTest` 15/15, `OpenCLContextTest` 33/33, `OpenCLGridResultTest` 17/17, `OpenCLContextGpuFilterWidthTest` 4/4, `OpenCLContextAllocationFailureTest` 2/2, `OpenCLZeroAnchorTest` 2/2, `OpenCLBuilderTest` 4/4, `OpenCLAddKernelSmokeTest` 1/1, `LwjglClApiBufferTest` 2/2; dazu `BitcoinAddressFinderArchitectureTest` 17/17, `ExampleRunScriptJarVersionTest` 1/1, `JvmModuleFlagConsistencyTest` 4/4, `bash -n` der vier Skripte, `check-run-scripts.py`, YAML-Prüfung, `spotless:apply` und `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** e1025ea

## Offene Verdachte ohne Fund-ID

- *(erledigt 2026-10-19, ohne Fund-ID: Testumgebung, kein Produktfehler)* `ProbeAddressesOpenCLTest#setSrcPrivateKeyChunk_privateKeyTooLarge_throwsException` brach auf Geräten mit 1024 MiB je Allokation mit `CL_INVALID_BUFFER_SIZE` ab, bevor die Prüfung lief (B3: mit `POCL_MEMORY_LIMIT=4` reproduziert, ab 2047 MB grün). Läuft jetzt auf einem 8-Bit-Raster (eigener Provider `privateKeysTooLargeForASmallGrid`), mit und ohne Begrenzung grün. Commit f06dd60. Ebenso B2: `test-opencl` setzt `POCL_MAX_WORK_GROUP_SIZE=256` (794d2b5; beim Branch-Review 2026-10-20 als Produktfehler eingeordnet und im Produkt behoben, [F211](#f211)), B6: `GcTrigger` entfernt (51fcfb3).

