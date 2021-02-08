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
import java.nio.ByteBuffer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.script.Script;

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
    public static final String COMMA = ",";
    public static final String SEMICOLON = ";";
    public static final String TAB_SPLIT = "\t";
    public static final String OR = "|";
    public static final Pattern SPLIT_REGEX = Pattern.compile(COMMA + OR + SEMICOLON + OR + TAB_SPLIT, 0);
    
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
        String[] lineSplitted = SPLIT_REGEX.split(line);
        String address = lineSplitted[0];
        Coin amount = getCoinIfPossible(lineSplitted, DEFAULT_COIN);
        address = address.trim();
        if (address.isEmpty() || address.startsWith(IGNORE_LINE_PREFIX) || address.startsWith(ADDRESS_HEADER)) {
            return null;
        }

        if (address.startsWith("q")) {
            // q: bitcoin cash Base58 (P2PKH)
            // convert to legacy address
            address = AddressConverter.toLegacyAddress(address);
        }

        if (address.startsWith("d-") || address.startsWith("m-") || address.startsWith("s-")) {
            // blockchair format for Bitcoin (d-) and Bitcoin Cash (m-) and (s-) (P2MS)
            return null;
        } else if (address.startsWith("bc1")) {
            // bitcoin Bech32 (P2WPKH) or bitcoin Bech32 (P2WSH)
            SegwitAddress segwitAddress = SegwitAddress.fromBech32(keyUtility.networkParameters, address);
            if (segwitAddress.getOutputScriptType() == Script.ScriptType.P2WSH) {
                return null;
            }
            byte[] hash = segwitAddress.getHash();
            ByteBuffer hash160 = keyUtility.byteBufferUtility.byteArrayToByteBuffer(hash);
            return new AddressToCoin(hash160, amount);
        } else if (address.startsWith("ltc")) {
            // litecoin Bech32 (P2WPKH)
            //https://privatekeys.pw/litecoin/address/ltc1qd5wm03t5kcdupjuyq5jffpuacnaqahvfsdu8smf8z0u0pqdqpatqsdrn8h
            return null;
        } else if (address.startsWith("p")) {
            // p: bitcoin cash / CashAddr (P2SH), this is a unique format and does not work
            return null;
        } else if (address.startsWith("7") || address.startsWith("A") || address.startsWith("9") || address.startsWith("M") || address.startsWith("3") || address.startsWith("t") || address.startsWith("X") || address.startsWith("D") || address.startsWith("L") || address.startsWith("G") || address.startsWith("B") || address.startsWith("V") || address.startsWith("N") || address.startsWith("4") || address.startsWith("R")) {
            // prefix clashes for signs: 7
            //
            // Base58 P2SH
            // 7: dash
            // A: dogecoin
            // 9: dogecoin
            // M: litecoin
            // 3: litecoin deprecated / bitcoin
            // t: Zcash
            //
            // Base58 P2PKH
            // X: dash
            // D: dogecoin
            // L: litecoin
            // G: bitcoin gold
            // B: blackcoin
            // 7: feathercoin
            // V: vertcoin
            // N: namecoin
            // 4: novacoin
            // R: reddcoin
            // t: Zcash

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
            } catch (AddressFormatException.InvalidChecksum e) {
                hash160 = getHash160AsByteBufferFromBase58AddressUnchecked(address, keyUtility, VERSION_BYTES_REGULAR);
            } catch (AddressFormatException.WrongNetwork e) {
                // bitcoin testnet
                hash160 = getHash160AsByteBufferFromBase58AddressUnchecked(address, keyUtility, VERSION_BYTES_REGULAR);
            } catch (AddressFormatException.InvalidDataLength e) {
                // too short address
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
