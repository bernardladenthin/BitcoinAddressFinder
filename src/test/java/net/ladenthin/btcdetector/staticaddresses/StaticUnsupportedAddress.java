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
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

public enum StaticUnsupportedAddress {

    // P2SH (Pay to Script Hash)
    /**
     * https://privatekeys.pw/bitcoin-cash/address/prseh0a4aejjcewhc665wjqhppgwrz2lw5txgn666a
     */
    BitcoinCashP2SH("prseh0a4aejjcewhc665wjqhppgwrz2lw5txgn666a"),

    // P2MS (Pay to Multisig)
    /**
     * https://privatekeys.pw/bitcoin-cash/address/m-6d14a66d55f88b28b41132f32d1f3059
     */
    BitcoinCashP2MS("m-6d14a66d55f88b28b41132f32d1f3059"),
    /**
     * https://privatekeys.pw/bitcoin-cash/address/s-8f1a5a77bd8e884c66044ec2081a05b0
     */
    BitcoinCashP2MS_2("s-8f1a5a77bd8e884c66044ec2081a05b0"),
    /**
     * https://privatekeys.pw/bitcoin-cash/address/d-414f1537e051163a5a558d5e9ee37439
     */
    BitcoinP2MS("d-414f1537e051163a5a558d5e9ee37439"),
    
    // P2SH (Pay to Witness Script Hash) 
    /**
     * https://privatekeys.pw/bitcoin/address/bc1qp762gmkychywl4elnuyuwph68hqw0uc2jkzu3ax48zfjkskslpsq8p66gf
     */
    BitcoinP2WSH("bc1qp762gmkychywl4elnuyuwph68hqw0uc2jkzu3ax48zfjkskslpsq8p66gf");
    
    private final String publicAddress;

    StaticUnsupportedAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    public String getPublicAddress() {
        return publicAddress;
    }
}
