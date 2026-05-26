// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "net.ladenthin.bitcoinaddressfinder", importOptions = ImportOption.DoNotIncludeTests.class)
public class BitcoinAddressFinderArchitectureTest {

    /**
     * The CLI entry point is the top-level consumer; no other package should import it.
     */
    @ArchTest
    static final ArchRule cliIsLeaf = noClasses()
            .that().resideOutsideOfPackage("net.ladenthin.bitcoinaddressfinder.cli..")
            .should().dependOnClassesThat()
            .resideInAPackage("net.ladenthin.bitcoinaddressfinder.cli..");

    /**
     * Test-framework classes must not appear in production code.
     */
    @ArchTest
    static final ArchRule noTestFrameworksInProduction = noClasses()
            .that().resideInAPackage("net.ladenthin.bitcoinaddressfinder..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.junit..", "net.jqwik..", "com.tngtech.archunit..");
}
