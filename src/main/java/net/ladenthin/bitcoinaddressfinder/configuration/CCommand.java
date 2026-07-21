// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

/**
 * Top-level operation mode of the CLI tool.
 */
public enum CCommand {
    /** Scan candidate private keys and check the LMDB database. */
    Find,
    /** Export the LMDB database to one or more address files. */
    LMDBToAddressFile,
    /** Import one or more address files into the LMDB database. */
    AddressFilesToLMDB,
    /** Print information about the available OpenCL platforms and devices. */
    OpenCLInfo,
    /**
     * Measure end-to-end pipeline throughput on this machine, sweep the tunable producer
     * parameters, and print the winning configuration as a ready-to-paste JSON fragment.
     *
     * <p>Distinct from the JMH benchmarks under {@code src/test}: those measure isolated
     * components (filter probe latency, kernel time, build cost) and compose a prediction
     * arithmetically. This command measures the composed pipeline itself, on the operator's own
     * hardware, so the recommendation does not rest on numbers taken from somebody else's GPU.
     */
    TuneConfiguration,
    /**
     * Write a compacted copy of an existing LMDB database (LMDB {@code MDB_CP_COMPACT}): free/dead pages
     * are omitted and pages are laid out sequentially, producing a smaller, read-denser database. Useful
     * as a post-import optimize step, especially when the database exceeds RAM.
     */
    CompactLMDB
}
