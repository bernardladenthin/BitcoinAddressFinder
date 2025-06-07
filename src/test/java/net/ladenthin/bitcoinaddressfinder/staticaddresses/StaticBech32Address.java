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
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import org.bouncycastle.util.encoders.Hex;

// P2WPKH addresses
public enum StaticBech32Address implements PublicAddress {
    
    // Bitcoin Bech32 (P2WPKH)
    // Created with random data via https://learnmeabitcoin.com/technical/script/p2wsh/
    // Public Key Hash: 458cda0e1178d8dd97598df1307ea448bc34c76e
    BitcoinP2WPKH_Random("bc1qgkxd5rs30rvdm96e3hcnql4yfz7rf3mwdgcxkc", "458cda0e1178d8dd97598df1307ea448bc34c76e"),
    
    // Bitcoin Oil Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/btco/address.dws?3851.htm
     */
    BitcoinOil("btco1q8xgl5s6sknp0kx3a7axk63p8xk797rnwp4fx7z", "3991fa4350b4c2fb1a3df74d6d442735bc5f0e6e"),
    
    // BitCore Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/btx/address.dws?7481872.htm
     */
    BitCore("btx1q7tn0z9mln8dq7c73dkrfqpx4wkutqh37sdprwv", "f2e6f1177f99da0f63d16d869004d575b8b05e3e"),
    
    // CanadaECoin Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/cdn/address.dws?cdn1qf7kyhfue3fjn4y09ec6cqqsxq56vh7q3z9ea8e.htm
     */
    CanadaECoin("cdn1qf7kyhfue3fjn4y09ec6cqqsxq56vh7q3z9ea8e", "4fac4ba7998a653a91e5ce358002060534cbf811"),
    
    // DeFiChain Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/dfi/address.dws?df1qvkmmsj6z602rymrx7fn82gqh68dp3v2f068gsu.htm
     */
    DeFiChain("df1qvkmmsj6z602rymrx7fn82gqh68dp3v2f068gsu", "65b7b84b42d3d4326c66f266752017d1da18b149"),
    
    // Digibyte Bech32 (P2WPKH)
    /**
     * https://dgb.tokenview.io/en/address/dgb1qnjf7e2a5ezft480kxzmhgg66pnzqk0aawxa06u
     */
    Digibyte("dgb1qnjf7e2a5ezft480kxzmhgg66pnzqk0aawxa06u", "9c93ecabb4c892ba9df630b774235a0cc40b3fbd"),
    
    // Doichain Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/doi/address.dws?2476957.htm
     */
    Doichain("dc1qzlspyu8t5ax9rspzd7f2ftn4awusl095c3udhg", "17e01270eba74c51c0226f92a4ae75ebb90fbcb4"),
        
    // feathercoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/ftc/address.dws?fc1qvr9zesajsdw8aydcndd70wxj2wdgzu6zzltsph.htm
     */
    Feathercoin("fc1qvr9zesajsdw8aydcndd70wxj2wdgzu6zzltsph", "60ca2cc3b2835c7e91b89b5be7b8d2539a817342"),
    
    // Groestlcoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/grs/address.dws?1455068.htm
     */
    Groestlcoin("grs1qpuf9cmfysp6dgdxmmar9eap8qrf5p2hl6a52pj", "0f125c6d248074d434dbdf465cf42700d340aaff"),
    
    // Groestlcoin Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/grs-test/address.dws?673.htm
     */
    GroestlcoinTestNet("tgrs1qw4z3xrtgx4f6w7akwpp2xa0gupmkv4yauemmm9", "7545130d683553a77bb67042a375e8e07766549d"),

    // litecoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/ltc/address.dws?187430165.htm
     */
    Litecoin("ltc1qr07zu594qf63xm7l7x6pu3a2v39m2z6hh5pp4t", "1bfc2e50b50275136fdff1b41e47aa644bb50b57"),

