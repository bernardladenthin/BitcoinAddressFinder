
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
- **Status:** nur beschrieben (Doku-Korrektur braucht nach der Loop-Regel „ohne roten Test kein Fix“ die Freigabe des Nutzers)
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
- **Beschreibung:** `readFile` dekodiert jede Zeile als UTF-8, entfernt aber eine Byte-Order-Mark (EF BB BF) am Dateianfang nicht; Windows-Editoren schreiben UTF-8 oft so. Die erste Zeile beginnt dann mit U+FEFF. Bei `secretFormat` `STRING_SHA256` wird die erste Passphrase mitsamt BOM gehasht: ein falsches Secret, ohne Fehler und ohne Zählung – die gemeinte Brainwallet wird nie geprüft. Bei `BIG_INTEGER`, `SHA256` und `DUMPED_PRIVATE_KEY` scheitert die erste Zeile, bei Adressdateien (`AddressFile`, `AddressFilesToLMDB` über dieselbe Basis) ist die erste Adresse „unsupported“.
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

## Offene Verdachte ohne Fund-ID

- **JSON-Konfiguration mit UTF-8-BOM (2026-10-17):** `cli/Main.readString` reicht den Dateiinhalt unverändert an Jackson; `ObjectMapper` lehnt ein führendes U+FEFF ab („Unexpected character (code 65279 / 0xfeff)“), `YAMLMapper` überspringt es (in der Sandbox geprüft). Eine mit BOM gespeicherte `.json`-Konfiguration startet also nicht – laut, mit klarer Meldung, aber vermeidbar. Kandidat für die nächste Runde.
- **`KeyProducerJavaIncremental`, Teilbatch am Bereichsende (2026-10-17):** `endPrivateKey` heißt in `CKeyProducerJavaIncremental` „inclusive upper bound“, aber mit `returnStartSecretOnly=false` wirft der letzte, nur teilweise in den Bereich passende Batch und die bis zu `overallWorkSize-1` Schlüssel davor werden nie geprüft; mit `returnStartSecretOnly=true` (OpenCL) läuft der Batch dagegen über das Ende hinaus. Beides legen die Tests ausdrücklich fest (Fälle 3, 5, 8, 9 der Tabelle in `KeyProducerJavaIncrementalTest`), also eine Designentscheidung – für den Nutzer zur Entscheidung, nicht angefasst.
