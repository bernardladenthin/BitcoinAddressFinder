// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.statistics.ReadStatistic;
import org.slf4j.Logger;

@AnalyzeClasses(packages = "net.ladenthin.bitcoinaddressfinder", importOptions = ImportOption.DoNotIncludeTests.class)
public class BitcoinAddressFinderArchitectureTest {

    /**
     * The CLI entry point is the top-level consumer; no other package should import it.
     */
    @ArchTest
    static final ArchRule cliIsLeaf = noClasses()
            .that()
            .resideOutsideOfPackage("net.ladenthin.bitcoinaddressfinder.cli..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder.cli..");

    /**
     * Test-framework classes must not appear in production code.
     */
    @ArchTest
    static final ArchRule noTestFrameworksInProduction = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.junit..", "net.jqwik..", "com.tngtech.archunit..");

    /**
     * Production code must not use {@code java.util.logging} directly; all logging
     * goes through SLF4J / Logback.
     */
    @ArchTest
    static final ArchRule noJavaUtilLogging = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("java.util.logging..");

    /**
     * Every SLF4J {@link Logger} field follows the {@code private static final} idiom.
     * Locks the convention used by the ongoing logger-DI migration.
     */
    @ArchTest
    static final ArchRule loggersArePrivateStaticFinal = fields().that()
            .haveRawType(Logger.class)
            .should()
            .bePrivate()
            .andShould()
            .beStatic()
            .andShould()
            .beFinal();

    /**
     * No package cycles between sub-packages. Catches design drift where a leaf
     * package starts importing from its parent or sibling.
     */
    @ArchTest
    static final ArchRule noPackageCycles = slices().matching("net.ladenthin.bitcoinaddressfinder.(*)..")
            .should()
            .beFreeOfCycles();

    /**
     * Production code must not import unsupported / internal JDK packages.
     * These are not part of the Java SE API and may change or disappear without notice.
     *
     * <p><b>Exception</b>: {@code ByteBufferUtility} deliberately uses
     * {@link jdk.internal.misc.Unsafe} for off-heap deallocation, gated by the
     * matching {@code --add-exports=java.base/jdk.internal.misc=ALL-UNNAMED}
     * line in {@code pom.xml}. If a second class ever needs internal JDK access,
     * widen the exception explicitly rather than relaxing the rule.
     */
    @ArchTest
    static final ArchRule noInternalJdkImports = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder..")
            .and()
            .doNotHaveSimpleName("ByteBufferUtility")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("sun..", "com.sun..", "jdk.internal..");

    /**
     * Public mutable state forbidden: any non-static field declared
     * {@code public} must also be {@code final}.
     *
     * <p>Two documented exceptions:
     * <ul>
     *   <li>The {@code net.ladenthin.bitcoinaddressfinder.configuration..}
     *       package — these are Maven-/Jackson-style config POJOs populated
     *       from JSON via reflection. Converting all of them to getter/setter
     *       form is a separate, larger refactor (see the design-fit review
     *       Open TODO).</li>
     *   <li>{@link ReadStatistic} — intentional shared mutable accumulator
     *       used across the address-file ingestion path. Refactoring to
     *       getters/setters would add noise without locking semantics; if a
     *       second mutable-counter class is ever added, prefer extending the
     *       exception list explicitly over relaxing the rule.</li>
     * </ul>
     */
    @ArchTest
    static final ArchRule noPublicMutableFields = fields().that()
            .arePublic()
            .and()
            .areNotStatic()
            .and()
            .areDeclaredInClassesThat()
            .resideOutsideOfPackage("net.ladenthin.bitcoinaddressfinder.configuration..")
            .and()
            .areDeclaredInClassesThat()
            .doNotHaveSimpleName("ReadStatistic")
            .should()
            .beFinal();

    /**
     * Production code must not call {@link System#exit(int)}; throw an exception instead so the
     * {@code Shutdown} hook gets to run and the cause is visible in stack traces.
     */
    @ArchTest
    static final ArchRule noSystemExit = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder..")
            .should()
            .callMethod(System.class, "exit", int.class)
            .allowEmptyShould(true);