    // litecoin cash Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/lcc/address.dws?lcc1qrzlsxpjl0tynu3t2fkrw2ff2dgm0pv53ern0s5.htm
     */
    LitecoinCash("lcc1qrzlsxpjl0tynu3t2fkrw2ff2dgm0pv53ern0s5", "18bf03065f7ac93e456a4d86e5252a6a36f0b291"),

    // Mooncoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/moon/address.dws?moon1q2rhkqa03lq2hza99ezpfsauvgqmgqz5xjlawjq.htm
     */
    Mooncoin("moon1q2rhkqa03lq2hza99ezpfsauvgqmgqz5xjlawjq", "50ef6075f1f8157174a5c88298778c4036800a86"),

    // Myriad Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/xmy/address.dws?4567225.htm
     */
    Myriad("my1qgsy2zfpk63xst000zqs6npyjzrn7udvcnlejcc", "4408a12436d44d05bdef1021a9849210e7ee3598"),

    // namecoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/nmc/address.dws?nc1q2ml905jv7gx0d8z5f7kl23af0vtrjk4j0llmwr.htm
     */
    Namecoin("nc1q2ml905jv7gx0d8z5f7kl23af0vtrjk4j0llmwr", "56fe57d24cf20cf69c544fadf547a97b16395ab2"),
    
    // Riecoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/ric/address.dws?2133357.htm
     */
    Riecoin("ric1qhklsed3veursw2m0gjt8xftkhm78uhmyk38exe", "bdbf0cb62ccf07072b6f4496732576befc7e5f64"),
    
    // SpaceXpanse Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/rod/address.dws?652202.htm
     */
    SpaceXpanse("rod1qkw670qzp4gg4gkg9g25jvt7z357c7ykwesha3v", "b3b5e78041aa1154590542a9262fc28d3d8f12ce"),
    
    // syscoin Bech32 (P2WPKH)
    /**
     * https://chainz.cryptoid.info/sys/address.dws?1435968.htm
     */
    Syscoin("sys1qync7erear7cvpkysvv0a28mj45g2ps0kq9c6qs", "24f1ec8f3d1fb0c0d890631fd51f72ad10a0c1f6"),
    
    // TheHolyRogerCoin Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/roger/address.dws?139264.htm
     */
    TheHolyRogerCoin("rog1q4vs95czpvgffxtsdz0l858gkkda6nmjkzly8qy", "ab205a60416212932e0d13fe7a1d16b37ba9ee56"),

    // UFO Bech32 (P2WPKH)
    /**
     * https://btc.cryptoid.info/ufo/address.dws?570258.htm
     */
    UFO("uf1qpp3rflelg3h79cnr9xcszr0htyk6n2hkusghed", "086234ff3f446fe2e26329b1010df7592da9aaf6"),

    // vertcoin Bech32 (P2WSH or P2WPKH)
    /**
     * https://chainz.cryptoid.info/vtc/address.dws?vtc1qa4wejdlw9lmc7ks7l8hplc9fm394u79qjj0792.htm
     */
    Vertcoin("vtc1qa4wejdlw9lmc7ks7l8hplc9fm394u79qjj0792", "ed5d9937ee2ff78f5a1ef9ee1fe0a9dc4b5e78a0");
    
    // See SegwitAddress#WITNESS_PROGRAM_LENGTH_PKH
    
    private final String publicAddress;
    private final String witnessProgramAsHex;
    
    StaticBech32Address(String publicAddress, String witnessProgramAsHex) {
        this.publicAddress = publicAddress;
        this.witnessProgramAsHex = witnessProgramAsHex;
    }
    
    @Override
    public String getPublicAddress() {
        return publicAddress;
    }
    
    public String getWitnessProgramAsHex() {
        return witnessProgramAsHex;
    }

    public byte[] getWitnessProgram() {
        return Hex.decode(getWitnessProgramAsHex());
    }
    
    public ByteBuffer getWitnessProgramAsByteBuffer() {
        return new ByteBufferUtility(true).getByteBufferFromHex(getWitnessProgramAsHex());
    }
}
