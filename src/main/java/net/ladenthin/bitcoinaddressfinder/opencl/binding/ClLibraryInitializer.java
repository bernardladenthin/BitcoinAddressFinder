// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

/**
 * Loads the native OpenCL library into the binding.
 *
 * <p>Exists purely as a seam: the underlying binding performs library loading through static methods
 * that either succeed or throw {@link UnsatisfiedLinkError}, which makes the failure path impossible
 * to exercise on a machine where loading happens to work. Behind this interface,
 * {@link OpenClLibraryLoader} can be tested against a substitute that fails on demand.
 *
 * @see OpenClLibraryLoader
 */
public interface ClLibraryInitializer {

    /**
     * Loads the OpenCL library using the binding's default name resolution.
     *
     * @throws UnsatisfiedLinkError if the library cannot be located or loaded
     */
    void create();

    /**
     * Loads the OpenCL library from an explicitly named shared library.
     *
     * @param libraryName the platform-specific library name, e.g. {@code libOpenCL.so.1}
     * @throws UnsatisfiedLinkError if the library cannot be located or loaded
     */
    void create(String libraryName);

    /**
     * Reports whether the native library has already been loaded.
     *
     * @return {@code true} if a previous load succeeded and the binding is usable
     */
    boolean isCreated();
}
