// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0

/**
 * JPMS module descriptor for the BitcoinAddressFinder library and CLI tool.
 *
 * <p>The configuration POJOs in {@code net.ladenthin.bitcoinaddressfinder.configuration}
 * are populated by Jackson via reflection. All current fields are public so a plain
 * {@code exports} would also let Jackson read them, but {@code opens ... to
 * com.fasterxml.jackson.databind;} is forward-compatible should any non-public members
 * be introduced later; the qualified form restricts the open to Jackson Databind and
 * does not widen reflective access to arbitrary modules.</p>
 *
 * <p>Resource lookups in this module ({@code BIP39Wordlist.class.getResourceAsStream(...)}
 * for the BIP39 wordlists, {@code Resources.getResource(...)} for OpenCL kernels) all
 * load resources that live in the same module, so no additional {@code opens} directives
 * are required for resource access.</p>
 *
 * <p>No non-implicit {@code requires} clauses are declared. The dependency graph
 * (bitcoinj, LMDB, JOCL, Jackson, SLF4J, Guava, etc.) is referenced from ordinary source
 * files only; consumers that put this jar on the module path resolve those dependencies
 * through their own {@code requires} graph. The fat-jar produced by {@code mvn package
 * -P assembly} runs on the classpath, where the module descriptor is ignored.</p>
 */
module net.ladenthin.bitcoinaddressfinder {
    // Lombok is `provided` scope: only used at compile time to generate equals/hashCode/toString.
    // `requires static` means the runtime does not need the lombok jar on the module path —
    // the @lombok.Generated annotation carried on generated members has CLASS retention, so it
    // is present in the .class file but not consulted at runtime by JVM linking.
    requires static lombok;

    opens net.ladenthin.bitcoinaddressfinder.configuration to com.fasterxml.jackson.databind;

    exports net.ladenthin.bitcoinaddressfinder;
    exports net.ladenthin.bitcoinaddressfinder.cli;
    exports net.ladenthin.bitcoinaddressfinder.configuration;
    exports net.ladenthin.bitcoinaddressfinder.constants;
    exports net.ladenthin.bitcoinaddressfinder.keyproducer;
    exports net.ladenthin.bitcoinaddressfinder.opencl;
    exports net.ladenthin.bitcoinaddressfinder.persistence;
    exports net.ladenthin.bitcoinaddressfinder.persistence.bloom;
    exports net.ladenthin.bitcoinaddressfinder.persistence.inmemory;
    exports net.ladenthin.bitcoinaddressfinder.persistence.lmdb;
}
