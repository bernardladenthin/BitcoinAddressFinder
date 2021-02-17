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
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bouncycastle.util.encoders.Hex;

public enum StaticP2PKHAddress {
    
    /**
     * https://privatekeys.pw/bitcoin/address/1JRW4d8vHZseMEtYbgJ7MwPG1TasHUUVNq
     */
    Bitcoin("1JRW4d8vHZseMEtYbgJ7MwPG1TasHUUVNq", "bf1c61ac19576d71d4623b185f3bae2a3d4df6bc"),
    /**
     * https://privatekeys.pw/bitcoin-cash/address/qz7xc0vl85nck65ffrsx5wvewjznp9lflgktxc5878
     */
    BitcoinCash("qz7xc0vl85nck65ffrsx5wvewjznp9lflgktxc5878", "bc6c3d9f3d278b6a8948e06a399974853097e9fa"),
    /**
     * https://bitinfocharts.com/de/bitcoin%20gold/address/GUGsnYNyGYDe4GT2iQKLDjKFPpd4KBXMQB
     */
    BitcoinGold("GUGsnYNyGYDe4GT2iQKLDjKFPpd4KBXMQB", "726975f819dc2043a0350257e92560e59d2e48ae"),
    /**
     * https://privatekeys.pw/bitcoin-testnet/address/miner8VH6WPrsQ1Fxqb7MPgJEoFYX2RCkS
     */
    BitcoinTestnet("miner8VH6WPrsQ1Fxqb7MPgJEoFYX2RCkS", "23e077ffac6f109795a82021dc1698bd9ce40119"),
    /**
     * https://privatekeys.pw/bitcoin/address/bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq
     */
    BitcoinSegregatedWitness("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", "e8df018c7e326cc253faac7e46cdc51e68542c42"),
    /**
     * https://bitinfocharts.com/de/blackcoin/address/BF58Wm7hXSPXxfXm6HwrkWAr45qrZhNHJu
     */
    Blackcoin("BF58Wm7hXSPXxfXm6HwrkWAr45qrZhNHJu", "7482a6aabd8e0bdb6d56a507a6f1352f7cc872fa"),
    /**
     * https://privatekeys.pw/dash/address/XdAUmwtig27HBG6WfYyHAzP8n6XC9jESEw
     */
    Dash("XdAUmwtig27HBG6WfYyHAzP8n6XC9jESEw", "1b2a522cc8d42b0be7ceb8db711416794d50c846"),
    /**
     * https://privatekeys.pw/dogecoin/address/DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L
     */
    Dogecoin("DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L", "830a7420e63d76244ff7cbd1c248e94c14463259"),
    /**
     * https://privatekeys.pw/litecoin/address/LQTpS3VaYTjCr4s9Y1t5zbeY26zevf7Fb3
     */
    Litecoin("LQTpS3VaYTjCr4s9Y1t5zbeY26zevf7Fb3", "3977ea726e43b1db5c1f3ddd634d56ade26eb0a2"),
    /**
     * https://bitinfocharts.com/de/namecoin/address/NAxZHe6yUCADnGAeCs4xrkgEKHjSFVrK5m
     */
    Namecoin("NAxZHe6yUCADnGAeCs4xrkgEKHjSFVrK5m", "9dc42a94d1852c6b42f93e8130d4a589bc3ffa1f"),
    /**
     * https://bitinfocharts.com/de/novacoin/address/4aLpet1cqwr6TuEb8grfAnpvD1eJbTqyvN
     */
    Novacoin("4aLpet1cqwr6TuEb8grfAnpvD1eJbTqyvN", "e261b8251b26231b0e2d62d7f6698d7acee1dbae"),
    /**
     * https://bitinfocharts.com/de/feathercoin/address/7E2vzSfb8o3N8E3PEZ6A48sp6bUGUTA8ro
     */
    Feathercoin("7E2vzSfb8o3N8E3PEZ6A48sp6bUGUTA8ro", "7842e48f5012d40ec702ec41d377559bc51f817a"),
    /**
     * https://bitinfocharts.com/de/vertcoin/address/VfukW89WKT9h3YjHZdSAAuGNVGELY31wyj
     */
    Vertcoin("VfukW89WKT9h3YjHZdSAAuGNVGELY31wyj", "40daff13d20e5c9fdbf17badda0a8a2df6c83f06"),
    /**
     * https://privatekeys.pw/zcash/address/t1VShHAhsQc5RVndQLyM1ZbQXLHKd35GkG1
     */
    Zcash("t1VShHAhsQc5RVndQLyM1ZbQXLHKd35GkG1", "7eeb8313a6c3829217d83a28acb0433a3d6a2bb0");
    
    private final String publicAddress;
    private final String publicKeyHash;
    
    StaticP2PKHAddress(String publicAddress, String publicKeyHash) {
        this.publicAddress = publicAddress;
        this.publicKeyHash = publicKeyHash;
    }

    public String getPublicAddress() {
        return publicAddress;
    }

    public String getPublicKeyHashAsHex() {
        return publicKeyHash;
    }
    
    public byte[] getPublicKeyHash() {
        return Hex.decode(getPublicKeyHashAsHex());
    }
    
    public ByteBuffer getPublicKeyHashAsByteBuffer() {
        return new ByteBufferUtility(true).getByteBufferFromHex(getPublicKeyHashAsHex());
    }
    
    public String getPublicKeyHashAsBase58() {
        final NetworkParameters networkParameters = MainNetParams.get();
        KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(true));
        return keyUtility.toBase58(getPublicKeyHash());
    }
}
