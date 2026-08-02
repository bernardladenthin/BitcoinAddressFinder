// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

/**
 * Assembles the default OpenCL binding stack.
 *
 * <p>The pieces of this package are deliberately small and separately injectable, which is what makes
 * them testable but also means a caller that just wants "the OpenCL API" would otherwise have to
 * repeat the same four-line wiring. This class is that wiring, kept in one place so a change to the
 * stack does not have to be chased through every call site.
 *
 * <p>Callers that need to substitute a part — a test faking the API, or the library loader — build
 * the stack themselves instead of going through here.
 */
public class OpenClBinding {

    /** Creates a new {@link OpenClBinding}. */
    public OpenClBinding() {}

    /**
     * Creates the default OpenCL API: LWJGL-backed, with error checking and the library-name
     * fallback that a stock Linux runtime needs.
     *
     * @return a ready-to-use OpenCL API
     */
    public ClApi createClApi() {
        return new LwjglClApi(
                new ClErrorChecker(new ClErrorCodeResolver()),
                new OpenClLibraryLoader(new LwjglClLibraryInitializer()));
    }

    /**
     * Convenience for call sites that hold no binding instance of their own, such as the no-argument
     * constructors that exist for backwards compatibility.
     *
     * @return a ready-to-use OpenCL API
     */
    public static ClApi defaultClApi() {
        return new OpenClBinding().createClApi();
    }
}
