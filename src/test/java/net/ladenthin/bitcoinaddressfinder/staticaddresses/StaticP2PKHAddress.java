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

public enum StaticP2PKHAddress implements PublicAddress {
    
    /**
     * https://chainz.cryptoid.info/42/address.dws?7793.htm
     */
    _42Coin("4MTfaF2ummgMp456oxRqefv3YSXPAJJUrt", "55136a92273338f277a5d8a845e637b7f4b8f1b2"),
    /**
     * https://chainz.cryptoid.info/aby/address.dws?4281.htm
     */
    Artbyte("CVVdgmYmygNSParn5SR8agYKEMsiMVFQiF", "8eee0ae5d8eaa9a0440feb600e41a1463f7d7b39"),
    /**
     * https://chainz.cryptoid.info/alias/address.dws?53237.htm
     */
    Alias("SSvrL2DGaCHFTbEA3CBx6pNTzs6EMXfrcp", "3dd0c10264dc3167da849fd62f297b7d2ae0c556"),
    /**
     * https://chainz.cryptoid.info/aur/address.dws?947212.htm
     */
    AuroraCoin("AYZUxKP8JcgwxksZTnDKyAeb5o4EwYXyiT", "b822342d1c6b82cc8979128cbf76186463c3a6c0"),
    /**
     * https://chainz.cryptoid.info/bbk/address.dws?348283.htm
     */
    BitBlocks("BKxdSFuJQDgMgegACDtnv5SoR5iqB5WATk", "aa2082ea8a4ea667f767d37fdea9986b14469006"),
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
     * https://btc.cryptoid.info/btco/address.dws?13423.htm
     */
    BitcoinOil("BY1DJzLaaTnjcueh2QLTnV3C8EBHWymcPN", "2e3f213ff41f777385de34e333d79c17efe04015"),
    /**
     * https://chainz.cryptoid.info/xbc/address.dws?117821.htm
     */
    BitcoinPlus("BMPf5LdLW5bRtG7VnTsKH2tQBd4RpVatrW", "b9d4679855e410bb7e3881594299b2b9e3f5cc9c"),
    /**
     * https://privatekeys.pw/bitcoin-testnet/address/miner8VH6WPrsQ1Fxqb7MPgJEoFYX2RCkS
     */
    BitcoinTestnet("miner8VH6WPrsQ1Fxqb7MPgJEoFYX2RCkS", "23e077ffac6f109795a82021dc1698bd9ce40119"),
    /**
     * https://privatekeys.pw/bitcoin/address/bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq
     */
    BitcoinSegregatedWitness("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq", "e8df018c7e326cc253faac7e46cdc51e68542c42"),
    /**
     * https://chainz.cryptoid.info/btx/address.dws?6572703.htm
     */
    BitCore("2NuwYZ1frd3s7FPTGLqkR2zNLTviQcac7o", "6c9519147914a961d277d5daff370ae1c7c4a5fa"),
    /**
     * https://chainz.cryptoid.info/btmk/address.dws?1351759.htm
     */
    Bitmark("bb2CrvTj9EfwxYkqkf5oYpEq8sTxxxTzzV", "f486a398c5071b090a64b41c97bf42024063eb3d"),
    /**
     * https://bitinfocharts.com/de/blackcoin/address/BF58Wm7hXSPXxfXm6HwrkWAr45qrZhNHJu
     */
    Blackcoin("BF58Wm7hXSPXxfXm6HwrkWAr45qrZhNHJu", "7482a6aabd8e0bdb6d56a507a6f1352f7cc872fa"),
    /**
     * https://chainz.cryptoid.info/block/address.dws?16204.htm
     */
    Blocknet("BUE65eaeh3NZwNm2p5yKSstxteYvShi4yu", "04cdffba976b79c0d25f06c56151fef6a2a3156b"),
    /**
     * https://chainz.cryptoid.info/boli/address.dws?25860.htm
     */
    BolivarCoin("bKkVYXDjG8cRrR11cvmujAg7RkVHD2Whz7", "4d0405e0b2932f994cc0f686512a3ccd2c7d714d"),
    /**
     * https://chainz.cryptoid.info/bytz/address.dws?547646.htm
     */
    BYTZ("sLR4SH5jyqyBW7PSvynBcuj3ftSxF89A6s", "17c40395c01837b0661ca7542b0812eb685d3dbc"),
    /**
     * https://btc.cryptoid.info/cdn/address.dws?124.htm
     */
    CanadaECoin("CQcWHGHSwa3oNVdKMQ2QQrsprAusKVHpgh", "596224cf7ea3ea29af56db750c1056c9769415fa"),
    /**
     * https://chainz.cryptoid.info/cat/address.dws?161276.htm
     */
    Catcoin("9jMLSxzshX8dHvv2SjhrfGcKq15UsyjeZY", "b248332ed86b2041fe7222953cbc4de6607ab7b6"),
    /**
     * https://chainz.cryptoid.info/chess/address.dws?142577.htm
     */
    ChessCoin("CahtBj65GuRju1qLkxhx1Ebc8FzGKECNG1", "c817c433719ca903caba000ec6763da7f3675d7a"),
    /**
     * https://chainz.cryptoid.info/clam/address.dws?3846102.htm
     */
    Clam("xJDCLAMZpUG2XsTdek7ZBSW5RhUxHL5MrV", "6ca9d69643c78efc8f82a62b73793f711c3b6c2c"),
    /**
     * https://chainz.cryptoid.info/cloak/address.dws?316050.htm
     */
    CloakCoin("C5REwXC5GL9HN3bLy6XcikhwLzitcE2Kha", "86d65f6ff0ecacf26f33a31a7d74f22f426ff77b"),
    /**
     * https://chainz.cryptoid.info/crw/address.dws?1628649.htm
     */
    CROWN("CRWVHacqVcMNdCduFLTKRT9EmiJCku7ydH6R", "7507bdfe41900b209a5f8d341b867cbc72f470ec"),
    /**
     * https://chainz.cryptoid.info/cno/address.dws?221360.htm
     */
    Coino("UWdeqR8s34asJ1qEtWdWeCyNDqg3aXeCjH", "5edf2ec006b5312443f66cc966472d2b1b385b89"),
    /**
     * https://chainz.cryptoid.info/colx/address.dws?116595.htm
     */
    ColossusXT("DSesymccyAQr6LjGLCHsvHzE41uKMk86XS", "ebfca2b8c5f6bdcc926ee3fe238a74c1440e22a7"),
    /**
     * https://chainz.cryptoid.info/cure/address.dws?12256.htm
     */
    Curecoin("B4dbDb5Qt7DifAd26LqJZi756fNB9YS2JB", "01fcde97ab8306f16ea73417c5e474c42b647ffb"),
    /**
     * https://privatekeys.pw/dash/address/XdAUmwtig27HBG6WfYyHAzP8n6XC9jESEw
     */
    Dash("XdAUmwtig27HBG6WfYyHAzP8n6XC9jESEw", "1b2a522cc8d42b0be7ceb8db711416794d50c846"),
    /**
     * https://chainz.cryptoid.info/dfi/address.dws?2581182.htm
     */
    DeFiChain("dbxMfrpLMQXUdcdz8dUs5gp97nzH5A2br5", "f7330ba6437fe7454c8e46f967fc28ca01cd5d2f"),
    /**
     * https://chainz.cryptoid.info/dem/address.dws?85.htm
     */
    DeutscheEMark("NbN8X6sUKw9cuX5JYX7X8RS2ro5ZbkAFDf", "a97c7e369e2a6143f740a102dff61ba39e8aec39"),
    /**
     * https://chainz.cryptoid.info/dmd/address.dws?250463.htm
     */
    Diamond("dZ2n5pMvviVjps7kJ4CYMniakSeDLjz4su", "d720b604208ce237d575dc2223fec7214488864a"),
    /**
     * https://dgb.tokenview.io/en/address/DUFy8rH6dxkGutvo43WZryXjR3DU612sjh
     */
    Digibyte("DUFy8rH6dxkGutvo43WZryXjR3DU612sjh", "fd979fe2f333d8a6cc0cb21eeb34d958a2691aa8"),
    /**
     * https://chainz.cryptoid.info/dgc/address.dws?1250320.htm
     */
    DigitalCoin("DQMLne3GZHo4uiu5nWsxdFsTrrmxYJnubS", "d2bb4667cb0c1b5d7eae0a97d2af3d83961778f4"),
    /**
     * https://chainz.cryptoid.info/dime/address.dws?1095943.htm
     */
    Dimecoin("7LgAUerFPc2RfGJ9bLK14NCH2ZmXZNFnc4", "c11e4bc44d8874da582f33b25304fa3207d2b229"),
    /**
     * https://chainz.cryptoid.info/divi/address.dws?896418.htm
     */
    Divicoin("DGu4qUwJNK3MW4EA1YgYoNMPgvBfggN8az", "80fa43aa6c5b022acf18b7a82ee866e334e035a0"),
    /**
     * https://privatekeys.pw/dogecoin/address/DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L
     */
    Dogecoin("DH5yaieqoZN36fDVciNyRueRGvGLR3mr7L", "830a7420e63d76244ff7cbd1c248e94c14463259"),
    /**
     * https://btc.cryptoid.info/doi/address.dws?54325.htm
     */
    Doichain("N6QZUS3kbPyyTHa1Km6YKereP4rNjiiCyJ", "6bd681fb9d151be554e28d3c95b9fb73d9e6c11c"),
    /**
     * https://chainz.cryptoid.info/efl/address.dws?228227.htm
     */
    eGulden("LfWE9Jqe8sJG31eeYemjvJN5yPyYxDNRJ1", "de766d85720138fc3fef6192e8c3641c55d9c867"),
    /**
     * https://chainz.cryptoid.info/elt/address.dws?5151818.htm
     */
    Electron("eCYQZH1khh3p6LEWYE2skmSuDU8UPuZu7T", "7297dfd7cc4bacaf7400f0f27d09907701781870"),
    /**
     * https://chainz.cryptoid.info/hyp/address.dws?187625.htm
     */
    Element("pQBXh5QCNMSu5kAqc3xzc1XUhePncwugmA", "6f8f0a226685adc6654c5e7f3ba1746515f2e1a3"),
    /**
     * https://chainz.cryptoid.info/1337/address.dws?758581.htm
     */
    Elite("LZ1mXdpVU9akMvav4BKXXbg28MUzVLNGJB", "9743cf1eae02588033f86ef3b48204561df43197"),
    /**
     * https://chainz.cryptoid.info/emd/address.dws?1369726.htm
     */
    Emerald("EnMcky7NAEaJUEDS5DfG4J6VQ4So66XDqK", "4052ab77e7f4ea9ffe56b439de36bf4f64155123"),
    /**
     * https://bitinfocharts.com/de/feathercoin/address/7E2vzSfb8o3N8E3PEZ6A48sp6bUGUTA8ro
     */
    Feathercoin("7E2vzSfb8o3N8E3PEZ6A48sp6bUGUTA8ro", "7842e48f5012d40ec702ec41d377559bc51f817a"),
    /**
     * https://chainz.cryptoid.info/firo/address.dws?1494520.htm
     */
    Firo("a8tXPzGEYxcehK6ehfpCUXTtQ6VocQHHRT", "59a837e0745d9119d7ae272be5239ebb080def77"),
    /**
     * https://chainz.cryptoid.info/gold/address.dws?83174.htm
     */
    Goldcash("GJU2Y9Nk8mhEcsEXfJRv7y8QLeSEW48Q7c", "06d4158cc047ae8b2861fb51817c9e66ea95c42f"),
    /**
     * https://chainz.cryptoid.info/grs/address.dws?1346801.htm
     */
    Groestlcoin("Fm5t7ayvYgJX7UF8CYiMRQyLRy6F38Px9S", "ae9800906772c4578cb27c0f90e3f165a2bc031e"),
    /**
     * https://chainz.cryptoid.info/heirs/address.dws?2933.htm
     */
    Herencia("HSBNkVSiw1JXe54pcm8sF8gMDViGgChdpv", "d7a62ff9cf419b28c2701502c5b98da27dd6d023"),
    /**
     * https://chainz.cryptoid.info/ioc/address.dws?65914.htm
     */
    IOCoin("iYL3GepzxkhnojHbxriiKub4eSG5yd2gZh", "3c80cfa9a979502574351e84ec0f7ef8f0d134bb"),
    /**
     * https://chainz.cryptoid.info/inn/address.dws?17764.htm
     */
    Innova("i8N8m3h8DXWrBYkFAgCKjnPY2vriezuB5h", "35a334ed46aaa35a7795bf1b576850a3406f8800"),
    /**
     * https://chainz.cryptoid.info/il8p/address.dws?111.htm
     */
    InfiniLooP("EYWLjjKWCTrb8hCTg8RJXUfbnjhNa8rRJ6", "a867474e613850d49d8414fce7d8bb966040bb06"),
    /**
     * https://chainz.cryptoid.info/ifc/address.dws?2316816.htm
     */
    Infinitecoin("iRRmTw3y6dLTG6Qpu1R141yc1rL49wtQQH", "f0cd3b9102e339eab7ba40bdb57619263ed89432"),
    /**
     * https://chainz.cryptoid.info/ixc/address.dws?634789.htm
     */
    iXcoin("xuwcXvEq64V5cYJ8ChrfoiRs53JaB98ee6", "f49bea19b0d0c89d12007716268c1c54cc001979"),
    /**
     * https://chainz.cryptoid.info/kmd/address.dws?2911308.htm
     */
    Komodo("RBmjPBHH65gbBSgLBByM1NxJJWwZMmG3Pa", "1b5607bab686183e8cde7f77f5a887e807a4930a"),
    /**
     * https://chainz.cryptoid.info/lana/address.dws?78499.htm
     */
    Lanacoin("LTdb5KrqryPihAU1RebDCy8tVxi1SVtGqu", "5c394d80bcd5525edd67777d3566c39ee52a3872"),
    /**
     * https://privatekeys.pw/litecoin/address/LQTpS3VaYTjCr4s9Y1t5zbeY26zevf7Fb3
     */
    Litecoin("LQTpS3VaYTjCr4s9Y1t5zbeY26zevf7Fb3", "3977ea726e43b1db5c1f3ddd634d56ade26eb0a2"),
    /**
     * https://chainz.cryptoid.info/lcc/address.dws?CdEK8wqqzg5ekA6hT9uwiv1TfPad9Wjx1z.htm
     */
    LitecoinCash("CdEK8wqqzg5ekA6hT9uwiv1TfPad9Wjx1z", "e3c953658a0c16ec171553fc923d7c2e11163d32"),
    /**
     * https://chainz.cryptoid.info/ldoge/address.dws?132909.htm
     */
    LiteDoge("dcdGuzwAjRH8DwpvyzKyLPdvuArfzXcxtN", "fe8f4c38418fea2d9b0c184c812537e78f2f35d0"),
    /**
     * https://chainz.cryptoid.info/lky/address.dws?358026.htm
     */
    Luckycoin("LBZYLUyFVyA3FymzQD7EpFfPNC28SLwpX3", "abf35719593fd163e15fb9daba4752e3ce0fbda5"),
    /**
     * https://chainz.cryptoid.info/lynx/address.dws?999646.htm
     */
    Lynx("KJ2MGS3jq4DPkVmE1ephMCbT7ojDcDSJRG", "769eca30fe71718db7fba89f101d243d55d50b02"),
    /**
     * https://chainz.cryptoid.info/mn2/address.dws?3764.htm
     */
    MasterNoder2("JXdRe3J2ZP3U865Y4jLZwGheNV5nRarJKM", "8fa35695a1d627429797681c059a8efed34188ab"),
    /**
     * https://chainz.cryptoid.info/moon/address.dws?1498374.htm
     */
    Mooncoin("MR3pPKTnPAdGaGAabC47ax954hzufzobkr", "bc1d5093b7e68e334b973f20adeb54ba85389a76"),
    /**
     * https://chainz.cryptoid.info/xmy/address.dws?3867024.htm
     */
    Myriad("4qJBRfzfsXHjTn22B946xTgV1EjBAAUwN3", "866bb2b191810842189f0dab3ad6fcdcf2ee368d"),
    /**
     * https://bitinfocharts.com/de/namecoin/address/NAxZHe6yUCADnGAeCs4xrkgEKHjSFVrK5m
     */
    Namecoin("NAxZHe6yUCADnGAeCs4xrkgEKHjSFVrK5m", "9dc42a94d1852c6b42f93e8130d4a589bc3ffa1f"),
    /**
     * https://chainz.cryptoid.info/nyc/address.dws?1710034.htm
     */
    NewYorkCoin("RWKh9kHForWNUqoAiQpzoekiPUpwhGceRQ", "e6d3be0495d42340b2b95d269ffcec077d3a040b"),
    /**
     * https://bitinfocharts.com/de/novacoin/address/4aLpet1cqwr6TuEb8grfAnpvD1eJbTqyvN
     */
    Novacoin("4aLpet1cqwr6TuEb8grfAnpvD1eJbTqyvN", "e261b8251b26231b0e2d62d7f6698d7acee1dbae"),
    /**
     * https://chainz.cryptoid.info/pnd/address.dws?5.htm
     */
    PandaCoin("PWdoUrPx9F2jeZpQGhFs5jr5CDmuE98Foe", "f1d1ec1375ef9cf6ce0232563ea7b611ea04dfb7"),
    /**
     * https://chainz.cryptoid.info/part/address.dws?437060.htm
     */
    Particl_P("PfF7TAgTcKBLKNqrzBP7BEf1giQ1YzBmfq", "50407d0e5fae1e4b440a51aa0ddc75bcc0b3925e"),
    /**
     * https://chainz.cryptoid.info/part/address.dws?168727.htm
     */
    Particl_R("RQYUDd3EJohpjq62So4ftcV5XZfxZxJPe9", "a76d99568f600ad29333229f74b4724bc991192a"),
    /**
     * https://chainz.cryptoid.info/pac/address.dws?1398861.htm
     */
    PACProtocol("PRppFVk7UgeZEZzEF2zHWkvRPGU4rqzSxc", "bd0e82e8d1170adade5253c0293a3904261f73cc"),
    /**
     * https://chainz.cryptoid.info/pak/address.dws?3611.htm
     */
    PakCoin("PQC6NTc7Pi4reEPWnJEDAmMhqumjmrSAwV", "ab24738aec2c21a9263e89b4973b84c7e0263a78"),
    /**
     * https://chainz.cryptoid.info/ppc/address.dws?932101.htm
     */
    Peercoin("PN9ZrKwPttJxM8VMsfBFtAWzbycpG2cosD", "94b9e4350dd548e23642a903b79ec90d65ede272"),
    /**
     * https://chainz.cryptoid.info/pink/address.dws?299.htm
     */
    Pinkcoin("2Ki78CrqJMZBPP8y4ShhstMJantscKUe24", "496fb9eab64aad43243809fe5f7842eed1ae5cea"),
    /**
     * https://chainz.cryptoid.info/xpm/address.dws?4777847.htm
     */
    Primecoin("ALcTQaSPKVNdkXMZNG5b75GYHr1C5Yf2Zi", "3510a69548ee579eae8271d0f2b81094d48d1b69"),
    /**
     * https://chainz.cryptoid.info/ppc/address.dws?1288727.htm
     */
    Peercoin_SmallPrefix("p77CZFn9jvg9waCzKBzkQfSvBBzPH1nRre", "23c89acd257e796c209f6f1914ed999f45076d10"),
    /**
     * https://chainz.cryptoid.info/pivx/address.dws?884545.htm
     */
    PIVX("DSfrFyx2pYR2GzxQuVoysfmqaiRbNf9iR6", "ec2b9d8bf19a3bd4cf75f1c72823832fab597200"),
    /**
     * https://chainz.cryptoid.info/pot/address.dws?1587087.htm
     */
    PotCoin("PSSaD2tSKy3NBi3SgeR8UKFXVzjmp8kTj9", "c3d1c390d84f32d482eb8e3cfcf120ed82eb3192"),
    /**
     * https://chainz.cryptoid.info/put/address.dws?6846.htm
     */
    PutinCoinV2("PLb6rb9pxN4YtmfUxjzwUW7TeoqzRPu9cY", "839deb912be5bd9f306cb7dae3d8fdb7562ba983"),
    /**
     * https://chainz.cryptoid.info/qrk/address.dws?1060048.htm
     */
    Quark("QNix46KwSXLWZnfaTPeEKo4LAN8rrhgRMS", "17417298753389ceed2a4be28c2b4876ddd14d3e"),
    /**
     * https://chainz.cryptoid.info/rtm/address.dws?1909401.htm
     */
    Raptoreum("RSbJLCmy8bntuXSeCkFJN72y3ZS1jWopXC", "bde6d877af4d3bcc751ed975cfb8a3d2ae281a4d"),
    /**
     * https://bitinfocharts.com/de/reddcoin/address/RdLmuVt2ByWxXhiqhKSk4aV9UPLj6Lu3HL
     */
    Reddcoin("RdLmuVt2ByWxXhiqhKSk4aV9UPLj6Lu3HL", "33d101e6da33f9d1da43069cfd7bf370208c9c37"),
    /**
     * https://chainz.cryptoid.info/sls/address.dws?1722.htm
     */
    SaluS("SMi87Up6Jh6GKTGFGfx1LLPKwFFWKRhDA3", "048fe4a5563845f36912a0878c03f1871ddc251b"),
    /**
     * https://chainz.cryptoid.info/smly/address.dws?940996.htm
     */
    Smileycoin("BDKdA9AkMvcQSHeMKy5ua6TJ4PnCK4vsQd", "61502257e1724b08f97ae822ef304a619d09d86a"),
    /**
     * https://btc.cryptoid.info/rod/address.dws?676451.htm
     */
    SpaceXpanse("RFufZfLKFiZrDjQUZrEFDCr5din6syy8gM", "48b6ac418c0622e4bf41e21d142638d2a9fd445f"),
    /**
     * https://chainz.cryptoid.info/slg/address.dws?88363.htm
     */
    Sterlingcoin("SeZm4fynaUaxwpsLmTPcXk8pCUbABbe2Z9", "bd75222a12897b9430d273cd14fd18c2514732b1"),
    /**
     * https://chainz.cryptoid.info/sys/address.dws?10476.htm
     */
    Syscoin("SUBbe8vb7ng9CxZE9J3hma2CCkBh5VBHrv", "4b92dbc7787cc53b5aa02bc026bd27c5dd5b1d93"),
    /**
     * https://chainz.cryptoid.info/trc/address.dws?1286494.htm
     */
    Terracoin("1FkmsofK4BMZPSZhQk55AoPKSiWiS933En", "a1d91b9dde62edbccda90476572842c5bdafbd53"),
    /**
     * https://btc.cryptoid.info/roger/address.dws?56145.htm
     */
    TheHolyRogerCoin("VWHKSae8spVDCXqQLUNWpjmLPERfGrgToN", "d73df84312ce9b05929ce462fae447eccff9bc3f"),
    /**
     * https://chainz.cryptoid.info/tzc/address.dws?956019.htm
     */
    Trezarcoin("TwSNVquvjtSdChxo8dbj7uq2pruKfmXAYM", "f2c0d17f28c7785222a1393a231a4aa06382b2a1"),
    /**
     * https://chainz.cryptoid.info/ufo/address.dws?193002.htm
     */
    UFO("Bs9EsQi1EnR5gAca148MJiYGx7SyaYN7uA", "002e2619af0dfd932513e5f3e855a0357abc9995"),
    /**
     * https://chainz.cryptoid.info/uno/address.dws?291074.htm
     */
    Unobtanium("uRJ4zdTGSLVDTXoVdB7jHHz6W14DhicmWV", "45b8c9a7faee655dd92163bf5ff68066b076ac35"),
    /**
     * https://chainz.cryptoid.info/val/address.dws?117121.htm
     */
    Validity("XeUbUeeR5igC3qoiY7ZdvRNrdq5QvBDFTS", "298f874855a346589845ecc35635cfef81788a2e"),
    /**
     * https://chainz.cryptoid.info/xvc/address.dws?22910.htm
     */
    Vanillacash("8Z7GY7YR2pDLwLxqLmzca6oHXVnT6NbQzU", "c5b68ee79f2ba1af86375ab221b2e72728ec89c7"),
    /**
     * https://chainz.cryptoid.info/vrc/address.dws?531079.htm
     */
    VeriCoin("VSXZqy4WxCtXL8CPjvpndvfJPKkpHM1Aqg", "ae0f5034a122fcd995a44c69f7edb1945d4f4daa"),
    /**
     * https://chainz.cryptoid.info/vcn/address.dws?22215.htm
     */
    Versacoin("VUKvcdKG1UhdcWtwhT8iDN43XC253khqnu", "c1cbe947645e17b019b7595c6c86798046df4161"),
    /**
     * https://bitinfocharts.com/de/vertcoin/address/VfukW89WKT9h3YjHZdSAAuGNVGELY31wyj
     */
    Vertcoin("VfukW89WKT9h3YjHZdSAAuGNVGELY31wyj", "40daff13d20e5c9fdbf17badda0a8a2df6c83f06"),
    /**
     * https://chainz.cryptoid.info/wdc/address.dws?1582980.htm
     */
    WorldCoin("WTUGT6Qgj1DXWfJ6vNjyoT2ambqgxNZJdw", "349ef771619cc37b392d9872b12eb86e9db20921"),
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

    @Override
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
        final Network network = new NetworkParameterFactory().getNetwork();
        KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
        return keyUtility.toBase58(getPublicKeyHash());
    }
}
