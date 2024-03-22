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

    // P2SH (Pay to Script Hash)
    /**
     * https://privatekeys.pw/bitcoin-cash/address/prseh0a4aejjcewhc665wjqhppgwrz2lw5txgn666a
     */
    BitcoinCashP2SH("prseh0a4aejjcewhc665wjqhppgwrz2lw5txgn666a"),
    /**
     * https://dgb.tokenview.io/en/address/dgb1qnjf7e2a5ezft480kxzmhgg66pnzqk0aawxa06u
     */
    DigibyteP2SH("dgb1qnjf7e2a5ezft480kxzmhgg66pnzqk0aawxa06u"),

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
    
    // Scripts with 32 bytes
    /**
     * bitcoin Bech32 (P2WSH) (Pay to Witness Script Hash) with 32 bytes
     * https://privatekeys.pw/bitcoin/address/bc1qp762gmkychywl4elnuyuwph68hqw0uc2jkzu3ax48zfjkskslpsq8p66gf
     * The hash of this script has 32 bytes: 0fb4a46ec4c5c8efd73f9f09c706fa3dc0e7f30a9585c8f4d538932b42d0f860
     */
    BitcoinP2WSH_32_BYTES("bc1qp762gmkychywl4elnuyuwph68hqw0uc2jkzu3ax48zfjkskslpsq8p66gf"),
    // P2TR
    /**
     * P2TR (Pay-to-Taproot) with 32 bytes
     * https://bitcoin.stackexchange.com/questions/110995/how-can-i-find-samples-for-p2tr-transactions-on-mainnet
     * The hash of this script has 32 bytes: a37c3903c8d0db6512e2b40b0dffa05e5a3ab73603ce8c9c4b7771e5412328f9
     */
    BitcoinP2TR("bc1p5d7rjq7g6rdk2yhzks9smlaqtedr4dekq08ge8ztwac72sfr9rusxg3297"),

    // feathercoin Bech32 (P2WSH or P2WPKH)
    /**
     * https://chainz.cryptoid.info/ftc/address.dws?fc1qvr9zesajsdw8aydcndd70wxj2wdgzu6zzltsph.htm
     */
    FeathercoinP2WSH("fc1qvr9zesajsdw8aydcndd70wxj2wdgzu6zzltsph"),

    // litecoin Bech32 (P2WSH or P2WPKH)
    /**
     * https://bitcoin.stackexchange.com/questions/110995/how-can-i-find-samples-for-p2tr-transactions-on-mainnet
     */
    LitecoinP2WSH("ltc1qd5wm03t5kcdupjuyq5jffpuacnaqahvfsdu8smf8z0u0pqdqpatqsdrn8h"),

    // litecoin Bech32 (P2WSH or P2WPKH)
    /**
     * https://chainz.cryptoid.info/lcc/address.dws?lcc1qrzlsxpjl0tynu3t2fkrw2ff2dgm0pv53ern0s5.htm
     */
    LitecoinCashP2WSH("lcc1qrzlsxpjl0tynu3t2fkrw2ff2dgm0pv53ern0s5"),

    // namecoin Bech32 (P2WSH or P2WPKH)
    /**
     * https://chainz.cryptoid.info/nmc/address.dws?nc1q2ml905jv7gx0d8z5f7kl23af0vtrjk4j0llmwr.htm
     */
    NamecoinP2WSH("nc1q2ml905jv7gx0d8z5f7kl23af0vtrjk4j0llmwr"),

    // vertcoin Bech32 (P2WSH or P2WPKH)
    /**
     * https://chainz.cryptoid.info/vtc/address.dws?vtc1qa4wejdlw9lmc7ks7l8hplc9fm394u79qjj0792.htm
     */
    VertcoinP2WSH("vtc1qa4wejdlw9lmc7ks7l8hplc9fm394u79qjj0792");
    
    private final String publicAddress;

    StaticUnsupportedAddress(String publicAddress) {
        this.publicAddress = publicAddress;
    }

    @Override
    public String getPublicAddress() {
        return publicAddress;
    }
}