    /**
     * Production code must not construct {@link java.util.Random}; {@code Random} is a non-cryptographic
     * PRNG (CWE-338). Use {@link java.security.SecureRandom} or {@link java.util.concurrent.ThreadLocalRandom}
     * depending on whether cryptographic strength or thread-local fast jitter is needed.
     *
     * <p>Two documented exceptions:
     * <ul>
     *   <li>{@code KeyProducerJavaRandom} deliberately constructs weak {@code Random} instances
     *       as documented CWE-338 demonstrations for vulnerability research (the project's
     *       raison d'être includes scanning addresses derived from weak-RNG wallets). See the
     *       {@code RANDOM_CURRENT_TIME_MILLIS_SEED} and {@code RANDOM_CUSTOM_SEED} branches.</li>
     *   <li>{@code BIP39KeyProducer extends java.util.Random} as a façade pattern (see Javadoc
     *       on the class); the implicit {@code super()} call hits the {@code Random()}
     *       constructor. The Random parent is used as a sequential-iterator surface for the
     *       deterministic HD-wallet derivation, not as a source of randomness.</li>
     * </ul>
     * If a third class ever needs {@code new Random()}, extend the exception list explicitly
     * rather than relaxing the rule.
     */
    @ArchTest
    static final ArchRule noNewRandom = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder..")
            .and()
            .doNotHaveSimpleName("KeyProducerJavaRandom")
            .and()
            .doNotHaveSimpleName("BIP39KeyProducer")
            .should()
            .callConstructor(Random.class)
            .orShould()
            .callConstructor(Random.class, long.class)
            .allowEmptyShould(true);

    // Note: deliberately NO `noThreadSleep` rule. The producer / consumer threading code
    // historically had five Thread.sleep call sites; three were refactored to higher-level
    // primitives (AbstractProducer → CountDownLatch, ConsumerJava → BlockingQueue.poll(timeout),
    // ProducerOpenCL → Semaphore — each removing a poll-based latency tax). The two remaining
    // sites are deliberate and documented:
    //   - AbstractKeyProducerQueueBuffered.sleep(int): the sleep primitive itself, used by
    //     KeyProducerJavaSocket for hard-capped bootstrap-retry back-off where exponential
    //     would add nothing (the loop gives up permanently after connectionRetryCount).
    //   - cli.Main.printAllStackTracesWithDelay: developer-debug helper behind an
    //     `if (false)` switch; sleep-then-sample-stacks is the textbook pattern.
    // Both are suppressed individually in spotbugs-exclude.xml under the MDM_THREAD_YIELD
    // section with full rationale.

    // ---------------------------------------------------------------------------------------
    // Layered-architecture invariants
    //
    // The root package's classes were split into dedicated layered packages (engine, command,
    // producer, consumer, io, model, util, core, statistics, secret) so the layer boundaries
    // align with packages. The full {@link com.tngtech.archunit.library.Architectures#layeredArchitecture()}
    // rule below now enforces the strict top-to-bottom dependency direction; the targeted leaf
    // rules that follow it remain as precise, fast-failing guards for the individual
    // foundation/entry-point invariants.
    // ---------------------------------------------------------------------------------------

