// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0

/**
 * Thin, instance-based seam over the OpenCL binding library.
 *
 * <p>Everything in this package exists to keep the rest of {@code opencl} free of any direct
 * dependency on a concrete binding. It provides three things the raw binding does not:
 *
 * <ul>
 *   <li><b>Typed handles.</b> LWJGL represents every OpenCL object as a bare {@code long}, so a
 *       buffer, a kernel and a command queue are the same type to the compiler. The handle records
 *       here restore the distinction that JOCL's {@code cl_mem} / {@code cl_kernel} classes provided.
 *   <li><b>Explicit error checking.</b> LWJGL returns {@code cl_int} error codes and never throws on
 *       its own, so an unchecked call fails silently. {@link
 *       net.ladenthin.bitcoinaddressfinder.opencl.binding.ClErrorChecker} turns a non-success code
 *       into an attributable {@link
 *       net.ladenthin.bitcoinaddressfinder.opencl.binding.OpenClCallFailedException}.
 *   <li><b>A mockable surface.</b> The binding's own API is entirely static. Wrapping it behind
 *       instance methods is what allows the layers above to be tested with mocks and spies instead of
 *       requiring a real OpenCL device.
 * </ul>
 *
 * <p>JSpecify {@code @NullMarked}: see {@link net.ladenthin.bitcoinaddressfinder} for the convention.
 */
@NullMarked
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import org.jspecify.annotations.NullMarked;
