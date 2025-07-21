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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import static net.ladenthin.bitcoinaddressfinder.PublicKeyBytes.INVALID_PRIVATE_KEY_REPLACEMENT;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;

import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.bouncycastle.util.encoders.Hex;
import org.jspecify.annotations.NonNull;

/**
 * https://stackoverflow.com/questions/5399798/byte-array-and-int-conversion-in-java/11419863
 * https://stackoverflow.com/questions/21087651/how-to-efficiently-change-endianess-of-byte-array-in-java
 * https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa
 */
public class KeyUtility {

    @NonNull
    public final Network network;
    public final ByteBufferUtility byteBufferUtility;

    public KeyUtility(Network network, ByteBufferUtility byteBufferUtility) {
        this.network = network;
        this.byteBufferUtility = byteBufferUtility;
    }
    
    public BigInteger killBits(BigInteger bigInteger, BigInteger killBits) {
        return bigInteger.andNot(killBits);
    }
    
    /**
     * Calculates the maximum allowed private key value that can safely be used as a base
     * for grid-based key generation without exceeding the secp256k1 private key limit.
     *
     * This is necessary for chunked or grid-based generation where the base key is incremented
     * by up to 2^batchSizeInBits - 1.
     *
     * @param batchSizeInBits The number of bits used for batch size (i.e., the number of keys generated in one grid chunk).
     *                        Must be in the range [0, {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BITS}].
     * @return The maximum base private key that will not overflow when incremented by the grid.
     */
    public static BigInteger getMaxPrivateKeyForBatchSize(int batchSizeInBits) {
        if (batchSizeInBits < 0 || batchSizeInBits > PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS) {
            throw new IllegalArgumentException("batchSizeInBits must be between 0 and " + PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS);
        }

        // 2^batchSizeInBits represents the maximum offset (grid size)
        BigInteger maxOffset = BigInteger.ONE.shiftLeft(batchSizeInBits);

        // Subtract maxOffset - 1 to ensure that baseKey + (2^bits - 1) ≤ MAX_PRIVATE_KEY
        BigInteger maxSafeKey = PublicKeyBytes.MAX_PRIVATE_KEY.subtract(maxOffset).add(BigInteger.ONE);

        if (maxSafeKey.signum() < 0) {
            throw new IllegalStateException("batchSizeInBits too large; no valid private keys remain.");
        }

        return maxSafeKey;
    }
    
    public static  boolean isInvalidWithBatchSize(BigInteger privateKeyBase, BigInteger maxPrivateKeyForBatchSize) {
        return privateKeyBase.compareTo(maxPrivateKeyForBatchSize) > 0;
    }
    
    public static boolean isOutsidePrivateKeyRange(BigInteger secret) {
        return secret.compareTo(PublicKeyBytes.MIN_VALID_PRIVATE_KEY) < 0 
            || secret.compareTo(PublicKeyBytes.MAX_PRIVATE_KEY) > 0;

    }
    
    public static BigInteger returnValidPrivateKey(BigInteger secret) {
        if (isOutsidePrivateKeyRange(secret)) {
            return INVALID_PRIVATE_KEY_REPLACEMENT;
        }
        return secret;
    }
    
    public static void replaceInvalidPrivateKeys(BigInteger[] secrets) {
        for (int i = 0; i < secrets.length; i++) {
            secrets[i] = returnValidPrivateKey(secrets[i]);
        }
    }
    
    /**
     * Require networkParameters.
     */
    public ByteBuffer getHash160ByteBufferFromBase58String(String base58) {
        LegacyAddress address = LegacyAddress.fromBase58(base58, network);
        byte[] hash160 = address.getHash();
        return byteBufferUtility.byteArrayToByteBuffer(hash160);
    }

    public String toBase58(byte[] hash160) {
        LegacyAddress address = LegacyAddress.fromPubKeyHash(network, hash160);
        return address.toBase58();
    }

    public BigInteger createSecret(int maximumBitLength, Random random) {
        BigInteger secret = new BigInteger(maximumBitLength, random);
        return secret;
    }

    public ECKey createECKey(BigInteger bi, boolean compressed) {
        return ECKey.fromPrivate(bi, compressed);
    }

    public String createKeyDetails(ECKey key) throws MnemonicException.MnemonicLengthException {
        BigInteger privateKeyBigInteger = key.getPrivKey();
        byte[] privateKeyBytes = key.getPrivKeyBytes();
        String privateKeyHex = key.getPrivateKeyAsHex();
        String privateKeyAsWiF = key.getPrivateKeyAsWiF(network);

        byte[] hash160 = key.getPubKeyHash();
        String publicKeyHash160Hex = Hex.toHexString(hash160);
        String publicKeyHash160Base58 = toBase58(hash160);

        String logprivateKeyBigInteger = "privateKeyBigInteger: [" + privateKeyBigInteger + "]";
        String logprivateKeyBytes = "privateKeyBytes: [" + Arrays.toString(privateKeyBytes) + "]";
        String logprivateKeyHex = "privateKeyHex: [" + privateKeyHex + "]";
        String logWiF = "WiF: [" + privateKeyAsWiF + "]";
        String logPublicKeyAsHex = "publicKeyAsHex: [" + key.getPublicKeyAsHex() + "]";
        String logPublicKeyHash160 = "publicKeyHash160Hex: [" + publicKeyHash160Hex + "]";
        String logPublicKeyHash160Base58 = "publicKeyHash160Base58: [" + publicKeyHash160Base58 + "]";
        String logCompressed = "Compressed: [" + key.isCompressed() + "]";
        String logMnemonic = createMnemonics(privateKeyBytes);

        String space = " ";
        return logprivateKeyBigInteger + space + logprivateKeyBytes + space + logprivateKeyHex + space + logWiF + space + logPublicKeyAsHex + space + logPublicKeyHash160 + space + logPublicKeyHash160Base58 + space + logCompressed + space + logMnemonic;
    }

