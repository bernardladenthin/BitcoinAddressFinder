
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
- [F35](#f35) — niedrig — *nur beschrieben*
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
- [F129](#f129) — niedrig — *nur beschrieben*
- [F130](#f130) — mittel — *gefixt*
- [F131](#f131) — mittel — *gefixt*
- [F132](#f132) — mittel — *gefixt*
- [F135](#f135) — niedrig — *gefixt*
- [F136](#f136) — mittel — *gefixt*
- [F139](#f139) — mittel — *gefixt*
- [F140](#f140) — mittel — *gefixt*
- [F141](#f141) — niedrig — *gefixt (Doku)*
- [F142](#f142) — niedrig — *nur beschrieben*
- [F144](#f144) — niedrig — *gefixt (Doku)*
- [F145](#f145) — niedrig — *gefixt (Doku)*
- [F146](#f146) — niedrig — *gefixt (Doku)*
- [F147](#f147) — niedrig — *gefixt (Doku)*

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
- **Commit:** BAF `0fa7428`

<a id="f08"></a>
### F08 – BAF: Secrets-Datei mit `batchUsePrivateKeyIncrement=false` prüft kein einziges Secret
- **Zeit:** 2026-10-16 23:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/producer/ProducerJavaSecretsFiles.java` (ehem. Override `processSecrets`, Zeile ~91)
- **Schwere:** hoch (stiller Totalausfall eines konfigurierbaren Modus)
- **Beschreibung:** `ProducerJavaSecretsFiles.processSecrets()` warf `UnsupportedOperationException("Not supported yet.")`. Mit `batchUsePrivateKeyIncrement=false` leitet `AbstractProducer.consumeSecrets` jedes Secret genau dorthin. `AbstractPlaintextFile.readFile()` fängt die Exception pro Zeile ab: Für **jede** Zeile wird „Error in line“ geloggt, die Zeile in `ReadStatistic.errors` gesammelt (wächst unbegrenzt) und **kein Secret geprüft** – der Lauf endet trotzdem „erfolgreich“. Default ist `true` und alle Beispielkonfigurationen setzen `true`, daher bisher unbemerkt. Fix: Override entfernt; die geerbte `ProducerJava.processSecrets()` erzeugt die Public Keys und gibt sie an den Consumer – wie bei den übrigen Java-Producern.
- **Reproduktion:** `ProducerJavaSecretsFilesTest#produceKeys_filesConfiguredWithoutPrivateKeyIncrement_keysCreated` (alle 4 Formate; vorher rot: 0 statt 6 Keys, danach grün). `ProducerJavaSecretsFilesTest` 11/11.
- **Nebenbefund (nicht geändert, niedrig):** `interrupt()` kann zwischen der `shouldRun`-Prüfung und `currentSecretsFile.set(...)` fallen; dann wird die nächste Datei trotzdem komplett gelesen. Außerdem bleibt `currentSecretsFile` bei einer Exception aus `readFile()` gesetzt (kein `finally`).
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `89d0abc`

<a id="f10"></a>
### F10 – BAF: Shutdown hängt, wenn mehrere Producer einen Socket/WebSocket/ZMQ-Key-Producer teilen (`timeoutMillis: -1`)
- **Zeit:** 2026-10-16 23:30 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/AbstractKeyProducerQueueBuffered.java` (`createSecrets`, Sentinel-Zweig; `signalShutdown`)
- **Schwere:** mittel
- **Beschreibung:** `signalShutdown()` legt **ein** `SHUTDOWN_SENTINEL` in die Queue. Mehrere Producer dürfen denselben `keyProducerId` nutzen (`Finder` prüft das nicht). Mit `timeoutMillis: -1` – genau die Einstellung, die CLAUDE.md für interaktive Feeds empfiehlt – blockiert jeder in `take()`. Nur der Aufrufer, der das Sentinel bekommt, wird geweckt; die anderen bleiben für immer hängen, der Shutdown kommt nicht zu Ende. Fix: Wer das Sentinel entnimmt, legt es vor dem Werfen wieder zurück.
- **Reproduktion:** `AbstractKeyProducerQueueBufferedTest#interrupt_unblocksEveryBlockedConsumer_negativeTimeout` (vorher rot: `TimeoutException` beim zweiten Aufrufer, danach grün). Zusätzlich grün: `AbstractKeyProducerQueueBufferedTest` 11/11, `KeyProducerJavaWebSocketTest` 5/5, `KeyProducerJavaSocketTest` 17/17, `KeyProducerJavaZmqTest` 7/7. PIT nicht gelaufen (der neue `offer`-false-Zweig ist nur Trace-Logging, wie der bestehende in `signalShutdown`).
- **Nebenbefund (nicht geändert):** Bricht `createSecrets` mitten in einem Batch ab (Timeout bei `i > 0`, nur ohne `batchUsePrivateKeyIncrement`), sind die bereits entnommenen Secrets verloren und werden nie geprüft. `addSecret` loggt bei voller Queue `{}` mit einem `byte[]` – Ausgabe ist `[B@…`, aber die Standard-Queue ist unbegrenzt.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `d2c3bc9`

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
- **Commit:** BAF `43ebd7a`

<a id="f14"></a>
### F14 – BAF: BIP39-Producer hält jeden abgeleiteten Schlüssel im Speicher → OutOfMemory bei langen Läufen
- **Zeit:** 2026-10-17 00:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/BIP39KeyProducer.java` (`nextKey`, ehem. `keyChain.getKeyByPath(path, true)`)
- **Schwere:** hoch
- **Beschreibung:** `DeterministicKeyChain.getKeyByPath(path, true)` (bitcoinj 0.17.1) legt jeden abgeleiteten Schlüssel dauerhaft in der `DeterministicHierarchy` des Chains ab. Der BIP39-Producer leitet pro Kandidat einen Schlüssel ab. **Gemessen:** Cache wächst exakt um 1 Eintrag pro Schlüssel; 200 000 Schlüssel = **103 MB** dauerhaft belegt (~540 Byte/Schlüssel). Mit dem Test-/Default-Heap von 2 GB ist nach ~4 Mio. Kandidaten Schluss. Fix: Nur der private Schlüssel am Basispfad wird gehalten, jedes Kind direkt mit `HDKeyDerivation.deriveChildKey(parent, child)` abgeleitet. Gleiche Schlüssel, nachgerechnet für 1000 normale Indizes und gehärtete Kinder; nach dem Fix bleibt bei 200 000 Schlüsseln **nichts** zurück (−1 MB gemessen).
- **Reproduktion:** `BIP39KeyProducerTest#producer_keepsNoKeyChainThatCachesEveryDerivedKey` (vorher rot, danach grün; strukturell, weil die Cache-Größe nur über bitcoinj-Interna sichtbar ist) + `#nextKey_matchesTheKeyChainDerivation_forPlainAndHardenedChildren` (Gleichwertigkeit, normal und gehärtet). `BIP39KeyProducerTest` 296/296, `KeyProducerJavaBip39Test` 1/1. Messprogramm (Heap vor/nach 200k `nextKey()`): Scratchpad `bip/Q.java`.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `f1dcbb9`

<a id="f15"></a>
### F15 – BAF: Beim Shutdown werden noch eingereihte Batches nie geprüft (Treffer gehen verloren)
- **Zeit:** 2026-10-17 00:35 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/consumer/ConsumerJava.java` (`consumeKeysRunner`, Schleifenende)
- **Schwere:** hoch (stiller Verlust geprüfter Arbeit inkl. möglicher Treffer)
- **Beschreibung:** `Finder.shutdownAndAwaitTermination()` stoppt zuerst die Producer, dann `ConsumerJava.interrupt()`. Laut Javadoc von `CConsumerJava.awaitQueueEmptySeconds` wartet das darauf, dass der Worker-Pool „die Keys-Queue leert“. Die Worker-Schleife (`while (shouldRun.get())`) endete aber sofort, als `shouldRun` auf `false` ging, ohne die Queue zu leeren. Alles, was noch eingereiht war (bis `queueSize` = 10 Batches à `2^batchSizeInBits` Schlüssel), blieb ungeprüft. Betroffen ist jeder Lauf, der von selbst endet: inkrementeller Bereich bis zum Ende, Secrets-Datei, `runOnce`, Socket-Feed mit Timeout. Gerade das Ende des Bereichs bzw. die letzten Zeilen fehlten, ein Treffer dort ging verloren. Mit Result-Broadcast fehlte für diese Bereiche auch das Event. Fix: Jeder Worker leert die Queue nach der Schleife einmal (`consumeKeys`), bevor er endet.
- **Reproduktion:** `ConsumerJavaTest#interrupt_batchesStillQueued_areCheckedBeforeTheConsumerStops` (Listener hält Batch 1 fest, Batch 2 liegt in der Queue, Shutdown beginnt; vorher rot: 1 statt 2 geprüfte Batches, danach grün). `ConsumerJavaTest` 34/34, `FinderTest` 21/21.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `90457e3`

<a id="f19"></a>
### F19 – BAF: ZeroMQ-Ergebnis-Broadcast aus mehreren Consumer-Threads korrumpiert den Socket → Consumer-Threads hängen für immer
- **Zeit:** 05:20 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/ZmqResultBroadcaster.java:183` (`send`, ebenso `close`)
- **Schwere:** hoch
- **Beschreibung:** `ConsumerJava` ruft seine `ResultListener` aus allen `consumerJava.threads` Worker-Threads auf; ein ZeroMQ-Socket ist nicht thread-sicher. Mit `keyProducerJavaZmq.broadcastResults = true` und mehr als einem Consumer-Thread senden mehrere Threads gleichzeitig auf demselben `PUB`-Socket: jeromq wirft intern `IndexOutOfBoundsException` (wird von `send` geschluckt und nur geloggt), danach drehen die sendenden Threads endlos – die Consumer prüfen keine Schlüssel mehr, der Scan steht still, ohne Fehler. Ebenso lief `close()` (Shutdown-Pfad, `Finder.interrupt`) unsynchronisiert parallel zu Sends.
- **Fix:** `send` und `close` teilen ein privates Lock; ein Send nach `close` wird übersprungen, `close` ist idempotent. WebSocket- und Socket-Broadcaster geprüft: Java-WebSocket ist thread-sicher, `SocketResultBroadcaster` schreibt jede Nachricht in einem einzigen `write`, das `NioSocketImpl` unter seinem Write-Lock ausführt → keine Verzahnung.
- **Reproduktion:** `ZmqResultBroadcasterTest#onBatchChecked_calledFromSeveralConsumerThreads_everyCallReturns` (8 Threads × 200 Aufrufe; vorher hängt er, nachher 4/4 grün in 1,5 s). Zusätzliche Probe `/home/user/BUGHUNT-2026-10-17-F19-probe.java`: vorher 3 von 3 Läufen Timeout mit 1 100–1 500 `IndexOutOfBoundsException`, nachher 1600/1600 Nachrichten empfangen.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `107e4ac`

<a id="f20"></a>
### F20 – BAF: Ein Ergebnis-Client, der verbindet und nie liest, friert über `SocketResultBroadcaster` den ganzen Scan ein
- **Zeit:** 05:31 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/SocketResultBroadcaster.java:215` (`broadcast`/`writeTo`)
- **Schwere:** hoch
- **Beschreibung:** `onBatchChecked` schrieb auf den Consumer-Worker-Threads synchron per blockierendem `OutputStream.write` an jeden Client. Ein blockierender Socket-Write scheitert nicht, wenn der Peer nicht liest – er wartet ewig, sobald die TCP-Puffer voll sind. Ein einziger Client, der sich auf `resultPort` verbindet (der `ServerSocket` lauscht auf allen Interfaces) und nichts liest, hält damit nach einigen MB alle Consumer-Threads an – der Scan steht, ohne Fehler. Das Klassen-Javadoc behauptete das Gegenteil („will eventually fill its buffer and be dropped on the resulting error rather than blocking forever“).
- **Fix:** pro Client eine begrenzte Queue (4096 Nachrichten) und ein eigener Writer (Executor); `broadcast` legt nur ab, ein Client, dessen Queue voll ist, wird getrennt. Greeting wird vor der Registrierung eingereiht (bleibt die erste Zeile). `close()` beendet auch die Writer. Javadoc korrigiert.
- **Reproduktion:** `SocketResultBroadcasterTest#onBatchChecked_clientConnectedButNeverReading_doesNotBlockTheCaller` (vorher `TimeoutException` nach 15 s, nachher 6/6 grün in 1,1 s); `BitcoinAddressFinderArchitectureTest` 17/17, `FinderTest` 21/21 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `d0519ca`

<a id="f21"></a>
### F21 – BAF: `WebSocketEndpoint` lässt einen Nicht-Daemon-Thread zurück → JVM beendet sich nach einem Find-Lauf mit WebSocket-Key-Producer nie
- **Zeit:** 05:41 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/WebSocketEndpoint.java:176` (`start`)
- **Schwere:** mittel
- **Beschreibung:** `start()` übergab `WebSocketServer.start()` – das sofort zurückkehrt und eigene Threads startet – an einen frisch erzeugten `Executors.newSingleThreadExecutor()`, der nie heruntergefahren wurde. Dessen untätiger Worker (`pool-N-thread-1`) ist kein Daemon und überlebt `stop()`. Da `Main` kein `System.exit` aufruft, hält er die JVM am Leben: ein Lauf, dessen Producer enden (z. B. `timeoutMillis` > 0 → `NoMoreSecretsAvailableException`), kehrt aus `main` zurück, der Prozess aber nie. Probe: nach `start(); stop();` bleibt `[pool-1-thread-1]` übrig, die JVM wird erst vom `timeout` beendet (Exit 124).
- **Fix:** Executor direkt nach dem `submit` per `shutdown()` beenden (die Startaufgabe läuft noch zu Ende).
- **Reproduktion:** neuer `WebSocketEndpointTest#stop_afterStart_leavesNoNonDaemonThreadBehind` (vorher rot: `<[Thread[#31,pool-2-thread-1,5,main]]>`, nachher grün); `KeyProducerJavaWebSocketTest` 5/5 und `WebSocketResultBroadcasterTest` 4/4 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `7469a72`

<a id="f22"></a>
### F22 – BAF: `KeyProducerJavaSocket` – `connectRetryCount` ist ein Lebenszeit-Budget → Producer stirbt still nach N Trennungen
- **Zeit:** 05:46 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/keyproducer/KeyProducerJavaSocket.java:108` (Reader-Schleife in `start`)
- **Schwere:** mittel
- **Beschreibung:** Jede verlorene Verbindung (Peer trennt, `End of stream`, oder `SO_TIMEOUT` von 3 s auf einem ruhenden Feed) zählte `attempts` hoch; zurückgesetzt wurde der Zähler nie – auch nicht nach einer erfolgreichen Verbindung. Nach insgesamt `connectRetryCount` (Default 5) Trennungen über die ganze Laufzeit endet der Reader endgültig (im Server-Modus wird auch der `ServerSocket` geschlossen), die `IllegalStateException` landet ungelesen im `Future`, und der Producer liefert still keine Secrets mehr (`createSecrets` → `NoMoreSecretsAvailableException`). Ein Feed, der z. B. fünfmal ein paar Sekunden pausiert, legt den Scan damit dauerhaft still.
- **Fix:** `attempts = 0` sobald eine Verbindung steht; `connectRetryCount` begrenzt damit wie dokumentiert die Versuche *pro* Verbindungsaufbau.
- **Reproduktion:** `KeyProducerJavaSocketTest#createSecrets_peerReconnectsMoreOftenThanTheRetryCount_keepsReading` (4 Verbindungen à 1 Secret bei `connectRetryCount = 2`; vorher `NoMoreSecretsAvailableException` beim dritten Secret, nachher grün; Klasse 18/18).
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `260f4db`

<a id="f23"></a>
### F23 – BAF: Adresszeilen mit führendem Leerraum werden beim Import als „address is empty“ verworfen
- **Zeit:** 05:51 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/io/AddressTxtLine.java:118` (`fromLine`, Ursache in `SeparatorFormat.split`)
- **Schwere:** niedrig
- **Beschreibung:** `SeparatorFormat` trennt auch an Leerzeichen und Tab. Eine eingerückte Zeile (`" 1Abc…,123"`, `"\t1Abc…"`) ergibt daher ein leeres erstes Token; `fromLine` trimmt zwar die Adressspalte, aber erst nachdem sie schon leer ist, und wirft `REASON_EMPTY`. Beim Import (`AddressFilesToLMDB`) werden solche Zeilen nur als „unsupported“ gezählt – ausgerichtete oder von Hand bearbeitete Adresslisten verlieren still Einträge.
- **Fix:** `fromLine` trimmt die ganze Zeile vor dem Aufteilen.
- **Reproduktion:** `AddressTxtLineTest#fromLine_addressWithLeadingWhitespace_returnHash160AndAmount` (Leerzeichen, Tab, zwei Leerzeichen; vorher `AddressFormatNotAcceptedException: address is empty`, nachher grün). `AddressTxtLineTest` 291/291, `SeparatorFormatTest` 83/83, `AddressFilesToLMDBTest` 24/24 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `f7c8d6c`

<a id="f27"></a>
### F27 – BAF: LMDB-Map wird bei vollem Speicher nur einmal vergrößert → Batch-Import bricht mit `MapFullException` ab
- **Zeit:** 06:16 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/persistence/lmdb/LMDBPersistence.java:556` (`putNewAmounts`/`putNewAmountWithAutoIncrease`)
- **Schwere:** mittel
- **Beschreibung:** Bei `MapFullException` wurde die Map um genau einen Schritt (`increaseSizeInMiB`) vergrößert und noch einmal geschrieben; reichte das nicht, flog die Ausnahme. `AddressFilesToLMDB` schreibt aber 32 768 zufällige Schlüssel pro Transaktion (`writeBatchSize`), die je eine Seite verschmutzen – das kann mehrere MiB auf einmal brauchen, und das mitgelieferte `examples/config_AddressFilesToLMDB.json` setzt `increaseSizeInMiB: 1`. Ein echter (großer) Import bricht damit ab; der CI-Smoke merkt es nicht, weil die Beispieldateien in die initialen 16 MiB passen. Der alte Kommentar nannte das ein Konfigurationsproblem – mit Batch-Schreiben ist es aber die Standardkonfiguration.
- **Fix:** beide Schreibpfade vergrößern die Map schrittweise, bis die Transaktion passt (`growMapOrRethrow`); `increaseSizeInMiB = 0` oder `increaseMapAutomatically = false` werfen wie bisher. Javadoc von `CLMDBConfigurationWrite.increaseSizeInMiB` angepasst.
- **Reproduktion:** `LMDBPersistenceTest#putNewAmounts_batchLargerThanOneIncreaseStep_mapGrowsUntilTheBatchFits` (128 Ki Einträge in einer Transaktion bei 1 MiB Schrittweite; vorher `MapFullException: Environment mapsize reached`, nachher grün). `LMDBPersistenceTest` 50/50 (inkl. der bestehenden Tests zur genauen Anzahl der Vergrößerungen), `AddressFilesToLMDBTest` 24/24 grün.
- **Status:** gefixt
- **Commit:** BitcoinAddressFinder `ccdb3cf`

<a id="f31"></a>
### F31 – BAF: `LMDBCompact` löscht die Quell-Datenbank, wenn das Ziel die Quelle unter anderer Schreibweise ist
- **Zeit:** 06:45 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBCompact.java:62`
- **Schwere:** hoch (Datenverlust der importierten Adressdatenbank)
- **Beschreibung:** Die Schutzprüfung „targetDirectory muss sich vom lmdbDirectory unterscheiden" verglich nur `getAbsolutePath()`. `<quelle>/.`, `<quelle>/../<quelle>`, `./lmdb` gegen `lmdb` oder ein Symlink auf die Quelle bestehen die Prüfung; danach löscht `run()` „eine frühere Kompaktierung" – also `data.mdb` **der Quelle** – noch bevor die Quelle geöffnet wird. Fix: Vergleich der kanonischen Pfade (`getCanonicalFile`). Nebenbefund ohne Fix: Das Feld `shouldRun` wird in `run()` nie gelesen, obwohl der Kommentar sagt, es verhindere einen noch nicht gestarteten Lauf (`interrupt()` ist damit wirkungslos).
- **Reproduktion:** `LMDBCompactTest#compactLMDB_targetIsSourceSpelledDifferently_throwsAndKeepsTheSourceDatabase` (ohne Fix: Quelle gelöscht, `LmdbNativeException`; mit Fix 3/3 grün)
- **Status:** gefixt
- **Commit:** 0535ba6

<a id="f32"></a>
### F32 – BAF: `LMDBCompact.interrupt()` ist wirkungslos – ein vor dem Start unterbrochener Lauf löscht und kopiert trotzdem
- **Zeit:** 2026-10-17 10:08 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/command/LMDBCompact.java:57`
- **Schwere:** niedrig
- **Beschreibung:** `interrupt()` setzte `shouldRun` auf `false`, aber `run()` las das Feld nie, obwohl der Kommentar sagt, es verhindere einen noch nicht gestarteten Lauf. Ein vor dem Start unterbrochener Compact-Befehl legte trotzdem das Zielverzeichnis an, löschte eine frühere Kompaktierung (`data.mdb` im Ziel) und kopierte die ganze Datenbank. Nebenbefund aus F31. Fix: `run()` prüft `shouldRun` zuerst und kehrt ohne Schreibzugriff zurück.
- **Reproduktion:** `LMDBCompactTest#compactLMDB_interruptedBeforeRun_writesNothing`
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <false> but: was <true>“: Zielverzeichnis angelegt), mit Fix grün 4/4 (skipped 0), Gegenprobe per `git stash` rot, nach `stash pop` wieder grün 4/4.
- **Status:** gefixt
- **Commit:** BAF `833b879`

<a id="f33"></a>
### F33 – BAF: `OpenClLibraryLoader.ensureLoaded()` initialisiert OpenCL bei gleichzeitigem Erstaufruf zweimal
- **Zeit:** 2026-10-17 10:25 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/binding/OpenClLibraryLoader.java:82`
- **Schwere:** niedrig
- **Beschreibung:** `ensureLoaded()` war ein Check-then-act ohne Synchronisation, `loaded` nicht `volatile`. Zwei Threads, die gleichzeitig den ersten OpenCL-Aufruf machen, riefen beide `libraryInitializer.create()`; LWJGLs zweites `CL.create()` wirft eine `IllegalStateException`, die hier niemand fängt. Im Programm selbst werden die OpenCL-Producer nacheinander initialisiert, daher niedrig. Fix: privates `loadLock` um den Rumpf, `loaded` `volatile` (für `isLoaded()`).
- **Reproduktion:** `OpenClLibraryLoaderTest#ensureLoaded_calledFromTwoThreadsAtOnce_initializesOnlyOnce` (der Fake hält den ersten `create()` fest, bis der zweite Thread `ensureLoaded()` betreten hat)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is <1> but: was <2>“), mit Fix grün 9/9 (skipped 0), Gegenprobe per `git stash` rot, nach `stash pop` grün 9/9.
- **Status:** gefixt
- **Commit:** BAF `4328bde`

<a id="f34"></a>
### F34 – BAF: `Finder.interrupt()` aus Shutdown-Hook und Main-Thread gleichzeitig → `ConcurrentModificationException` im Shutdown
- **Zeit:** 2026-10-17 10:52 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/engine/Finder.java:724` (`interrupt`), Aufrufer `cli/Main` (Shutdown-Hook) und `Finder.shutdownAndAwaitTermination`
- **Schwere:** mittel
- **Beschreibung:** Bei Ctrl-C ruft der Shutdown-Hook `Main.interrupt()` → `finder.interrupt()`. Dadurch enden die Producer, `awaitTermination` im Main-Thread kehrt zurück, und `shutdownAndAwaitTermination()` ruft **gleichzeitig** ein zweites `interrupt()`. Beide Aufrufe liefen über dieselben `ArrayList`s (`zmqBroadcasters`, `socketBroadcasters`) und leerten sie; der eine `clear()`te, während der andere noch iterierte → `ConcurrentModificationException` im Shutdown, die übrigen Broadcaster und Producer wurden in diesem Thread nicht mehr freigegeben. (Schlüssel-Producer und Producer laufen über Kopien, `ProducerReleaser` gibt jeden genau einmal frei – die Broadcaster-Listen waren die Lücke.) Fix: privates `interruptLock` serialisiert `interrupt()`; der zweite Aufruf findet die Listen leer.
- **Reproduktion:** `FinderTest#interrupt_calledFromTwoThreadsAtOnce_neitherFails` (ein ZMQ-Broadcaster auf `inproc://`, dessen erstes `close()` festgehalten wird, per Reflection in die private Liste gelegt)
- **Rot-Grün-Nachweis:** ohne Fix rot („Expected: is null but: was <java.util.ConcurrentModificationException>“), mit Fix grün, `FinderTest` 22/22, `BitcoinAddressFinderArchitectureTest` 17/17; Gegenprobe per `git stash` rot, nach `stash pop` grün. `spotbugs:check` grün.
- **Status:** gefixt
- **Commit:** BAF `cf46353`

<a id="f35"></a>
### F35 – BAF: Javadoc begründet den GC vor dem GPU-Filter-Upload mit `CL_MEM_COPY_HOST_PTR`, das der Code nicht mehr nutzt
- **Zeit:** 2026-10-17 16:24 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/opencl/GcTrigger.java:15` (Klassen-Javadoc „Why this exists in the upload path“), `opencl/OpenCLContext.java:466` (`reclaimHeapBeforeUpload`), `opencl/OpenCLContext.java:683` (`uploadGpuFilter`: „Two CL_MEM_READ_ONLY | CL_MEM_COPY_HOST_PTR buffers“)
- **Schwere:** niedrig (nur Dokumentation)
- **Beschreibung:** Die drei Javadocs begründen den GC vor dem Filter-Upload damit, dass der Treiber für `CL_MEM_COPY_HOST_PTR` einen Host-Staging-Bereich anheften muss. Der Code nutzt `CL_MEM_COPY_HOST_PTR` nicht mehr: Die Puffer werden mit `ClBufferFlags.READ_ONLY` angelegt und über `ClApi.writeBufferChunked` in Scheiben durch einen kleinen wiederverwendeten Staging-Puffer beschrieben (so auch CLAUDE.md, Abschnitt „OpenCL / GPU Code“). Die Doku beschreibt damit einen Mechanismus, den es nicht mehr gibt; ob der GC mit dem gestückelten Upload überhaupt noch nötig ist, ist offen (er ist harmlos, kostet einmalig Millisekunden). Verhalten korrekt: `reclaimHeapBeforeUpload()` läuft in `allocateFilterBuffers` unmittelbar vor `createBuffer`.
- **Weitere Doku-Abweichung derselben Art (2026-10-17):** `persistence/AddressLookup.java:25` zeigt im Beispiel `consumerJava.useLookup(lookup)`; diese Methode gibt es nicht, sie heißt `ConsumerJava.initWithLookup(AddressPresence)`.
- **Reproduktion:** keine – reine Dokumentationsabweichung, kein Verhalten, das ein Test rot machen könnte.
- **Status:** nur beschrieben (Nutzerregel vom 2026-10-17: gefixt und committet wird nur mit Test; eine reine Doku-Abweichung hat keinen, bleibt also unverändert)
- **Commit:** –

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
- **Commit:** BAF `be8ff36`

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
- **Commit:** BAF `767708a`

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
- **Commit:** BAF `b068ef1`

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
- **Commit:** BAF `fa1d418`

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
- **Commit:** BAF `b94d720`

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
- **Commit:** BAF `2d7c23c`

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
- **Commit:** BAF `61d9cc2`

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
- **Commit:** BAF `396b967`

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
- **Commit:** BAF `20dde71`

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
- **Commit:** BAF `1a957de`

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
- **Commit:** BAF `e245947`

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
- **Commit:** BAF `d871b31`

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
- **Commit:** BAF `338ec00`

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
- **Commit:** BitcoinAddressFinder `3c355ee`

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
- **Commit:** BitcoinAddressFinder `6c94e19`

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
- **Commit:** BitcoinAddressFinder `9d66eb7`

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
- **Commit:** BitcoinAddressFinder `eb3ee16`

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
- **Commit:** BitcoinAddressFinder `59dfecf`

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
- **Commit:** BitcoinAddressFinder `6cb074f`

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
- **Commit:** BitcoinAddressFinder `8abd2d0`

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
- **Commit:** BitcoinAddressFinder `1107caf`

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
- **Commit:** BitcoinAddressFinder `279f8de`

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
- **Commit:** BitcoinAddressFinder `b230cfa`

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
- **Commit:** BitcoinAddressFinder `c5dd4d2`

<a id="f129"></a>
### F129 – BitcoinAddressFinder: Der OpenCL-Producer verfehlt die Schlüssel 2 … keysPerWorkItem−1, weil das erste Work-Item der Basis 0 bei 0·G verankert ist
- **Zeit:** 2026-10-18 18:10 UTC
- **Datei:** `src/main/resources/inc_ecc_secp256k1custom.cl:1058` (Anker `P0 = (baseK0 | base_offset)·G` per `point_mul_xy_comb`, Kommentar „Degenerate case … impossible for the supported aligned sequential / random ranges“ darüber), Aufrufer `producer/ProducerOpenCL`, Auswertung `opencl/OpenCLGridResult.readEntry`
- **Schwere:** niedrig
- **Beschreibung:** Jedes Work-Item berechnet seine `keysPerWorkItem` Schlüssel als `P0 + m·G` aus einem gemeinsamen affinen Anker `P0`. Für das erste Work-Item eines Rasters mit Basis 0 ist der Anker der Skalar 0, also der Punkt im Unendlichen, der affin nicht darstellbar ist: alle Schlüssel dieses Work-Items werden falsch berechnet. `OpenCLGridResult.readEntry` ersetzt nur den Schlüssel 0 selbst durch `INVALID_KEY_ONE`, die übrigen Einträge 2 … keysPerWorkItem−1 tragen falsche Koordinaten und Hashes; im kompakten Filtermodus verwirft schon der GPU-Filter sie. Basis 0 ist kein Sonderfall: der inkrementelle Key-Producer beginnt standardmäßig bei `MIN_VALID_PRIVATE_KEY` = 2, und das Raster wird auf 0 abgerundet – jeder solche Scan auf der GPU überspringt still die kleinsten Schlüssel, die der CPU-Pfad findet. Praktisch betroffen sind nur die winzigen Schlüssel (die Puzzles 2–4 liegen unter 16, sind aber längst gelöst). *Nachtrag 2026-10-18 18:40 UTC:* Die ursprünglich mitbehauptete Entartung am oberen Ende (`k0 + m ≡ 0 (mod n)`) ist gemessen nicht erreichbar: `OpenClTask.setSrcPrivateKeyChunk` weist jede Basis über `PrivateKeyValidator.getMaxPrivateKeyForBatchSize` mit `PrivateKeyTooLargeException` ab (ERROR, die Charge entfällt), sodass die GPU kein Raster rechnet, das n überschreitet; mit ausgerichteten Basen bliebe nur `batchSizeInBits` 1 (dort lässt die Grenze `n − (2^b − 1)` den Schlüssel n als letzte Rasterzahl zu, nach F45 eins zu weit). Gemessen auf pocl mit Start auf einer ausgerichteten Basis und `endPrivateKey` = n+190: die CPU findet alle 113 Ziele n−1 … n−299, die GPU (mit `keysPerWorkItem` 16 wie 1) alle außer den 35 im obersten Raster n−65 … n−1, das sie wegen dieser Grenze verwirft – ein dokumentierter Unterschied der Rastermodi, kein weiterer Fehler.
- **Fix:** offen, Entscheidung des Nutzers, weil der Kernel leistungskritisch ist. Möglich: im Kernel für einen Anker 0 (bzw. `k0 + m ≡ 0`) auf einen anderen Anker ausweichen (etwa `P0 = 1·G` und `m` um eins verschoben), oder auf dem Host das erste Work-Item eines Rasters mit Basis 0 auf der CPU nachrechnen (hilft nur ohne GPU-Filter). Der Kernel-Kommentar zum „unmöglichen“ Entartungsfall ist in jedem Fall falsch.
- **Reproduktion:** Ende zu Ende über die CLI mit pocl (`apt-get install pocl-opencl-icd`): Datenbank mit 682 Zieladressen (jeder dritte Schlüssel 3…2046) plus 5000 Rauschadressen, inkrementeller Key-Producer 2…0x7ff, `producerOpenCL` mit `batchSizeInBits` 8. Mit `keysPerWorkItem` 16: 677 Treffer, es fehlen genau 3, 6, 9, 12, 15 – gleich in voller Übertragung, mit FUSE_8 und mit FUSE_16. Mit `keysPerWorkItem` 1: alle 682. Der CPU-Producer findet in derselben Lage alle 682 (siehe BUGHUNT-CHECKED.md, Lookup-Backends). Ein `@OpenCLTest`, der für Basis 0 und `keysPerWorkItem` 16 die Hashes der Einträge 2…15 mit `PublicKeyBytes.fromPrivate` vergleicht, wäre rot; er wurde nicht eingecheckt, weil er ohne Fix den `test-opencl`-Job rot machte.
- **Rot-Grün-Nachweis:** —
- **CI-Bedarf:** Ein Fix braucht den `test-opencl`-Job (pocl) und eine Messung der Kernel-Laufzeit auf echter GPU.
- **Status:** nur beschrieben
- **Commit:** —

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
- **Commit:** BitcoinAddressFinder `68a80f9`

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
- **Commit:** BitcoinAddressFinder `035f2ac`

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
- **Commit:** BitcoinAddressFinder `2c6841a`

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
- **Commit:** BitcoinAddressFinder `275fc61`

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
- **Commit:** BitcoinAddressFinder `9e317c5`

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
- **Commit:** BitcoinAddressFinder `4264e7a`

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
- **Commit:** BitcoinAddressFinder `b124070`

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
- **Commit:** ba6f610

<a id="f142"></a>
### F142 – BitcoinAddressFinder: Javadoc von `putNewAmountWithAutoIncrease` beschreibt noch den Zustand vor F27
- **Zeit:** 2026-10-19 00:40 UTC
- **Datei:** `src/main/java/net/ladenthin/bitcoinaddressfinder/persistence/lmdb/LMDBPersistence.java:483` (Javadoc von `putNewAmountWithAutoIncrease`)
- **Schwere:** niedrig
- **Beschreibung:** Die Javadoc sagt, nach `MapFullException` werde die Map um `increaseSizeInMiB` vergrößert „and the insert is retried once“, und „if the second attempt also fails, the configured increase size is too small“. Seit F27 wächst die Map in einer Schleife über `growMapOrRethrow`, bis der Eintrag passt; geworfen wird nur noch bei `increaseMapAutomatically = false` oder `increaseSizeInMiB <= 0`. Ende zu Ende bestätigt: Import von 50 000 Adressen mit `initialMapSizeInMiB` 1 und `increaseSizeInMiB` 1 läuft durch (Exit 0, „50000 written“, `data.mdb` auf 5 MiB gewachsen). Reine Doku-Abweichung, das Verhalten ist das bessere.
- **Fix:** keiner – nach der Regel vom 17. ohne Test keine Änderung; für eine Javadoc gibt es keinen roten Test. Der Satz wäre an die Javadoc von `putNewAmounts`/`growMapOrRethrow` anzugleichen.
- **Reproduktion:** Lesen gegen `putNewAmountWithAutoIncrease` (Schleife `while (true)` um `growMapOrRethrow`).
- **Rot-Grün-Nachweis:** – (nicht gefixt)
- **Status:** nur beschrieben
- **Commit:** –

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
- **Commit:** –

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
- **Commit:** –

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
- **Commit:** –

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
- **Commit:** –

## Offene Verdachte ohne Fund-ID

- **`PrivateKeyValidator.getMaxPrivateKeyForBatchSize` lässt n im GPU-Raster zu (2026-10-17, Folge von F45):** `maxSafeKey = n − 2^b + 1` sichert nur `base + 2^b − 1 ≤ n`, das letzte zulässige OpenCL-Raster endet also auf n. Dort stürzt nichts ab (der Kernel liefert für n·G Koordinaten, die zu keinem Eintrag passen), es wird nur ein sinnloser Schlüssel geprüft. Die Korrektur auf `n − 2^b` ändert drei Tests von `getMaxPrivateKeyForBatchSize` und die Grenze von `PrivateKeyTooLargeException` – erst mit Rückmeldung des Nutzers, da kein Fehlverhalten außer einem verschwendeten Rasterplatz.
- **`KeyProducerJavaIncremental`, Teilbatch am Bereichsende (2026-10-17):** `endPrivateKey` heißt in `CKeyProducerJavaIncremental` „inclusive upper bound“, aber mit `returnStartSecretOnly=false` wirft der letzte, nur teilweise in den Bereich passende Batch und die bis zu `overallWorkSize-1` Schlüssel davor werden nie geprüft; mit `returnStartSecretOnly=true` (OpenCL) läuft der Batch dagegen über das Ende hinaus. Beides legen die Tests ausdrücklich fest (Fälle 3, 5, 8, 9 der Tabelle in `KeyProducerJavaIncrementalTest`), also eine Designentscheidung – für den Nutzer zur Entscheidung, nicht angefasst.
- *(latent, niedrig, nur beschrieben, 2026-10-17)* BAF `command/TuneConfiguration.templatesForDevices`: übernimmt `deviceType` vom Prototyp, setzt aber `deviceIndex` auf die Position unter **allen** Geräten der Plattform, während `OpenCLPlatformSelector` nur die zur Maske passenden zählt. Mit einer GPU-Maske auf einer Plattform, die eine CPU vor der GPU listet (pocl + CUDA), adressiert die Vorlage ein nicht vorhandenes Gerät (`Invalid device index: 1 for deviceType: 4`, per Wegwerf-Test gezeigt). Heute unerreichbar: der einzige Aufrufer übergibt `autoDiscoveryPrototype`, also `CL_DEVICE_TYPE_ALL`. Würde je ein vom Nutzer konfigurierter Producer als Prototyp dienen, wäre der Fix `copy.deviceType = CL_DEVICE_TYPE_ALL` in der Schleife; der Test steht in der Notiz zu `OpenCLPlatformSelector` in `BUGHUNT-CHECKED.md`.
- *(außerhalb des Ziels, niedrig, nur beschrieben, 2026-10-17)* BAF `examples/websocket_scanner.html` `start()`: auch „To (hex)“ wird per `alignDown` abgerundet und die Schleife sendet nur `nextBase < toBase`. Ist „To“ nicht auf ein Vielfaches von 2^batchSizeInBits ausgerichtet, bleibt der Rest zwischen dem abgerundeten Wert und „To“ ungeprüft, und „Range complete.“ wird trotzdem gemeldet – genau das stille Auslassen, vor dem der Kommentar über `alignDown` warnt. Fix wäre, „To“ aufzurunden (`((to + step - 1n) / step) * step`). Kein Java-Code, daher kein Rot-Grün-Test in diesem Loop; Entscheidung des Nutzers.
- *(latent, niedrig, nur beschrieben, CI-Bedarf: OpenCL, 2026-10-17, Musterdurchsicht nach F66)* BAF `producer/ProducerReleaser.release` → `ProducerOpenCL.releaseProducer`: läuft `waitTillProducerNotRunning` nach `shutdownTimeoutSeconds` (300 s) ab, wird trotzdem `releaseProducer()` gerufen und gibt Kernel, Queue, `dstMem` und Kontext frei, während der Producer noch laufen kann. Ist er gerade in `OpenClTask.executeKernel`, benutzt er freigegebene Handles (undefiniert im Treiber); später endet er nur mit einer geloggten NPE (`requireNonNull(openClTask)`). Auf dem normalen Pfad sicher: die Result-Reader lesen nur Java-Direct-Buffer, nie `dstMem` (so in `OpenClTask.close` begründet). Ohne OpenCL-Gerät hier nicht testbar; ein Mock bewiese nur die Reihenfolge, nicht den Schaden. Möglicher Fix: bei Timeout die Freigabe auslassen (Leck statt Use-after-free) oder dem Producer-Thread am Ende überlassen.
- *(Designfrage, Entscheidung des Nutzers, niedrig, 2026-10-17)* BAF Export `HexHash` → Import: `AddressTxtLine.fromLine` kennt kein reines Hex-hash160 (nur das Riecoin-ScriptPubKey `76a914…`). Eine 40-stellige Hex-Zeile fällt bis zum Base58-Fallback durch; enthält sie eine `0` (nicht im Base58-Alphabet), wird sie verworfen, sonst (Anteil (15/16)^40 ≈ 7,6 %) zu einem **falschen** hash160 dekodiert und importiert, weil Prüfsummenfehler laut Bulk-Import-Vertrag nur auf DEBUG geloggt und angenommen werden. README verspricht den Hex-Import nicht, sagt aber „malformed lines are silently skipped“ – für diese Zeilen stimmt das nicht. Ein Hex-Zweig (40 Hex-Zeichen → hash160) wäre der naheliegende Fix, ist aber eine Formaterweiterung, daher nicht still umgesetzt.
- *(mittel, unsicher, nur beschrieben, Entscheidung des Nutzers, 2026-10-18)* `Finder.announceGridConfigurationToBroadcasters` meldet allen Ergebnis-Clients `max(batchSizeInBits)` über **alle** konfigurierten Producer (`configuredProducerConfigs`), auch über solche, die gar nicht aus dem sendenden Key-Producer gespeist werden. Beispiel: ein `producerJava` mit `keyProducerJavaRandom` und `batchSizeInBits` 20 neben einem `producerOpenCL` am WebSocket-Key-Producer mit 18 Bit: der WebSocket-Client erfährt 20 und schreitet um 2^20 voran, der Producer, der seine Basen bekommt, prüft ab der auf 2^18 abgerundeten Basis nur 2^18 Schlüssel – drei Viertel jedes Schritts bleiben ungeprüft, ohne Meldung. Der Kommentar begründet das Maximum damit, dass es „Bereiche nicht überlappen“ lässt; bei mehreren Producern **am selben** Key-Producer erzeugt ein zu großer Schritt aber ebenso Lücken (jede Basis geht an genau einen Producer), ein zu kleiner nur doppelte Prüfung (die Abrundung auf das größere Gitter deckt den ganzen Block ab). Vorschlag: je Broadcaster nur die Producer mit dessen `keyProducerId` zählen und dort das **Minimum** melden, oder unterschiedliche Größen am selben Key-Producer beim Start ablehnen. Testbar wäre das über eine paketinterne Naht für die Gitterberechnung; wegen der Abwägung Lücke gegen Doppelprüfung nicht ohne Entscheidung geändert.
- *(niedrig, nur beschrieben, 2026-10-18)* BitcoinAddressFinder `keyproducer/WebSocketEndpoint.java:54`: `greetingSupplier` ist nicht `volatile`, wird aber erst nach dem Start des Servers gesetzt – `Finder.startKeyProducer()` startet den Server (`KeyProducerJavaWebSocket.start()` → `endpoint.start()`), `startConsumer()` baut danach den `WebSocketResultBroadcaster`, dessen Konstruktor `setGreetingSupplier` aufruft. Gelesen wird das Feld in `onOpen` auf dem Worker-Thread von Java-WebSocket; zwischen Schreiben und Lesen gibt es keine Happens-before-Kante, ein Client könnte nach dem JMM den Gruß dauerhaft nicht bekommen. `messageHandler` dagegen wird vor `start()` gesetzt (`Thread.start` ordnet). Praktisch sichtbar ist das auf HotSpot/x86 nicht, ein Rot-Test ist deterministisch nicht herstellbar (auch jcstress zeigt solche Sichtbarkeitslücken auf x86 nicht), daher kein Fix; Vorschlag: `volatile`. Die Reihenfolge Registrieren-vor-`onOpen` ist in Java-WebSocket 1.6.0 dagegen richtig (`onWebsocketOpen`: `addConnection`, dann `onOpen`), das F119-Fenster gibt es dort nicht.
- *(niedrig, nur beschrieben, 2026-10-18)* BitcoinAddressFinder `io/AddressTxtLine.java:331` (`getCoinIfPossible`), per Probe-Klasse gemessen: Eine Betragsspalte, die `Long.parseLong` nicht versteht, wird still zu `DEFAULT_COIN` (1) – ein Dezimalbetrag in BTC (`1.5`), Text (`abc`), ein Wert über `Long.MAX_VALUE` und eine leere zweite Spalte (`A,,5`) ergeben alle `coin=1`, ohne Warnung; ein negativer Betrag (`-5`) wird übernommen. Die README sagt nur „optionally include an associated coin amount“, Einheit und Fehlerverhalten sind nicht festgelegt, und der Betrag ist für die Suche nur Begleitinformation, daher kein Fix ohne Entscheidung (Vorschlag: bei unlesbarem Betrag einmal je Datei warnen, negative ablehnen). Ebenfalls gemessen und unauffällig: Groß-Bech32 inklusive `LTC1…`; eine Base58-Adresse mit falscher Prüfsumme wird ohne Prüfung importiert (gewollt, mehrere Altcoins rechnen sie anders), P2SH-CashAddr (`p…`) wird abgelehnt (README: BCH P2SH ❌).
- *(Designfrage, Entscheidung des Nutzers, niedrig, 2026-10-18)* BAF `ZmqResultBroadcaster`: Linger 0 verwirft beim Schließen, was der `PUB`-Socket noch nicht gesendet hat. Lokal gemessen geht nichts verloren (3 von 3 Läufen vollständig), über ein langsames Netz könnten die letzten Batches – das Abschlusssignal – fehlen. Der Socket-Broadcaster liefert seit F130 bis zu 2 s nach; ein gleicher begrenzter Linger (etwa 2000 ms) würde beide Kanäle angleichen, kostet aber genau die Verzögerung, die die Javadoc ausschließen will.
- **Designfrage `TuneConfiguration.extendSweepBeyondTheCandidates` (2026-10-18, neben F135):** Die Erweiterung endet beim ersten Arm, der den Gewinner nicht schlägt, und meldet dann „The optimum was inside range.“ Liegt der Gewinner auf beiden Rändern (Ende zu Ende: Listen [8, 10] × [1, 4], Gewinner (10, 4)), misst sie nur `keysPerWorkItem`=8; scheitert der, bleibt die Achse `batchSizeInBits` unerweitert, obwohl der Gewinner dort am Rand liegt – die Meldung stimmt dann für diese Achse nicht. Der nächste Aufruf von `nextExtensionArm` würde genau diesen Bit-Arm liefern. Ob nach einem gescheiterten Arm der andere Rand noch probiert werden soll (ein Arm mehr) oder nur die Meldung anzupassen ist, entscheidet der Nutzer.
- **`TuneConfiguration`: Gerät ohne erfolgreichen Arm behält die Werte des letzten Arms (2026-10-18, neben F136):** `runArm` schreibt jedes Arm-Raster in den Producer, `applyWinnerTo(template, null)` tut nichts. Scheitern auf einem Gerät alle Arme, während ein anderes gewinnt, enthält die ausgegebene Konfiguration für das gescheiterte Gerät das zuletzt gemessene (meist größte, gescheiterte) Raster statt der Werte, die der Nutzer angegeben hatte. Was dort stehen soll (ursprüngliche Werte, Gerät weglassen, Hinweis), ist eine Entscheidung des Nutzers.
- **Designfrage Socket-Key-Producer: Konfigurationsfehler beim Binden endet als regulärer Lauf mit Exit 0 (2026-10-19):** Ende zu Ende über die CLI mit `keyProducerJavaSocket` im Modus SERVER, `port` = `resultPort` = 5591, `broadcastResults` true: der Result-Broadcaster bindet den Port zuerst, der Key-Producer scheitert viermal mit „Connection attempt n failed: Address already in use“ (WARN), dann endet der Producer nach `timeoutMillis` 3000 mit „No more keys in secret factory“ und Find mit Exit 0 – ohne einen einzigen Schlüssel geprüft zu haben. Dasselbe tritt bei jedem belegten Port ein, solange `timeoutMillis` kürzer ist als `connectRetryCount` × `connectRetryDelayMillis` (dann kommt die vorgesehene ERROR-Meldung „Unable to establish socket connection“ nie). Zwei kleine Möglichkeiten, zu entscheiden vom Nutzer: `port == resultPort` bei SERVER beim Start abweisen; und/oder einen Lauf, dessen Key-Producer nie eine Verbindung hatte, nicht als regulär beenden.
