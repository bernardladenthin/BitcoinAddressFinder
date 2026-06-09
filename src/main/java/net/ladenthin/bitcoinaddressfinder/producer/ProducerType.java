// @formatter:off
// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
// @formatter:on
package net.ladenthin.bitcoinaddressfinder.producer;

/**
 * Coarse compute backend of a {@link Producer}, used to label per-producer runtime
 * statistics. The backend is a property of the producer class and is <b>not</b> derivable
 * from the {@code keyProducerId} (a single key producer can feed both a CPU and a GPU
 * producer at once).
 */
public enum ProducerType {
    /** CPU-based producers ({@code ProducerJava}, {@code ProducerJavaSecretsFiles}). */
    CPU,
    /** GPU-based producer ({@code ProducerOpenCL}). */
    GPU
}
