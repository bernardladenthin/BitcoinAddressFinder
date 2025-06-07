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

public enum StaticUnsupportedAddress implements PublicAddress {
    
    // Scripts with 32 bytes
    /**
     * bitcoin Bech32 (P2WSH) with 32 bytes
     * https://privatekeys.pw/bitcoin/address/bc1qp762gmkychywl4elnuyuwph68hqw0uc2jkzu3ax48zfjkskslpsq8p66gf
     * The hash of this script has 32 bytes: 0fb4a46ec4c5c8efd73f9f09c706fa3dc0e7f30a9585c8f4d538932b42d0f860
     */
    Bitcoin("bc1qp762gmkychywl4elnuyuwph68hqw0uc2jkzu3ax48zfjkskslpsq8p66gf"),
    
    // Bitcoin Bech32 (P2WSH)
    BitcoinP2SH("bc1pmfr3p9j00pfxjh0zmgp99y8zftmd3s5pmedqhyptwy6lm87hf5ss52r5n8"),
    
    // Bitcoin Bech32 (P2WSH)
    // Created with random data via https://learnmeabitcoin.com/technical/script/p2wsh/
    // Script Hash: d698321d7b9f5883fa1bf99b9cb87a9cac9bd462affb300a844998a28d67584d
    BitcoinP2SH_Random("bc1q66vry8tmnavg87smlxdeewr6njkfh4rz4lanqz5yfxv29rt8tpxs8z4cmm"),
    
    // Bitcoin Bech32 (P2TR)
    // Created with random data via https://learnmeabitcoin.com/technical/script/p2wsh/
    // tweaked public key: 52a5d35869fedad2a5ce56b39d78500124dfb77b612f9931d4d8f7a1398b0d18
    BitcoinP2TRRandom("bc1p22jaxkrflmdd9fww26ee67zsqyjdldmmvyhejvw5mrm6zwvtp5vq9y5p0g"),

    // Bitcoin Cash Bech32 (P2WSH)
    /**
     * https://privatekeys.pw/bitcoin-cash/address/prseh0a4aejjcewhc665wjqhppgwrz2lw5txgn666a
     */
    BitcoinCashP2SH("prseh0a4aejjcewhc665wjqhppgwrz2lw5txgn666a"),

    // P2MS (Pay to Multisig)
    /**
     * 
     */
    BitcoinP2MS("d-414f1537e051163a5a558d5e9ee37439"),
    /**
     * https://privatekeys.pw/address/bitcoin-cash/m-6d14a66d55f88b28b41132f32d1f3059
     */
    BitcoinCashP2MS("m-6d14a66d55f88b28b41132f32d1f3059"),
    /**
     * 
     */
    BitcoinCashP2MS_2("s-8f1a5a77bd8e884c66044ec2081a05b0"),
    
    // Doichain Bech32 (P2WSH)
    /**
     * https://btc.cryptoid.info/doi/address.dws?2378564.htm
     */
    DoichainP2WSH("dc1q7pmm98r4shmvtjwy6u5ulj4tqemq00fkjgrc2a9alj5z5keaf6ysn5t2m0"),
    
    // litecoin Bech32 (P2WSH)
    /**
     * https://bitcoin.stackexchange.com/questions/110995/how-can-i-find-samples-for-p2tr-transactions-on-mainnet
     */
    Litecoin("ltc1qd5wm03t5kcdupjuyq5jffpuacnaqahvfsdu8smf8z0u0pqdqpatqsdrn8h"),
    
    // Riecoin Bech32 (P2SH)
    /**
     * https://chainz.cryptoid.info/ric/address.dws?2130005.htm
     */
    Riecoin("ric1pmm582sczt9zw8zn7j5eflqlle3p9ap75wwcpjewpdp34wscg0kjsmp5h35"),
    
    // P2TR
    /**
     * P2TR (Pay-to-Taproot) with 32 bytes
     * https://bitcoin.stackexchange.com/questions/110995/how-can-i-find-samples-for-p2tr-transactions-on-mainnet
     * The hash of this script has 32 bytes: a37c3903c8d0db6512e2b40b0dffa05e5a3ab73603ce8c9c4b7771e5412328f9
     */
    BitcoinP2TR("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297");
    
    private final String publicAddress;

    StaticUnsupportedAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    @Override
    public String getPublicAddress() {
        return publicAddress;
    }
}
