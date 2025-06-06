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
