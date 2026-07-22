// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBDelta;
import net.ladenthin.bitcoinaddressfinder.core.Interruptable;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes every address that is present in at least one of the "other" databases but <b>not</b> in the
 * reference database to a plaintext file — one Base58 P2PKH ({@code 1...}) address per line.
 *
 * <h2>Cursor merge — near-zero memory</h2>
 * LMDB stores entries sorted by hash160, so each database can be streamed in key order via a single
 * cursor. The reference and all other databases are walked simultaneously as a k-way sorted merge: for
 * the smallest hash160 across the other cursors, it is emitted iff it does not appear in the reference,
 * then every cursor sitting on that key is advanced (which deduplicates across the other databases).
 * The output is streamed directly to the sink, so memory is just the per-cursor head — independent of
 * the delta size — and the whole pass is sequential I/O rather than billions of random lookups.
 *
 * <p>hash160 is re-encoded as a mainnet P2PKH {@code 1...} address. This is round-trip-safe: re-importing
 * decodes back to the same hash160 (the tool keys on hash160), even though a hash160 that originally came
 * from a P2SH/altcoin/bech32 address is written as {@code 1...}.
 */
@ToString
public class LMDBDelta implements Runnable, Interruptable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LMDBDelta.class);

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(true));

    private final CLMDBDelta lmdbDelta;

    // Lifecycle flag — uninformative in aggregate toString.
    @ToString.Exclude
    private final AtomicBoolean shouldRun = new AtomicBoolean(true);

    /**
     * Creates a new delta writer.
     *
     * @param lmdbDelta the delta configuration
     */
    public LMDBDelta(CLMDBDelta lmdbDelta) {
        this.lmdbDelta = lmdbDelta;
    }

    @Override
    public void run() {
        PersistenceUtils persistenceUtils = new PersistenceUtils(network);
        List<LMDBPersistence> opened = new ArrayList<>();
        try {
            LMDBPersistence reference =
                    new LMDBPersistence(lmdbDelta.referenceLmdbConfigurationReadOnly, persistenceUtils);
            reference.init();
            opened.add(reference);

            List<AddressIterable> others = new ArrayList<>(lmdbDelta.lmdbConfigurationReadOnlyList.size());
            for (CLMDBConfigurationReadOnly otherConfig : lmdbDelta.lmdbConfigurationReadOnlyList) {
                LMDBPersistence other = new LMDBPersistence(otherConfig, persistenceUtils);
                other.init();
                opened.add(other);
                others.add(other);
            }

            Path deltaFile = Path.of(lmdbDelta.deltaAddressesFile);
            LOGGER.info("Writing delta of " + others.size() + " database(s) against the reference to " + deltaFile
                    + " ...");
            long written;
            try (Writer writer = Files.newBufferedWriter(deltaFile, StandardCharsets.UTF_8)) {
                written = writeDelta(reference, others, writer);
            }
            LOGGER.info("... delta done: " + written + " address(es) written to " + deltaFile + ".");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write delta to " + lmdbDelta.deltaAddressesFile, e);
        } finally {
            for (LMDBPersistence persistence : opened) {
                persistence.close();
            }
        }
    }

    /**
     * Streams every hash160 present in one of {@code others} but not in {@code reference} to {@code out}
     * as a Base58 P2PKH address line, using a k-way sorted merge over the (key-ordered) cursors. Takes a
     * plain {@link Appendable} rather than a file so it is trivially unit-testable in memory.
     *
     * @param reference the reference address set (excluded from the delta)
     * @param others    the other address sets to diff against the reference
     * @param out       the sink to append one {@code 1...} address per delta hash160, newline-separated
     * @return the number of delta addresses written
     * @throws IOException if appending to {@code out} fails
     */
    @VisibleForTesting
    long writeDelta(AddressIterable reference, List<? extends AddressIterable> others, Appendable out)
            throws IOException {
        List<Stream<ByteBuffer>> openStreams = new ArrayList<>();
        try {
            Stream<ByteBuffer> referenceStream = reference.addresses();
            openStreams.add(referenceStream);
            Peeker referencePeeker = new Peeker(referenceStream.iterator());

            List<Peeker> otherPeekers = new ArrayList<>(others.size());
            for (AddressIterable other : others) {
                Stream<ByteBuffer> otherStream = other.addresses();
                openStreams.add(otherStream);
                otherPeekers.add(new Peeker(otherStream.iterator()));
            }

            long written = 0;
            while (shouldRun.get()) {
                byte @Nullable [] smallest = smallestHead(otherPeekers);
                if (smallest == null) {
                    break; // every other database exhausted
                }
                // Advance the reference to the first key >= smallest (its keys are also ascending).
                byte @Nullable [] referenceHead = referencePeeker.peek();
                while (referenceHead != null && Arrays.compareUnsigned(referenceHead, smallest) < 0) {
                    referencePeeker.advance();
                    referenceHead = referencePeeker.peek();
                }
                boolean inReference = referenceHead != null && Arrays.equals(referenceHead, smallest);
                if (!inReference) {
                    out.append(keyUtility.toBase58(smallest)).append('\n');
                    written++;
                }
                // Consume this key from every other database (deduplicates across them).
                for (Peeker peeker : otherPeekers) {
                    byte @Nullable [] head = peeker.peek();
                    if (head != null && Arrays.equals(head, smallest)) {
                        peeker.advance();
                    }
                }
            }
            return written;
        } finally {
            for (Stream<ByteBuffer> stream : openStreams) {
                stream.close();
            }
        }
    }

    /** Returns the unsigned-smallest current head across the peekers, or {@code null} if all are exhausted. */
    private byte @Nullable [] smallestHead(Iterable<Peeker> peekers) {
        byte[] smallest = null;
        for (Peeker peeker : peekers) {
            byte @Nullable [] head = peeker.peek();
            if (head != null && (smallest == null || Arrays.compareUnsigned(head, smallest) < 0)) {
                smallest = head;
            }
        }
        return smallest;
    }

    @Override
    public void interrupt() {
        shouldRun.set(false);
    }

    /**
     * One-element look-ahead over a key-ordered hash160 stream. Each head is copied out of the (possibly
     * cursor-owned, reused) {@link ByteBuffer} so it stays valid after the stream advances. Static nested:
     * holds no reference to the enclosing instance.
     */
    private static class Peeker {

        private final Iterator<ByteBuffer> iterator;

        private byte @Nullable [] head;

        Peeker(Iterator<ByteBuffer> iterator) {
            this.iterator = iterator;
            this.head = nextOrNull(iterator);
        }

        byte @Nullable [] peek() {
            return head;
        }

        void advance() {
            head = nextOrNull(iterator);
        }

        private static byte @Nullable [] nextOrNull(Iterator<ByteBuffer> iterator) {
            if (iterator.hasNext()) {
                ByteBuffer buffer = iterator.next();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return bytes;
            }
            return null;
        }
    }
}
