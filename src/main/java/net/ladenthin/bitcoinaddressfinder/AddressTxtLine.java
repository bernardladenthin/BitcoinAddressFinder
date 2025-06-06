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
package net.ladenthin.bitcoinaddressfinder;

import com.github.kiulian.converter.AddressConverter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import org.bitcoinj.base.Base58;
import org.bitcoinj.base.Bech32;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.SegwitAddress;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.jspecify.annotations.Nullable;


/**
 * Most txt files have a common format which uses Base58 address and separated
 * anmount.
 */
public class AddressTxtLine {

    /**
     * Should not be {@link Coin#ZERO} because it can't be written to LMDB.
     */
    public static final Coin DEFAULT_COIN = Coin.SATOSHI;

    public static final String IGNORE_LINE_PREFIX = "#";
    public static final String ADDRESS_HEADER = "address";
    
    
    private final static int VERSION_BYTES_REGULAR = 1;
    private final static int VERSION_BYTES_ZCASH = 2;

    /**
     * If no coins can be found in the line {@link #DEFAULT_COIN} is used.
     *
     * @param line The line to parse.
     * @param keyUtility The {@link KeyUtility}.
     * @return Returns an {@link AddressToCoin} instance.
     */
    @Nullable
    public AddressToCoin fromLine(String line, KeyUtility keyUtility) {
        String[] lineSplitted = SeparatorFormat.split(line);
        String address = lineSplitted[0];
        Coin amount = getCoinIfPossible(lineSplitted, DEFAULT_COIN);
        address = address.trim();
        if (address.isEmpty() || address.startsWith(IGNORE_LINE_PREFIX) || address.startsWith(ADDRESS_HEADER)) {
            return null;
        }
        
        // Riecoin
        {
            final String OP_DUP = "76";
            final String OP_HASH160 = "a9";
            final String OP_PUSH_20_BYTES = "14";
            final int length20Bytes = PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES;
            final String riecoinP2SHPrefix = OP_DUP + OP_HASH160 + OP_PUSH_20_BYTES;
            final int riecoinScriptPubKeyLengthHex = length20Bytes * 2 + riecoinP2SHPrefix.length();
            if (address.length() >= riecoinScriptPubKeyLengthHex && address.startsWith(riecoinP2SHPrefix)) {
                final String hash160Hex = address.substring(riecoinP2SHPrefix.length(), length20Bytes*2+riecoinP2SHPrefix.length());
                final ByteBuffer hash160 = keyUtility.byteBufferUtility.getByteBufferFromHex(hash160Hex);
                return new AddressToCoin(hash160, amount);
            }
        }

        // blockchair Multisig format prefix (P2MS)
        if (
               address.startsWith("d-")
            || address.startsWith("m-")
            || address.startsWith("s-")
        ) {
            return null;
        }

        if (address.startsWith("wkh_")) {
            // BitCore (WKH) is base36 encoded hash160
            String addressWKH = address.substring("wkh_".length());
            
            byte[] hash160 = new Base36Decoder().decodeBase36ToFixedLengthBytes(addressWKH, PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES);

            ByteBuffer hash160AsByteBuffer = keyUtility.byteBufferUtility.byteArrayToByteBuffer(hash160);
            return new AddressToCoin(hash160AsByteBuffer, amount);
        }
        
        if (address.startsWith("q")) {
            // q: bitcoin cash Base58 (P2PKH)
            // convert to legacy address
            address = AddressConverter.toLegacyAddress(address);
        }
        
        if (
            // bitcoin
               address.startsWith("bc1")
            // Bitcoin Oil
            || address.startsWith("btco1")
            // Canada-eCoin
            || address.startsWith("cdn1q")
            // Canada-eCoin
            || address.startsWith("btx1")
            // DeFiChain
            || address.startsWith("df1q")
            // digibyte
            || address.startsWith("dgb1")
            // Doichain
            || address.startsWith("dc1q")
            // Groestlcoin || Groestlcoin TestNet
            || address.startsWith("grs1") || address.startsWith("tgrs1")
            // feathercoin
            || address.startsWith("fc1")
            // litecoin cash
            || address.startsWith("lcc1")
            // litecoin
            || address.startsWith("ltc1")
            // Mooncoin
            || address.startsWith("moon1")
            // Myriad
            || address.startsWith("my1q")
            // namecoin
            || address.startsWith("nc1")
            // Riecoin
            || address.startsWith("ric1")
            // SpaceXpanse
            || address.startsWith("rod1q")
            // syscoin
            || address.startsWith("sys1")
            // TheHolyRogerCoin
            || address.startsWith("rog1q")
            // UFO
            || address.startsWith("uf1q")
            // vertcoin
            || address.startsWith("vtc1")
        ) {
            // bitcoin Bech32 (P2WSH or P2WPKH) or P2TR
            // supported (20 bytes): https://privatekeys.pw/address/bitcoin/bc1qazcm763858nkj2dj986etajv6wquslv8uxwczt
            try {
                //Bech32.Bech32Data bech32Data = Bech32.decode(address);
            } catch (AddressFormatException e) {
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                throw e;
            }
            // do everything manual
            Bech32.Bech32Data bechData = Bech32.decode(address);
            // is protected: bechData.witnessProgram();
            Class<?> clazz = Bech32.Bech32Bytes.class;
            Method witnessProgramMethod;
            try {
                witnessProgramMethod = clazz.getDeclaredMethod("witnessProgram");
                witnessProgramMethod.setAccessible(true);
                try {
                    byte[] hash160AsByteArray = (byte[]) witnessProgramMethod.invoke(bechData);
                    if (hash160AsByteArray.length == SegwitAddress.WITNESS_PROGRAM_LENGTH_PKH) {
                        final ByteBuffer hash160 = keyUtility.byteBufferUtility.byteArrayToByteBuffer(hash160AsByteArray);
                        return new AddressToCoin(hash160, amount);
                    } else if (hash160AsByteArray.length == SegwitAddress.WITNESS_PROGRAM_LENGTH_SH) {
                        return null;
                    } else if (hash160AsByteArray.length == SegwitAddress.WITNESS_PROGRAM_LENGTH_TR) {
                        return null;
                    } else {
                        throw new AddressFormatException();
                    }
                } catch (IllegalAccessException ex) {
                } catch (InvocationTargetException ex) {
                }
            } catch (NoSuchMethodException ex) {
            } catch (SecurityException ex) {
            }
            throw new AddressFormatException();
        } else if (address.startsWith("p")) {
            // p: bitcoin cash / CashAddr (P2SH), this is a unique format and does not work
            // p: peercoin possible
            try {
                ByteBuffer hash160 = getHash160AsByteBufferFromBase58AddressUnchecked(address, keyUtility, VERSION_BYTES_ZCASH);
                return new AddressToCoin(hash160, amount);
            } catch (RuntimeException e) {
                return null;
            }
        } else if (
                   address.startsWith("1")
                || address.startsWith("2")
                || address.startsWith("3")
                || address.startsWith("4")
                || address.startsWith("7")
                || address.startsWith("8")
                || address.startsWith("9")
                || address.startsWith("a")
                || address.startsWith("A")
                || address.startsWith("B")
                || address.startsWith("b")
                || address.startsWith("C")
                || address.startsWith("d")
                || address.startsWith("D")
                || address.startsWith("E")
                || address.startsWith("F")
                || address.startsWith("G")
                || address.startsWith("H")
                || address.startsWith("i")
                || address.startsWith("J")
                || address.startsWith("K")
                || address.startsWith("L")
                || address.startsWith("M")
                || address.startsWith("N")
                || address.startsWith("p")
                || address.startsWith("P")
                || address.startsWith("Q")
                || address.startsWith("R")
                || address.startsWith("s")
                || address.startsWith("S")
                || address.startsWith("t")
                || address.startsWith("T")
                || address.startsWith("u")
                || address.startsWith("V")
                || address.startsWith("W")
                || address.startsWith("x")
                || address.startsWith("X")
        ) {
            // prefix clashes for signs: 2, 7, 8, 9, A, C, M
            //
            // Base58 P2SH
            // 2: Mooncoin / Particl
            // 3: litecoin deprecated / bitcoin / Particl
            // 7: dash / Riecoin
            // 8: DeFiChain / BYTZ
            // 9: dogecoin multisig / 42-coin
            // A: dogecoin / SaluS
            // B: CloakCoin
            // C: UFO
            // M: litecoin / Myriad
            // t: Zcash
            //
            // Base58 P2PKH
            // 1: Terracoin
            // 2: BitCore / Pinkcoin
            // 4: novacoin / Myriad / 42-coin
            // 7: feathercoin / Dimecoin
            // 8: Vanillacash
            // 9: Catcoin
            // a: Firo
            // A: AuroraCoin / Primecoin
            // B: curecoin / BitBlocks / blackcoin / UFO / Smileycoin / Blocknet / BitcoinPlus
            // b: Bitmark / BolivarCoin
            // C: CloakCoin / CROWN / ChessCoin / Artbyte / Canada-eCoin
            // d: LiteDoge / Diamond / DeFiChain
            // D: dogecoin / digibyte / PIVX / DigitalCoin / Divicoin / ColossusXT
            // e: Electron
            // E: Emerald / InfiniLooP
            // F: Groestlcoin
            // G: bitcoin gold / Goldcash
            // H: Herencia
            // i: Innova / I/O Coin / Infinitecoin
            // J: MasterNoder2
            // K: Lynx
            // L: litecoin / Luckycoin / Lanacoin / e-Gulden / Elite
            // M: Mooncoin
            // N: namecoin / Deutsche eMark / Doichain
            // p: Element
            // P: Peercoin / PotCoin / PAC Protocol / PutinCoin v2 / Particl / PandaCoin / PakCoin
            // Q: Quark
            // R: reddcoin / Komodo / NewYorkCoin / Particl / Raptoreum / SpaceXpanse
            // s: BYTZ
            // S: Sterlingcoin / Syscoin / SaluS / Alias
            // t: Zcash
            // T: Trezarcoin
            // u: Unobtanium
            // U: Coino
            // V: vertcoin / VeriCoin / Versacoin / TheHolyRogerCoin
            // W: WorldCoin
            // x: Clam / iXcoin
            // X: dash / Validity
            
            if (address.startsWith("t")) {
                // ZCash has two version bytes
                ByteBuffer hash160 = getHash160AsByteBufferFromBase58AddressUnchecked(address, keyUtility, VERSION_BYTES_ZCASH);
                return new AddressToCoin(hash160, amount);
            } else {
                ByteBuffer hash160 = getHash160AsByteBufferFromBase58AddressUnchecked(address, keyUtility, VERSION_BYTES_REGULAR);
                return new AddressToCoin(hash160, amount);
            }
        } else {
            // bitcoin Base58 (P2PKH)
            ByteBuffer hash160;
            try {
                hash160 = keyUtility.getHash160ByteBufferFromBase58String(address);
            } catch (
                    AddressFormatException.InvalidChecksum   // InvalidChecksum
                  | AddressFormatException.WrongNetwork      // e.g. bitcoin testnet
                  | AddressFormatException.InvalidDataLength // e.g. too short address
                  e
            ) {
                hash160 = getHash160AsByteBufferFromBase58AddressUnchecked(address, keyUtility, VERSION_BYTES_REGULAR);
            }
            return new AddressToCoin(hash160, amount);
        }
    }

    private ByteBuffer getHash160AsByteBufferFromBase58AddressUnchecked(String base58, KeyUtility keyUtility, int srcPos) {
        byte[] hash160 = getHash160fromBase58AddressUnchecked(base58, srcPos);
        ByteBuffer hash160AsByteBuffer = keyUtility.byteBufferUtility.byteArrayToByteBuffer(hash160);
        return hash160AsByteBuffer;
    }

    byte[] getHash160fromBase58AddressUnchecked(String base58, int srcPos) {
        byte[] decoded = Base58.decode(base58);
        byte[] hash160 = new byte[20];
        int toCopy = Math.min(decoded.length - srcPos, hash160.length);
        System.arraycopy(decoded, srcPos, hash160, 0, toCopy);
        return hash160;
    }

    @Nullable
    private Coin getCoinIfPossible(String[] lineSplitted, Coin defaultValue) throws NumberFormatException {
        if (lineSplitted.length > 1) {
            String amountString = lineSplitted[1];
            try {
                return Coin.valueOf(Long.valueOf(amountString));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }
}