    /**
     * Strict layered architecture. Each layer may only be accessed by the layers above it,
     * giving a top-to-bottom dependency direction with no upward edges:
     *
     * <pre>
     *   Entry          cli
     *   Orchestration  engine, command
     *   Pipeline       producer, consumer
     *   Capabilities   keyproducer, opencl, persistence
     *   InputOutput    io
     *   Foundation     model, util, core, statistics, secret
     *   Config         configuration
     *   Constants      constants
     * </pre>
     *
     * <p>Layers are coarser than packages on purpose: the legitimate
     * {@code producer -> Consumer} pipeline edge lives inside the {@code Pipeline} layer
     * rather than crossing a boundary. {@code consideringOnlyDependenciesInLayers()} keeps
     * the check focused on intra-project edges (external libraries are ignored).
     */
    @ArchTest
    static final ArchRule layeredArchitecture = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Entry")
            .definedBy("net.ladenthin.bitcoinaddressfinder.cli..")
            .layer("Orchestration")
            .definedBy("net.ladenthin.bitcoinaddressfinder.engine..", "net.ladenthin.bitcoinaddressfinder.command..")
            .layer("Pipeline")
            .definedBy("net.ladenthin.bitcoinaddressfinder.producer..", "net.ladenthin.bitcoinaddressfinder.consumer..")
            .layer("Capabilities")
            .definedBy(
                    "net.ladenthin.bitcoinaddressfinder.keyproducer..",
                    "net.ladenthin.bitcoinaddressfinder.opencl..",
                    "net.ladenthin.bitcoinaddressfinder.persistence..")
            .layer("InputOutput")
            .definedBy("net.ladenthin.bitcoinaddressfinder.io..")
            .layer("Foundation")
            .definedBy(
                    "net.ladenthin.bitcoinaddressfinder.model..",
                    "net.ladenthin.bitcoinaddressfinder.util..",
                    "net.ladenthin.bitcoinaddressfinder.core..",
                    "net.ladenthin.bitcoinaddressfinder.statistics..",
                    "net.ladenthin.bitcoinaddressfinder.secret..")
            .layer("Config")
            .definedBy("net.ladenthin.bitcoinaddressfinder.configuration..")
            .layer("Constants")
            .definedBy("net.ladenthin.bitcoinaddressfinder.constants..")
            // Access lists are tightened to the EXACT set of layers that actually reach each
            // layer today (verified by jdeps on the compiled classes), so any NEW unintended
            // cross-layer edge fails the build rather than slipping through a permissive bound.
            .whereLayer("Entry")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Orchestration")
            .mayOnlyBeAccessedByLayers("Entry")
            .whereLayer("Pipeline")
            // only the engine (Orchestration) wires producers/consumer; cli does not touch them
            .mayOnlyBeAccessedByLayers("Orchestration")
            .whereLayer("Capabilities")
            .mayOnlyBeAccessedByLayers("Entry", "Orchestration", "Pipeline")
            .whereLayer("InputOutput")
            // io is reached by command (Orchestration), producer (Pipeline) and persistence
            // (Capabilities) — never by cli (Entry) or the Foundation layer
            .mayOnlyBeAccessedByLayers("Orchestration", "Pipeline", "Capabilities")
            .whereLayer("Foundation")
            .mayOnlyBeAccessedByLayers("Entry", "Orchestration", "Pipeline", "Capabilities", "InputOutput")
            .whereLayer("Config")
            // configuration is read by every runtime layer but NOT by the Foundation layer
            .mayOnlyBeAccessedByLayers("Entry", "Orchestration", "Pipeline", "Capabilities", "InputOutput");

