// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.configuration;

public enum CSecretFormat {
    
    /**
     * Represents a big integer.
     * e.g.:
     * <pre>
     * 72155939486846849509759369733266486982821795810448245423168957390607644363272
     * 39929263256442288830290225612580366403172818928633701045115663441379782969864
     * 42379586058257162021782620237913525000692985364990081801945649219990416465578
     * </pre>
     */
    BIG_INTEGER,
    
    /**
     * Also referred to as WiF (Wallet Import Format).
     * e.g.:
     * <pre>
     * 5K2YUVmWfxbmvsNxCsfvArXdGXm7d5DC9pn4yD75k2UaSYgkXTh
     * 5JVAXLpkZ21svEzwyimMHn5hAkWNqJq8uGxqUqcRrNb8F4Csp8V
     * 5JXYuGrwSbyp8sKBmiLcvokqSnxALPjKWQMPJXZYyBWKof7c2pk
     * </pre>
     */
    DUMPED_RIVATE_KEY,
    
    /**
     * A HEX-encoded SHA256 string. This might already be the hashed brainwallet.
     * e.g.:
     * <pre>
     * 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
     * 58472980a1d3449939eadc2652370972d5007fa9c059ce84fb3ab98f544e4a08
     * 5db1fee4b5703808c48078a76768b155b421b210c0761cd6a5d223f4d99f1eaa
     * </pre>
     */
    SHA256,
    
    /**
     * Also known as a brainwallet. A string is hashed using SHA256 and used as the private key.
     * e.g.: 
     * <pre>
     * test
     * test with space
     * 1337
     * </pre>
     */
    STRING_DO_SHA256
}
