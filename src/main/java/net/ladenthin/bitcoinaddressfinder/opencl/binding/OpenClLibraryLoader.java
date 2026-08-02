// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads the native OpenCL library, falling back to alternative library names when the binding's
 * default resolution fails.
 *
 * <p>The fallback is not defensive padding; it fixes a reproducible failure. A stock Linux install
 * of an OpenCL driver provides the versioned SONAME {@code libOpenCL.so.1}, while the unversioned
 * {@code libOpenCL.so} that a plain-name lookup resolves is shipped by the separate development
 * package. An application that only ever asks for {@code OpenCL} therefore refuses to start on a
 * perfectly working machine unless a build-time package happens to be installed. Verified in a
 * Debian container carrying {@code pocl-opencl-icd} but no development package: the plain-name load
 * fails, the versioned load succeeds.
 *
 * <p>Loading is idempotent and cheap after the first success, so callers may invoke
 * {@link #ensureLoaded()} freely instead of tracking initialisation themselves.
 */
@ToString
public class OpenClLibraryLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenClLibraryLoader.class);

    /**
     * Versioned Linux SONAME of the OpenCL ICD loader. Present on every machine with a working
     * OpenCL runtime; the unversioned symlink next to it is not.
     */
    public static final String VERSIONED_LINUX_LIBRARY_NAME = "libOpenCL.so.1";

    /**
     * Library names tried, in order, after the binding's own default resolution has failed. Kept as
     * an ordered list rather than a set so the attempt order stays part of the contract.
     */
    private static final ImmutableList<String> FALLBACK_LIBRARY_NAMES = ImmutableList.of(VERSIONED_LINUX_LIBRARY_NAME);

    private final ClLibraryInitializer libraryInitializer;

    /** Guards against repeating the load once it has succeeded through this loader. */
    private boolean loaded = false;

    /**
     * Creates a new loader.
     *
     * @param libraryInitializer performs the actual native library load
     */
    public OpenClLibraryLoader(ClLibraryInitializer libraryInitializer) {
        this.libraryInitializer = libraryInitializer;
    }

    /**
     * Returns the library names tried after default resolution fails.
     *
     * @return the ordered fallback library names
     */
    public List<String> fallbackLibraryNames() {
        return FALLBACK_LIBRARY_NAMES;
    }

    /**
     * Ensures the native OpenCL library is loaded, trying the default name first and then every
     * fallback name in turn. Does nothing if the library is already loaded.
     *
     * @throws OpenClLibraryUnavailableException if no candidate could be loaded
     */
    public void ensureLoaded() {
        if (loaded || libraryInitializer.isCreated()) {
            return;
        }

        @Nullable UnsatisfiedLinkError lastFailure = null;
        try {
            libraryInitializer.create();
            loaded = true;
            return;
        } catch (UnsatisfiedLinkError e) {
            lastFailure = e;
            LOGGER.debug("Default OpenCL library name did not resolve, trying fallback names", e);
        }

        for (String libraryName : FALLBACK_LIBRARY_NAMES) {
            try {
                libraryInitializer.create(libraryName);
                loaded = true;
                LOGGER.info("Loaded the OpenCL library under the fallback name {}", libraryName);
                return;
            } catch (UnsatisfiedLinkError e) {
                lastFailure = e;
                LOGGER.debug("OpenCL library name {} did not resolve", libraryName, e);
            }
        }

        throw new OpenClLibraryUnavailableException(
                "Unable to load the native OpenCL library. Tried the default name and " + FALLBACK_LIBRARY_NAMES
                        + ". Install an OpenCL driver (ICD) for your device.",
                lastFailure);
    }

    /**
     * Reports whether the native OpenCL library is loaded and usable.
     *
     * @return {@code true} if the library has been loaded
     */
    public boolean isLoaded() {
        return loaded || libraryInitializer.isCreated();
    }
}
