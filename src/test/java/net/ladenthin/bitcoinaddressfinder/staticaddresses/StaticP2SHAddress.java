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

import java.nio.ByteBuffer;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.bouncycastle.util.encoders.Hex;

public enum StaticP2SHAddress implements PublicAddress {
    
    /**
     * https://chainz.cryptoid.info/42/address.dws?5973.htm
     */
    _42Coin("9QEnUA3JnXaFXCAjm6fC2naraT6otg6jmV", "e0a09e80866feacac2567198f312b1bce774b2e4"),
    /**
     * https://bitcoin.stackexchange.com/questions/62781/litecoin-constants-and-prefixes
     * https://privatekeys.pw/litecoin/address/M8T1B2Z97gVdvmfkQcAtYbEepune1tzGua
     * https://privatekeys.pw/bitcoin/address/3MSvaVbVFFLML86rt5eqgA9SvW23upaXdY
     */
    BitcoinAndLitecoinDeprecated("3MSvaVbVFFLML86rt5eqgA9SvW23upaXdY", "d8b83ad7bf8795b9ff61464fcf06f156c28e3e1f"),
    
    // bitcoin Bech32 (P2WSH) (Pay to Witness Script Hash) with 20 bytes
    /**
     * https://privatekeys.pw/address/bitcoin/bc1qazcm763858nkj2dj986etajv6wquslv8uxwczt
     */
    BitcoinP2WSH_20_BYTES("bc1qazcm763858nkj2dj986etajv6wquslv8uxwczt", "e8b1bf6a27a1e76929b229f595f64cd381c87d87"),

    /**
     * https://chainz.cryptoid.info/bytz/address.dws?1494.htm
     */
    BYTZ("8VS5PNwJ8URbpUdbsQAPwazu22TFNg9PiN", "9d64a67ad444c2a00373cca73c85fd5f731f1c62"),
    /**
     * https://chainz.cryptoid.info/cloak/address.dws?299452.htm
     */
    CloakCoin("BsXZiNs4wZurSwHBbLTZywhcqD4JKRX5oM", "046707c85e3de21f4f7701f724e0e952c6a5ddad"),
    /**
     * https://privatekeys.pw/dash/address/7dZe6LvtGPd2TLPpARzThqdF4YwAZvAKDv
     */
    Dash("7dZe6LvtGPd2TLPpARzThqdF4YwAZvAKDv", "7a5c931f83bb3a356c2dcf72b24e1bea461df587"),
    /**
     * https://chainz.cryptoid.info/dfi/address.dws?2770030.htm
     */
    DeFiChain("8W8yVQDEaQiJ5PB4Hy8CvKj9pBtD3zqkhb", "a520c86a08366941cd90d22e11ac1c7eefa2db37"),
    /**
     * https://privatekeys.pw/dogecoin/address/A8c3xNz2mqsDLFwv5KL5fpH12QEwDaoTXo
     */
    Dogecoin("A8c3xNz2mqsDLFwv5KL5fpH12QEwDaoTXo", "b15b9098e14dc3c48b4f0a6ac548c66771d30f54"),
    /**
     * https://privatekeys.pw/dogecoin/address/9tp7THhBG3fNNFka1AJm95DU3E4x3pwQA3
     */
    DogecoinMultisig("9tp7THhBG3fNNFka1AJm95DU3E4x3pwQA3", "1a11b73ad3dd99d77da9f858cf323205a84dd5da"),
    /**
     * https://privatekeys.pw/litecoin/address/M8T1B2Z97gVdvmfkQcAtYbEepune1tzGua
     */
    Litecoin("M8T1B2Z97gVdvmfkQcAtYbEepune1tzGua", "0605bfbd71b78f7e9fc815fe9cc90aaeb1d9a728"),
    /**
     * https://chainz.cryptoid.info/moon/address.dws?846906.htm
     */
    Mooncoin("2QovBjnVke4fgn9UXdz9osheNLxQCk3d8R", "8161b82d4543b119cd8317ff4d33f278fc4feac7"),
    /**
     * https://chainz.cryptoid.info/xmy/address.dws?4587668.htm
     */
    Myriad("MAZExxxhYSjr6zmrxd3SRu4sChjYbWW7Ef", "1d240263b4555a8fa09d7e1780ea526e22535fd5"),
    /**
     * https://chainz.cryptoid.info/part/address.dws?167783.htm
     */
    Particl_2("2vSfGkmkaqG9XQusB4nWbk2sJXE6ZnGoyT5X3TroxaJfAVeonry", "5487c9b5a7db93e200ca72a2fd32c4d322e70bdc"),
    /**
     * https://chainz.cryptoid.info/part/address.dws?166420.htm
     */
    Particl_3("34GQxGYXTZeGQSj8Q1typubQaKYQhEGT7X3QV6HDsjwE93kuDBN", "5ad51842f557faa595eee94a521c790e94a7beaf"),
    /**
     * https://chainz.cryptoid.info/ric/address.dws?1357243.htm
     */
    Riecoin("76a914a17ee99993b6fdd0c717a052c35cd654f17e338088ac", "a17ee99993b6fdd0c717a052c35cd654f17e3380"),
    /**
     * https://chainz.cryptoid.info/sls/address.dws?3825.htm
     */
    SaluS("AeZbYSeAK6gWWz7aX1eiPPWYFShfi16cih", "f9f8754cfcc9e49ff024a37cd81b8ec99a24164b"),
    /**
     * https://chainz.cryptoid.info/ufo/address.dws?312088.htm
     */
    UFO("C4viFjPP5YQwPT61ZdLXowJDwJJYiXj4nk", "81711c475bc15419e47397817e8bf7cb3e5a145a"),
    /**
     * https://privatekeys.pw/zcash/address/t3JcMe1E5UkFsUtVb7k17eJwXX5FYUewMBy
     */
    Zcash("t3JcMe1E5UkFsUtVb7k17eJwXX5FYUewMBy", "00847bd140242bd1c9c19024bfe00a4acefbbb85");

    private final String publicAddress;
    private final String scriptHash;

    StaticP2SHAddress(String publicAddress, String scriptHash) {
        this.publicAddress = publicAddress;
        this.scriptHash = scriptHash;
    }

    @Override
    public String getPublicAddress() {
        return publicAddress;
    }

    public String getScriptHashAsHex() {
        return scriptHash;
    }

    public byte[] getScriptHash() {
        return Hex.decode(getScriptHashAsHex());
    }

    public ByteBuffer getScriptHashAsByteBuffer() {
        return new ByteBufferUtility(true).getByteBufferFromHex(getScriptHashAsHex());
    }

    @Deprecated
    private String getScriptHashAsBase58() {
        final Network network = new NetworkParameterFactory().getNetwork();
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
        return keyUtility.toBase58(getScriptHash());
    }
}