    public String createMnemonics(byte[] privateKeyBytes) {
        StringBuilder logMnemonic = new StringBuilder("Mnemonic:");
        for (BIP39Wordlist wordList : BIP39Wordlist.values()) {
            try {
                MnemonicCode mnemonicCode = new MnemonicCode(wordList.getWordListStream(), null);
                List<String> mnemonics = mnemonicCode.toMnemonic(privateKeyBytes);
                logMnemonic.append(" ");
                logMnemonic.append(wordList.name());
                logMnemonic.append(": [");
                boolean first = true;
                for(String mnemonic : mnemonics) {
                    if (!first) {
                        logMnemonic.append(wordList.getSeparator());
                    }
                    logMnemonic.append(mnemonic);
                    first = false;
                }
                logMnemonic.append("]");
            } catch (IOException | IllegalArgumentException ex) {
                throw new RuntimeException(ex);
            }
        }
        return logMnemonic.toString();
    }

    // <editor-fold defaultstate="collapsed" desc="ByteBuffer LegacyAddress conversion">
    public ByteBuffer addressToByteBuffer(LegacyAddress address) {
        ByteBuffer byteBuffer = byteBufferUtility.byteArrayToByteBuffer(address.getHash());
        return byteBuffer;
    }

    /**
     * Require networkParameters.
     */
    public LegacyAddress byteBufferToAddress(ByteBuffer byteBuffer) {
        return LegacyAddress.fromPubKeyHash(network, byteBufferUtility.byteBufferToBytes(byteBuffer));
    }
    // </editor-fold>
    
    public BigInteger[] createSecrets(int overallWorkSize, boolean returnStartSecretOnly, int privateKeyMaxNumBits, SecretSupplier supplier) throws NoMoreSecretsAvailableException {
        int length = returnStartSecretOnly ? 1 : overallWorkSize;
        BigInteger[] secrets = new BigInteger[length];
        for (int i = 0; i < secrets.length; i++) {
            secrets[i] = supplier.nextSecret(privateKeyMaxNumBits);
        }
        return secrets;
    }

    @Deprecated
    static int byteArrayToInt(byte[] b) {
        return byteArrayToInt(b, 0);
    }

    @Deprecated
    static int byteArrayToInt(byte[] b, int offsetByteArray) {
        return b[3 + offsetByteArray] & 0xFF
                | (b[2 + offsetByteArray] & 0xFF) << 8
                | (b[1 + offsetByteArray] & 0xFF) << 16
                | (b[0 + offsetByteArray] & 0xFF) << 24;
    }

    @Deprecated
    static void byteArrayToIntArray(byte[] b, int offsetByteArray, int[] i, int offsetIntArray) {
        i[offsetIntArray] = byteArrayToInt(b, offsetByteArray);
    }

    @Deprecated
    static byte[] intToByteArray(int a) {
        byte[] b = new byte[4];
        intToByteArray(a, b, 0);
        return b;
    }

    @Deprecated
    static void intToByteArray(int a, byte[] b, int offset) {
        b[0 + offset] = (byte) ((a >> 24) & 0xFF);
        b[1 + offset] = (byte) ((a >> 16) & 0xFF);
        b[2 + offset] = (byte) ((a >> 8) & 0xFF);
        b[3 + offset] = (byte) (a & 0xFF);
    }

    @Deprecated
    private static void swapIntBytes(byte[] bytes) {
        assert bytes.length % 4 == 0;
        for (int i = 0; i < bytes.length; i += 4) {
            // swap 0 and 3
            byte tmp = bytes[i];
            bytes[i] = bytes[i + 3];
            bytes[i + 3] = tmp;
            // swap 1 and 2
            byte tmp2 = bytes[i + 1];
            bytes[i + 1] = bytes[i + 2];
            bytes[i + 2] = tmp2;
        }
    }

    /**
     * Converts a BigInteger to a fixed-length 64-character (32-byte) lowercase
     * hex string. Preserves leading zeros, which are otherwise dropped by
     * BigInteger.toByteArray().
     *
     * @param value The BigInteger to convert.
     * @return A 64-character hex string representing the value as a 256-bit
     * number.
     */
    public String bigIntegerToFixedLengthHex(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] result = new byte[PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES];
        int srcPos = Math.max(0, raw.length - result.length);
        int length = Math.min(result.length, raw.length);
        System.arraycopy(raw, srcPos, result, result.length - length, length);
        return Hex.toHexString(result);
    }

    /**
     * Converts a 32-byte array into a positive BigInteger, preserving leading
     * zeros. The array must be exactly
     * {@link PublicKeyBytes#PRIVATE_KEY_MAX_NUM_BYTES} bytes.
     *
     * @param buffer a 32-byte array representing the unsigned big-endian
     * integer
     * @return a positive BigInteger constructed from the buffer
     */
    public BigInteger bigIntegerFromUnsignedByteArray(byte[] buffer) {
        if (buffer.length != PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
            throw new IllegalArgumentException("Expected buffer of length " + PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES);
        }
        return new BigInteger(1, buffer);
    }
}
