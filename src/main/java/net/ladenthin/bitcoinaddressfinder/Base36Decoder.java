// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.math.BigInteger;

public class Base36Decoder {
    /**
     * Decodes a Base36-encoded string (e.g., from a WKH address) into a fixed-length byte array.
     * <p>
     * This method is typically used to convert a Base36-encoded hash representation (such as a RIPEMD-160 hash)
     * into a normalized {@code byte[]} of exactly {@code hashLength} bytes.
     * It handles BigInteger's sign-extension by trimming or zero-padding the result as needed.
     * <p>
     * If the decoded byte array is shorter than {@code hashLength}, the result is left-padded with zeros.
     * If the decoded array is longer, only the least-significant {@code hashLength} bytes are kept.
     *
     * @param base36Encoded  the Base36-encoded input string (e.g., from a "wkh_" address)
     * @param hashLength  the desired length of the output byte array (e.g., 20 for hash160)
     * @return a normalized byte array of length {@code hashLength}, representing the decoded hash
     * @throws NumberFormatException if the input string is not valid Base36
     */
    public byte[] decodeBase36ToFixedLengthBytes(String base36Encoded, final int hashLength) {
        byte[] raw = new BigInteger(base36Encoded, 36).toByteArray();
        byte[] hash160 = new byte[hashLength];
        int srcPos = Math.max(0, raw.length - hashLength);
        int destPos = hashLength - (raw.length - srcPos);
        int length = raw.length - srcPos;
        System.arraycopy(raw, srcPos, hash160, destPos, length);
        return hash160;
    }
}
