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

import com.google.common.hash.Hashing;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.nio.ByteBuffer;
import org.bitcoinj.base.Base58;
import org.bitcoinj.base.Bech32;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.SegwitAddress;
import org.bitcoinj.base.exceptions.AddressFormatException;
import org.bouncycastle.util.encoders.DecoderException;
import org.jspecify.annotations.NonNull;
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
    public static final String BITCOIN_CASH_PREFIX = "bitcoincash:";
    
    
    public final static int VERSION_BYTES_REGULAR = 1;
    public final static int VERSION_BYTES_ZCASH = 2;
    public final static int CHECKSUM_BYTES_REGULAR = 4;
    
    /**
    * Witness version 0, used for SegWit v0 addresses such as:
    * <ul>
    *   <li><b>P2WPKH</b> – Pay to Witness Public Key Hash</li>
    *   <li><b>P2WSH</b> – Pay to Witness Script Hash</li>
    * </ul>
    * Defined in <a href="https://github.com/bitcoin/bips/blob/master/bip-0173.mediawiki">BIP-173</a>.
    */
   private static final int WITNESS_VERSION_0 = 0;

   /**
    * Witness version 1, introduced with Taproot (SegWit v1):
    * <ul>
    *   <li><b>P2TR</b> – Pay to Taproot</li>
    * </ul>
    * Defined in <a href="https://github.com/bitcoin/bips/blob/master/bip-0341.mediawiki">BIP-341</a> and
    * encoded using Bech32m as specified in <a href="https://github.com/bitcoin/bips/blob/master/bip-0350.mediawiki">BIP-350</a>.
    */
   private static final int WITNESS_VERSION_1 = 1;
    
    /**
     * Parses a line containing an address and optional amount.
     * Returns {@code null} if the address is unsupported, malformed, or marked as ignored.
     * <p>
     * If no coin amount is specified in the line, {@link #DEFAULT_COIN} is used as a fallback.
     *
     * @param line the line to parse
     * @param keyUtility the {@link KeyUtility} used for conversions
     * @return an {@link AddressToCoin} instance, or {@code null} if the address is invalid or unsupported
     */
    @Nullable
    public AddressToCoin fromLine(String line, KeyUtility keyUtility) {
        // Remove the Bitcoin Cash prefix (which includes a colon) to avoid incorrect splitting.
        // This ensures the address is recognized properly and not misinterpreted during parsing.
        if(line.contains(BITCOIN_CASH_PREFIX)) {
            line = line.replace(BITCOIN_CASH_PREFIX, "");
        }
        String[] lineSplitted = SeparatorFormat.split(line);
        String address = lineSplitted[0];
        Coin amount = getCoinIfPossible(lineSplitted, DEFAULT_COIN);
        address = address.trim();
        if (address.isEmpty() || address.startsWith(IGNORE_LINE_PREFIX) || address.startsWith(ADDRESS_HEADER)) {
            return null;
        }
        
        // Riecoin: ScriptPubKey-style encoded address (hex with OP codes)
        {
            final String OP_DUP = "76";
            final String OP_HASH160 = "a9";
            final String OP_PUSH_20_BYTES = "14";
            final int length20Bytes = PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES;
            final String riecoinP2SHPrefix = OP_DUP + OP_HASH160 + OP_PUSH_20_BYTES;
            final int riecoinScriptPubKeyLengthHex = length20Bytes * 2 + riecoinP2SHPrefix.length();
            if (address.length() >= riecoinScriptPubKeyLengthHex && address.startsWith(riecoinP2SHPrefix)) {
                final String hash160Hex = address.substring(riecoinP2SHPrefix.length(), length20Bytes*2+riecoinP2SHPrefix.length());
                final ByteBuffer hash160 = keyUtility.byteBufferUtility().getByteBufferFromHex(hash160Hex);
                return new AddressToCoin(hash160, amount, AddressType.P2PKH_OR_P2SH);
            }
        }

        // Blockchair Multisig (P2MS) format is not supported
        if (
               address.startsWith("d-")
            || address.startsWith("m-")
            || address.startsWith("s-")
        ) {
            return null;
        }
        
        // BitCore WKH format (Base36-encoded hash160)
        if (address.startsWith("wkh_")) {
            // BitCore (WKH) is base36 encoded hash160
            String addressWKH = address.substring("wkh_".length());
            byte[] hash160 = new Base36Decoder().decodeBase36ToFixedLengthBytes(addressWKH, PublicKeyBytes.RIPEMD160_HASH_NUM_BYTES);
            ByteBuffer hash160AsByteBuffer = keyUtility.byteBufferUtility().byteArrayToByteBuffer(hash160);
            return new AddressToCoin(hash160AsByteBuffer, amount, AddressType.P2WPKH);
        }
        
        // Bech32 decoding (P2WPKH, P2WSH, P2TR)
        try {
            Bech32.Bech32Data bechData = Bech32.decode(address);
            
            // protected: bechData.witnessVersion();
            short witnessVersion = invokeProtectedMethod(bechData, "witnessVersion", Short.class);
            // protected: bechData.witnessProgram();
            byte[] witnessProgram = invokeProtectedMethod(bechData, "witnessProgram", byte[].class);
            
            switch (witnessVersion) {
                case WITNESS_VERSION_0:
                    if (witnessProgram.length == SegwitAddress.WITNESS_PROGRAM_LENGTH_PKH) {
                        ByteBuffer hash160 = keyUtility.byteBufferUtility().byteArrayToByteBuffer(witnessProgram);
                        return new AddressToCoin(hash160, amount, AddressType.P2WPKH); // P2WPKH supported
                    } else if (witnessProgram.length == SegwitAddress.WITNESS_PROGRAM_LENGTH_SH) {
                        byte[] scriptHash = witnessProgram;
                        return null; // P2WSH not supported
                    }
                    break;
                case WITNESS_VERSION_1:
                    if (witnessProgram.length == SegwitAddress.WITNESS_PROGRAM_LENGTH_TR) {
                        byte[] tweakedPublicKey = witnessProgram;
                        return null; // P2TR not supported
                    }
                    break;
                default:
                    // not supported
                    return null;
            }
        } catch (AddressFormatException | ReflectiveOperationException  e) {
            // Bech32 parsing or reflection failed; continue to next format
        }
        
        // ZCash or Peercoin with 2-byte version
        if (address.startsWith("p") || address.startsWith("t")) {
            // p: bitcoin cash / CashAddr (P2SH), this is a unique format and does not work
            // p: peercoin possible
            // t: ZCash has two version bytes
            try {
                AddressToCoin addressToCoin = parseBase58Address(address, VERSION_BYTES_ZCASH, CHECKSUM_BYTES_REGULAR, keyUtility);
                return new AddressToCoin(addressToCoin.hash160(), amount, addressToCoin.type());
            } catch (RuntimeException e) {
                // Fall through to other format checks
            }
        }
        
        try {
            // Bitcoin Cash 'q' prefix: convert to legacy address
            if (address.startsWith("q")) {
                byte[] payload = extractPKHFromBitcoinCashAddress(address);
                ByteBuffer hash160 = keyUtility.byteBufferUtility().byteArrayToByteBuffer(payload);
                return new AddressToCoin(hash160, amount, AddressType.P2PKH_OR_P2SH);
            }
        } catch (DecoderException e) {
            throw e;
        } catch (RuntimeException | ReflectiveOperationException e) {
            return null;
        }
        
        // Fallback: assume Base58 with 1-byte version prefix
        try {
            AddressToCoin addressToCoin = parseBase58Address(address, VERSION_BYTES_REGULAR, CHECKSUM_BYTES_REGULAR, keyUtility);
            return new AddressToCoin(addressToCoin.hash160(), amount, addressToCoin.type());
        } catch (AddressFormatException e) {
            return null;
        }
    }

    public static byte[] extractPKHFromBitcoinCashAddress(String address) throws ReflectiveOperationException {
        if (address.startsWith(BITCOIN_CASH_PREFIX)) {
            address = address.substring(BITCOIN_CASH_PREFIX.length());
        }
        byte[] decoded5 = decodeBech32CharsetToValues(address);
        byte[] decoded8 = decode5to8WithPadding(decoded5);
        // Extracts the payload portion from the decoded Bech32 data.
        // Skips the first byte (address type/version) and removes the last 6 bytes (checksum).
        // The result is the raw payload encoded in 5-bit format.
        byte[] payload = Arrays.copyOfRange(decoded8, 1, decoded8.length - 6);
        return payload;
    }
    
    @SuppressWarnings("unchecked")
    private <T> T invokeProtectedMethod(Bech32.Bech32Bytes bech32Bytes, String methodName, Class<T> returnType) throws ReflectiveOperationException  {
        Class<?> clazz = Bech32.Bech32Bytes.class;
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (T) method.invoke(bech32Bytes);
    }
    
    public static byte[] decodeBech32CharsetToValues(String base32String) {
        // Bech32 character set as defined in BIP-0173
        final String CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l";

        // Prepare lookup table for fast character-to-value resolution
        int[] lookup = new int[128];
        Arrays.fill(lookup, -1);
        for (int i = 0; i < CHARSET.length(); i++) {
            lookup[CHARSET.charAt(i)] = i;
        }

        // Decode characters to 5-bit values
        int len = base32String.length();
        byte[] result = new byte[len];
        for (int i = 0; i < len; i++) {
            char c = base32String.charAt(i);
            if (c >= 128 || lookup[c] == -1) {
                throw new IllegalArgumentException("Invalid character in Bech32 string: " + c);
            }
            result[i] = (byte) lookup[c];
        }

        return result;
    }
    
    /**
     * Return the data, fully-decoded with 8-bits per byte.
     * @return The data, fully-decoded as a byte array.
     */
    private static byte[] decode5to8(byte[] bytes) throws ReflectiveOperationException {
        return invokeConvertBitsStatic(bytes, 0, bytes.length, 5, 8, false);
    }
    
    private static byte[] decode5to8WithPadding(byte[] bytes) throws ReflectiveOperationException {
        return invokeConvertBitsStatic(bytes, 0, bytes.length, 5, 8, true);
    }
    
    private static byte[] encode8to5(byte[] data) throws ReflectiveOperationException {
        return invokeConvertBitsStatic(data, 0, data.length, 8, 5, true);
    }
    
    @SuppressWarnings("unchecked")
    private static byte[] invokeConvertBitsStatic(byte[] in, int inStart, int inLen, int fromBits, int toBits, boolean pad) throws ReflectiveOperationException {
        Method method = Bech32.class.getDeclaredMethod("convertBits", byte[].class, int.class, int.class, int.class, int.class, boolean.class);
        method.setAccessible(true);
        try {
            return (byte[]) method.invoke(null, in, inStart, inLen, fromBits, toBits, pad);
        } catch (ReflectiveOperationException e) {
            // rethrow AddressFormatException if it's the underlying cause
            Throwable cause = e.getCause();
            if (cause instanceof AddressFormatException) {
                throw (AddressFormatException) cause;
            }
            throw e;
        }
    }
    
    AddressToCoin parseBase58Address(String base58, int versionBytes, int checksumBytes, KeyUtility keyUtility) {
        byte[] decoded = Base58.decode(base58);
        
        final byte[] version;
        if (versionBytes > 0) {
            // copy version bytes
            version = new byte[versionBytes];
            System.arraycopy(decoded, 0, version, 0, version.length);
        } else {
            version = null;
        }
        
        byte[] hash160 = new byte[20];
        int storedBytes = Math.min(decoded.length - versionBytes, hash160.length);
        {
            // copy hash160
            System.arraycopy(decoded, versionBytes, hash160, 0, storedBytes);
        }
        
        final byte[] checksum;
        if (decoded.length >= versionBytes + hash160.length + checksumBytes) {
            checksum = new byte[checksumBytes];
            // copy cheksum
            System.arraycopy(decoded, versionBytes + storedBytes, checksum, 0, checksum.length);
            String checksumAsHex = org.apache.commons.codec.binary.Hex.encodeHexString(checksum);
        } else {
            checksum = null;
        }
        
        boolean checksumMatches = false;
        if (version != null && checksum != null) {
            byte[] payload = new byte[version.length + hash160.length];
            System.arraycopy(version, 0, payload, 0, version.length);
            System.arraycopy(hash160, 0, payload, version.length, hash160.length);

            byte[] firstHash = Hashing.sha256().hashBytes(payload).asBytes();
            byte[] secondHash = Hashing.sha256().hashBytes(firstHash).asBytes();
            byte[] calculatedChecksum = Arrays.copyOfRange(secondHash, 0, checksumBytes);

            checksumMatches = Arrays.equals(calculatedChecksum, checksum);
            if (false) {
                // TODO: For debugging only
                String versionAsHex = org.apache.commons.codec.binary.Hex.encodeHexString(version);
            }
        }

        if (false) {
            // TODO: For debugging only
            String decodedAsHex = org.apache.commons.codec.binary.Hex.encodeHexString(decoded);
            String hash160AsHex = org.apache.commons.codec.binary.Hex.encodeHexString(hash160);
        }

        ByteBuffer hash160AsByteBuffer = keyUtility.byteBufferUtility().byteArrayToByteBuffer(hash160);
        
        // fallback
        AddressType addressType = AddressType.P2PKH_OR_P2SH;

        AddressToCoin addressToCoin = new AddressToCoin(hash160AsByteBuffer, DEFAULT_COIN, addressType);
        return addressToCoin;
    }

    @NonNull
    private Coin getCoinIfPossible(@NonNull String[] lineSplitted, @NonNull Coin defaultValue) throws NumberFormatException {
        if (lineSplitted.length > 1) {
            String amountString = lineSplitted[1];
            try {
                return Coin.valueOf(Long.parseLong(amountString));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }
}