    /**
     * The {@code constants} sub-package is a true architectural leaf. Pure
     * project-wide invariants (currently the secp256k1 spec values in
     * {@link net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants}) live
     * there so every layer above ({@code configuration}, {@code keyproducer},
     * root orchestration, &hellip;) can reference them without inviting
     * back-and-forth cross-package dependencies.
     *
     * <p>This rule pins the "leaf" property as a test failure rather than a
     * code-review reminder. The package's own {@code package-info.java} carries
     * the rationale and lists the legitimate sibling clients.
     */
    @ArchTest
    static final ArchRule constantsPackageIsALeaf = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder.constants..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "net.ladenthin.bitcoinaddressfinder",
                    "net.ladenthin.bitcoinaddressfinder.cli..",
                    "net.ladenthin.bitcoinaddressfinder.configuration..",
                    "net.ladenthin.bitcoinaddressfinder.keyproducer..",
                    "net.ladenthin.bitcoinaddressfinder.opencl..",
                    "net.ladenthin.bitcoinaddressfinder.persistence..");

    /**
     * The {@code configuration} sub-package contains Jackson-populated POJOs. They
     * must not pull in runtime behaviour from any sibling layer &mdash; not the
     * root orchestration package, not {@code cli}, not {@code keyproducer},
     * not {@code opencl}, not {@code persistence}. The only
     * permitted internal dependencies are on the {@code constants} leaf, which
     * carries pure spec / wire-format values without code.
     *
     * <p>After this session's leaf extractions the rule is fully strict:
     * <ul>
     *   <li>Secp256k1 spec scalars live in {@link
     *       net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants}.</li>
     *   <li>The OpenCL chunk-layout block and the derived array-capacity bound
     *       ({@code MAXIMUM_CHUNK_ELEMENTS}, {@code BIT_COUNT_FOR_MAX_CHUNKS_ARRAY})
     *       live in {@link
     *       net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants}.</li>
     * </ul>
     * Any reintroduction of a {@code configuration → root} edge fails this test.
     */
    @ArchTest
    static final ArchRule configurationDoesNotDependOnRuntimeLayers = noClasses()
            .that()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder.configuration..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "net.ladenthin.bitcoinaddressfinder",
                    "net.ladenthin.bitcoinaddressfinder.cli..",
                    "net.ladenthin.bitcoinaddressfinder.keyproducer..",
                    "net.ladenthin.bitcoinaddressfinder.opencl..",
                    "net.ladenthin.bitcoinaddressfinder.persistence..");

    // eckeyIsLowLevelCrypto rule removed: the eckey package was deleted together with
    // its sole inhabitant (Secp256k1.java was a development-time reference oracle for
    // the OpenCL secp256k1 scalar-mul kernel, dead code with no production callers).
    // If a future low-level crypto helper needs the same guard, restore the rule
    // verbatim from history alongside the new package.

    /**
     * The {@code cli} sub-package is the program entry point ({@code Main} +
     * {@code FileType}). Nothing else inside the project may import from it: that
     * would invert the dependency direction (libraries reaching into the
     * entry-point's argument-parsing / file-loading code) and turn {@code Main}
     * into shared library code by accident.
     */
    @ArchTest
    static final ArchRule cliIsEntryPointOnly = noClasses()
            .that()
            .resideInAnyPackage(
                    "net.ladenthin.bitcoinaddressfinder",
                    "net.ladenthin.bitcoinaddressfinder.configuration..",
                    "net.ladenthin.bitcoinaddressfinder.constants..",
                    "net.ladenthin.bitcoinaddressfinder.keyproducer..",
                    "net.ladenthin.bitcoinaddressfinder.opencl..",
                    "net.ladenthin.bitcoinaddressfinder.persistence..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder.cli..");

    // ---------------------------------------------------------------------------------------
    // Per-module banned imports — confine heavy / specific third-party dependencies to the
    // single layer that owns them, so the rest of the codebase stays decoupled from them.
    // ---------------------------------------------------------------------------------------

    /**
     * The JOCL / OpenCL GPU binding ({@code org.jocl..}) may only be referenced from the
     * {@code opencl} package. No other layer may take a compile dependency on the GPU runtime;
     * everything above consumes the GPU exclusively through {@code opencl}'s own types.
     */
    @ArchTest
    static final ArchRule joclConfinedToOpencl = noClasses()
            .that()
            .resideOutsideOfPackage("net.ladenthin.bitcoinaddressfinder.opencl..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.jocl..")
            .allowEmptyShould(true);

    /**
     * The network key-input libraries — ZeroMQ ({@code org.zeromq..}) and
     * Java-WebSocket ({@code org.java_websocket..}) — may only be referenced from the
     * {@code keyproducer} package (the socket / websocket / zmq key producers live there).
     */
    @ArchTest
    static final ArchRule networkInputLibsConfinedToKeyproducer = noClasses()
            .that()
            .resideOutsideOfPackage("net.ladenthin.bitcoinaddressfinder.keyproducer..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.zeromq..", "org.java_websocket..")
            .allowEmptyShould(true);

    /**
     * The LMDB binding ({@code org.lmdbjava..}) may only be referenced from {@code persistence}
     * (the database backend) and {@code io} (which catches {@code LmdbException} during address
     * file ingestion). Runtime/orchestration layers consume LMDB only through the
     * {@code persistence} abstraction, never the driver directly.
     */
    @ArchTest
    static final ArchRule lmdbConfinedToPersistenceAndIo = noClasses()
            .that()
            .resideOutsideOfPackages(
                    "net.ladenthin.bitcoinaddressfinder.persistence..", "net.ladenthin.bitcoinaddressfinder.io..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.lmdbjava..")
            .allowEmptyShould(true);
}
