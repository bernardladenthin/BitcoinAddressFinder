// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.opencl.binding;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/**
 * Tests {@link OpenClLibraryLoader}.
 *
 * <p>These tests exist because of a concrete failure observed while preparing the migration off
 * JOCL. On a stock Linux install the OpenCL runtime package ships only the versioned SONAME
 * {@code libOpenCL.so.1}; the unversioned {@code libOpenCL.so} symlink comes from the separate
 * development package. JOCL never noticed, because its own JNI shim is linked against the SONAME.
 * LWJGL resolves the library by plain name and therefore fails with
 * {@code UnsatisfiedLinkError: Failed to locate library: libOpenCL.so} on any machine that has a
 * working OpenCL driver but no development package installed. Reproduced in a Debian container with
 * {@code pocl-opencl-icd} before this loader existed.
 *
 * <p>The fallback chain is what keeps the application runnable there, so it is tested against a
 * mocked initializer rather than against a real driver: the failure path cannot be provoked on a
 * machine where the default name happens to resolve.
 */
class OpenClLibraryLoaderTest {

    /** Message of the failure LWJGL raises when the library name cannot be resolved. */
    private static final String LINK_ERROR_MESSAGE = "Failed to locate library: libOpenCL.so";

    /**
     * Creates the failure LWJGL raises when a library name cannot be resolved. A factory rather than
     * a shared constant: a {@link Throwable} kept in a static field accumulates a stack trace from
     * whichever test happened to create it first.
     *
     * @return a fresh link error carrying {@link #LINK_ERROR_MESSAGE}
     */
    private static UnsatisfiedLinkError linkError() {
        return new UnsatisfiedLinkError(LINK_ERROR_MESSAGE);
    }

    // <editor-fold defaultstate="collapsed" desc="ensureLoaded">
    @Test
    void ensureLoaded_defaultNameResolves_noFallbackAttempted() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act
        loader.ensureLoaded();

        // assert
        verify(initializer, times(1)).create();
        verify(initializer, never()).create(anyString());
    }

    @Test
    void ensureLoaded_defaultNameFails_versionedSonameUsedAsFallback() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        doThrow(linkError()).when(initializer).create();
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act
        loader.ensureLoaded();

        // assert
        verify(initializer).create(eq(OpenClLibraryLoader.VERSIONED_LINUX_LIBRARY_NAME));
    }

    @Test
    void ensureLoaded_everyCandidateFails_throwsException() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        doThrow(linkError()).when(initializer).create();
        doThrow(linkError()).when(initializer).create(anyString());
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act, assert
        assertThrows(OpenClLibraryUnavailableException.class, loader::ensureLoaded);
    }

    @Test
    void ensureLoaded_everyCandidateFails_exceptionNamesTheAttemptedCandidate() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        doThrow(linkError()).when(initializer).create();
        doThrow(linkError()).when(initializer).create(anyString());
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act
        final OpenClLibraryUnavailableException e =
                assertThrows(OpenClLibraryUnavailableException.class, loader::ensureLoaded);

        // assert
        assertThat(e.getMessage(), containsString(OpenClLibraryLoader.VERSIONED_LINUX_LIBRARY_NAME));
    }

    @Test
    void ensureLoaded_everyCandidateFails_originalLinkErrorIsRetainedAsCause() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        doThrow(linkError()).when(initializer).create();
        doThrow(linkError()).when(initializer).create(anyString());
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act
        final OpenClLibraryUnavailableException e =
                assertThrows(OpenClLibraryUnavailableException.class, loader::ensureLoaded);

        // pre-assert
        assertThat(e.getCause(), is(notNullValue()));

        // assert
        assertThat(e.getCause().getMessage(), containsString("libOpenCL.so"));
    }

    @Test
    void ensureLoaded_calledTwice_initializesOnlyOnce() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act
        loader.ensureLoaded();
        loader.ensureLoaded();

        // assert
        verify(initializer, times(1)).create();
    }

    @Test
    void ensureLoaded_initializerAlreadyReportsCreated_noInitializationAttempted() {
        // arrange
        final ClLibraryInitializer initializer = mock(ClLibraryInitializer.class);
        when(initializer.isCreated()).thenReturn(true);
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(initializer);

        // act
        assertDoesNotThrow(loader::ensureLoaded);

        // assert
        verify(initializer, never()).create();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="fallbackLibraryNames">
    @Test
    void fallbackLibraryNames_whenCalled_containsVersionedLinuxSoname() {
        // arrange
        final OpenClLibraryLoader loader = new OpenClLibraryLoader(mock(ClLibraryInitializer.class));

        // act, assert
        assertThat(loader.fallbackLibraryNames(), hasItem(OpenClLibraryLoader.VERSIONED_LINUX_LIBRARY_NAME));
    }
    // </editor-fold>
}
