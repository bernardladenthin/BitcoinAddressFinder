// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CConsumerJava {
    public @NonNull CLMDBConfigurationReadOnly lmdbConfigurationReadOnly = new CLMDBConfigurationReadOnly();
    public int printStatisticsEveryNSeconds = 60;
    public int threads = 4;
    /**
     * in ms.
     */
    public long delayEmptyConsumer = 100;
    public int queueSize = 10;
    
    /**
     * Enables runtime verification of public key calculation.
     * 
     * This performs a full consistency check for each generated key by comparing it
     * against a known-correct Java-based calculation. Use this option only in very specific
     * debugging or validation scenarios, such as:
     * 
     * <ul>
     *   <li>Suspected errors in the OpenCL implementation</li>
     *   <li>Hardware defects causing incorrect calculations</li>
     *   <li>Regression testing during development</li>
     * </ul>
     * 
     * <b>Warning:</b> This has a significant performance impact.
     * <ul>
     *   <li>Enabled: ~20k keys/second</li>
     *   <li>Disabled (default): ~10 million keys/second (LMDB benchmark)</li>
     * </ul>
     * 
     * <p>Do not enable in production or performance benchmarking environments.</p>
     */
    public boolean runtimePublicKeyCalculationCheck;
    
    public boolean enableVanity = false;
    
    public @Nullable String vanityPattern;
}
