// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
/**
 * Project-wide pure constant types.
 *
 * <p>This package is a true architectural leaf: it must have <b>zero
 * internal dependencies</b> on any other {@code net.ladenthin.bitcoinaddressfinder.*}
 * sub-package. Every other layer (configuration, eckey, persistence,
 * keyproducer, opencl, cli, the orchestration root) may freely depend on
 * it, which makes it the canonical home for invariants that need to be
 * referenced from multiple layers without inviting back-and-forth
 * cross-package dependencies.
 *
 * <p>Currently holds the secp256k1 spec constants ({@link
 * net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants}).
 * Additional pure-constant types may join later (e.g. a wire-format
 * radix constant, an OpenCL-pipeline byte-layout constant) provided they
 * remain dependency-leaf and represent project-wide invariants rather
 * than layer-specific tuning knobs.
 *
 * <p>The {@code argsPackageIsALeaf}-style ArchUnit invariant in {@code
 * BitcoinAddressFinderArchitectureTest} pins the leaf property as a
 * test failure rather than a code-review reminder.
 */
@org.jspecify.annotations.NullMarked
package net.ladenthin.bitcoinaddressfinder.constants;
