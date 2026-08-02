// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.core;

import java.math.BigInteger;

/**
 * One address found in the database, together with the key that produced it.
 *
 * <p><b>Contains the private key.</b> A hit is the thing this program exists to find, and anything
 * that forwards one — a log file, a broadcast to connected listeners — hands over full control of
 * the funds. Treat every sink for this type as a place where key material leaves the process.
 *
 * <p>Deliberately built from plain JDK types rather than the project's address value objects: this
 * package sits below the model layer, and keeping the payload primitive also keeps it stable as a
 * wire format for external consumers. The hash is carried hex-encoded rather than as a byte array so
 * the record keeps value semantics and needs no defensive copying.
 *
 * @param privateKey   the private key that derives this address
 * @param hash160Hex   the RIPEMD-160 hash of the public key, hex-encoded
 * @param address      the encoded address
 * @param compressed   whether the address was derived from the compressed public key
 * @param vanity       whether this is a vanity-pattern match rather than a database hit
 */
public record Hit(BigInteger privateKey, String hash160Hex, String address, boolean compressed, boolean vanity) {}
