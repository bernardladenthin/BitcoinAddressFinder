// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
package net.ladenthin.bitcoinaddressfinder.configuration;

public enum CAddressFileOutputFormat {
    /**
     * The hash160 will be written encoded in hex without the amount. Optimal to view with a viewer with a fixed width (e.g. HxD).
     */
    HexHash,
    /**
     * The addresses will be written with a fixed width and without the amount. Optimal to view with a viewer with a fixed width (e.g. HxD).
     */
    FixedWidthBase58BitcoinAddress,
    /**
     * The addresses will be written with amount. Separated with a {@link net.ladenthin.bitcoinaddressfinder.AddressTxtLine#COMMA}.
     */
    DynamicWidthBase58BitcoinAddressWithAmount
}
