// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.bitcoinj.crypto.MnemonicCode;
import org.bouncycastle.util.encoders.Hex;
import org.jspecify.annotations.NonNull;

/**
 * Cryptographic helpers for the {@link Network} (address derivation, key conversion).
 *
 * <p>References:
 * <ul>
 *   <li>https://stackoverflow.com/questions/5399798/byte-array-and-int-conversion-in-java/11419863</li>
 *   <li>https://stackoverflow.com/questions/21087651/how-to-efficiently-change-endianess-of-byte-array-in-java</li>
 *   <li>https://stackoverflow.com/questions/7619058/convert-a-byte-array-to-integer-in-java-and-vice-versa</li>
 * </ul>
 *
 * @param network           the Bitcoin/altcoin network used for address derivation
 * @param byteBufferUtility helper for {@link ByteBuffer} conversions
 */
public record KeyUtility(@NonNull Network network, @NonNull ByteBufferUtility byteBufferUtility) {

    /**
     * Clears the bits set in {@code killBits} from {@code bigInteger}.
     *
     * @param bigInteger the source value
     * @param killBits   the bit mask of bits to clear
     * @return {@code bigInteger AND NOT killBits}
     */
    public BigInteger killBits(BigInteger bigInteger, BigInteger killBits) {
        return bigInteger.andNot(killBits);
    }

    /**
     * Decodes a Base58 address and returns its hash160 as a {@link ByteBuffer}.
     * <p>Requires {@code networkParameters}.
     *
     * @param base58 the Base58-encoded address
     * @return the hash160 wrapped in a {@link ByteBuffer}
     */
    public ByteBuffer getHash160ByteBufferFromBase58String(String base58) {
        LegacyAddress address = LegacyAddress.fromBase58(base58, network);
        byte[] hash160 = address.getHash();
        return byteBufferUtility.byteArrayToByteBuffer(hash160);
    }

    /**
     * Encodes a hash160 as a Base58 legacy address.
     *
     * @param hash160 the 20-byte address hash
     * @return the Base58-encoded legacy address
     */
    public String toBase58(byte[] hash160) {
        LegacyAddress address = LegacyAddress.fromPubKeyHash(network, hash160);
        return address.toBase58();
    }

    /**
     * Draws a non-negative random {@link BigInteger} of up to {@code maximumBitLength} bits.
     *
     * @param maximumBitLength the maximum bit length of the resulting secret
     * @param random           the random number generator
     * @return a random secret
     */
    public BigInteger createSecret(int maximumBitLength, Random random) {
        return new BigInteger(maximumBitLength, random);
    }

    /**
     * Creates an {@link ECKey} from a private-key {@link BigInteger}.
     *
     * @param bi         the private-key value
     * @param compressed whether to use the compressed public-key representation
     * @return the constructed {@link ECKey}
     */
    public ECKey createECKey(BigInteger bi, boolean compressed) {
        return ECKey.fromPrivate(bi, compressed);
    }

    /**
     * Builds a verbose, single-line description of all relevant key fields, including BIP39 mnemonics.
     *
     * @param key the key to describe
     * @return the formatted log line
     */
    public String createKeyDetails(ECKey key) {
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
        return logprivateKeyBigInteger
                + space
                + logprivateKeyBytes
                + space
                + logprivateKeyHex
                + space
                + logWiF
                + space
                + logPublicKeyAsHex
                + space
                + logPublicKeyHash160
                + space
                + logPublicKeyHash160Base58
                + space
                + logCompressed
                + space
                + logMnemonic;
    }

    /**
     * Generates BIP39 mnemonics from the given private-key bytes for every supported wordlist.
     *
     * @param privateKeyBytes the raw private-key bytes
     * @return a string containing the mnemonic for each {@link BIP39Wordlist}
     */
    public String createMnemonics(byte[] privateKeyBytes) {
        StringBuilder logMnemonic = new StringBuilder("Mnemonic:");
        for (BIP39Wordlist wordList : BIP39Wordlist.values()) {
            try {
                MnemonicCode mnemonicCode = new MnemonicCode(wordList.getWordListStream(), null);
                List<String> mnemonics = mnemonicCode.toMnemonic(privateKeyBytes);
                logMnemonic.append(' ');
                logMnemonic.append(wordList.name());
                logMnemonic.append(": [");
                boolean first = true;
                for (String mnemonic : mnemonics) {
                    if (!first) {
                        logMnemonic.append(wordList.getSeparator());
                    }
                    logMnemonic.append(mnemonic);
                    first = false;
                }
                logMnemonic.append(']');
            } catch (IOException | IllegalArgumentException ex) {
                throw new RuntimeException(ex);
            }
        }
        return logMnemonic.toString();
    }

    // <editor-fold defaultstate="collapsed" desc="ByteBuffer LegacyAddress conversion">
    /**
     * Converts a {@link LegacyAddress} into a {@link ByteBuffer} containing its hash160.
     *
     * @param address the legacy address
     * @return a buffer with the address' hash160
     */
    public ByteBuffer addressToByteBuffer(LegacyAddress address) {
        return byteBufferUtility.byteArrayToByteBuffer(address.getHash());
    }

    /**
     * Reads a hash160 from {@code byteBuffer} and constructs a {@link LegacyAddress}.
     * <p>Requires {@code networkParameters}.
     *
     * @param byteBuffer the buffer containing the hash160
     * @return the reconstructed legacy address
     */
    public LegacyAddress byteBufferToAddress(ByteBuffer byteBuffer) {
        return LegacyAddress.fromPubKeyHash(network, byteBufferUtility.byteBufferToBytes(byteBuffer));
    }
    // </editor-fold>

    /**
     * Creates a batch of secrets via the given {@link SecretSupplier}.
     *
     * @param overallWorkSize        the requested number of secrets
     * @param returnStartSecretOnly  if {@code true} only one secret is generated
     * @param privateKeyMaxNumBits   maximum bit length of each secret
     * @param supplier               the supplier providing concrete secrets
     * @return the generated secrets
     * @throws NoMoreSecretsAvailableException if the supplier cannot satisfy the request
     */
    public BigInteger[] createSecrets(
            int overallWorkSize, boolean returnStartSecretOnly, int privateKeyMaxNumBits, SecretSupplier supplier)
            throws NoMoreSecretsAvailableException {
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
                | (b[offsetByteArray] & 0xFF) << 24;
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
        b[offset] = (byte) ((a >> 24) & 0xFF);
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
     *               integer
     * @return a positive BigInteger constructed from the buffer
     */
    public BigInteger bigIntegerFromUnsignedByteArray(byte[] buffer) {
        if (buffer.length != PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES) {
            throw new IllegalArgumentException("Expected buffer of length " + PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BYTES);
        }
        return new BigInteger(1, buffer);
    }
}
