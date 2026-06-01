// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
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
    static final ArchRule loggersArePrivateStaticFinal = fields()
            .that()
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
    static final ArchRule noPackageCycles = slices()
            .matching("net.ladenthin.bitcoinaddressfinder.(*)..")
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
    static final ArchRule noPublicMutableFields = fields()
            .that()
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
}
