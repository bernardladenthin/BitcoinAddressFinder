// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

/**
 * Enum representing different Bitcoin/altcoin address script types.
 * Constants for witness program lengths are defined in {@link org.bitcoinj.base.SegwitAddress}.
 */
public enum AddressType {

    /**
     * Pay to Public Key Hash (Legacy Address) or
     * Pay to Script Hash.
     * <p>
     * P2PKH Format: Base58Check. Typical prefix: '1' (BTC mainnet).<br>
     * P2SH Format: Base58Check. Typical prefix: '3' (BTC mainnet).<br>
     * P2PKH Script: OP_DUP OP_HASH160 &lt;pubKeyHash&gt; OP_EQUALVERIFY OP_CHECKSIG<br>
     * P2SH Script: OP_HASH160 &lt;scriptHash&gt; OP_EQUAL
     * </p>
     * <p><b>Note:</b> P2PKH and P2SH addresses use the same Base58Check format and differ only by their version byte. 
     * This makes it impossible to distinguish them without additional context (e.g., the coin type or expected script).
     * Address collisions or misclassification may occur when coins use the same address format with different version bytes.
     * </p>
     */
    P2PKH_OR_P2SH,

    /**
     * Pay to Witness Public Key Hash (SegWit v0).
     * Format: Bech32. Typical prefix: 'bc1q'.
     * Length: {@link org.bitcoinj.base.SegwitAddress#WITNESS_PROGRAM_LENGTH_PKH}
     * Script: 0 &lt;20-byte pubKeyHash&gt;
     */
    P2WPKH
}
