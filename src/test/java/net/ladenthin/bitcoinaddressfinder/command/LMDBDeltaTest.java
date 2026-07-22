// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.command;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.LMDBBase;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationReadOnly;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBConfigurationWrite;
import net.ladenthin.bitcoinaddressfinder.configuration.CLMDBDelta;
import net.ladenthin.bitcoinaddressfinder.persistence.AddressIterable;
import net.ladenthin.bitcoinaddressfinder.persistence.PersistenceUtils;
import net.ladenthin.bitcoinaddressfinder.persistence.lmdb.LMDBPersistence;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import org.bitcoinj.base.Coin;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LMDBDelta}. The core merge is exercised in memory via {@code writeDelta} (which takes
 * an {@link Appendable}, not a file); one integration test verifies the config-driven command writes to
 * a real file.
 */
public class LMDBDeltaTest extends LMDBBase {

    // <editor-fold defaultstate="collapsed" desc="writeDelta core (in-memory, no file)">

    @Test
    public void writeDelta_emitsOthersMinusReference_dedupedAndSorted() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);
        byte[] h3 = hash160(3);
        byte[] h4 = hash160(4);

        AddressIterable reference = iterableOf(h1, h3);
        List<AddressIterable> others = List.of(iterableOf(h1, h2), iterableOf(h2, h4));

        StringBuilder out = new StringBuilder();
        long written = writeDelta(delta, reference, others, out);

        assertThat(written, is(equalTo(2L)));
        assertThat(lines(out), contains(keyUtility.toBase58(h2), keyUtility.toBase58(h4)));
    }

    @Test
    public void writeDelta_allKeysInReference_writesNothing() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);
        byte[] h3 = hash160(3);

        StringBuilder out = new StringBuilder();
        long written = writeDelta(delta, iterableOf(h1, h2, h3), List.of(iterableOf(h1, h2), iterableOf(h3)), out);

        assertThat(written, is(equalTo(0L)));
        assertThat(lines(out), is(empty()));
    }

    @Test
    public void writeDelta_emptyReference_writesAllOthersDeduped() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);

        StringBuilder out = new StringBuilder();
        long written = writeDelta(delta, iterableOf(), List.of(iterableOf(h1, h2), iterableOf(h1)), out);

        assertThat(written, is(equalTo(2L)));
        assertThat(lines(out), contains(keyUtility.toBase58(h1), keyUtility.toBase58(h2)));
    }

    @Test
    public void writeDelta_noOtherDatabases_writesNothing() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());

        StringBuilder out = new StringBuilder();
        long written = writeDelta(delta, iterableOf(hash160(1)), List.of(), out);

        assertThat(written, is(equalTo(0L)));
        assertThat(lines(out), is(empty()));
    }

    /**
     * hash160 keys are ordered by LMDB as unsigned bytes. A key with a high first byte (0x80) must sort
     * <em>after</em> 0x7F; a signed comparison would misorder them and wrongly emit a key that is in the
     * reference.
     */
    @Test
    public void writeDelta_usesUnsignedKeyOrder() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] low = hash160(0x7F); // 127
        byte[] high = hash160(0x80); // 128 (negative as a signed byte)

        StringBuilder out = new StringBuilder();
        // reference holds the high-byte key; the other holds both, ascending unsigned.
        long written = writeDelta(delta, iterableOf(high), List.of(iterableOf(low, high)), out);

        // Only `low` is new; `high` is in the reference. Signed ordering would also emit `high`.
        assertThat(written, is(equalTo(1L)));
        assertThat(lines(out), contains(keyUtility.toBase58(low)));
    }

    /**
     * An "other" key that sorts <em>below</em> every reference key must be emitted without advancing the
     * reference at all — the reference head is already greater than {@code smallest}, so the skip-over
     * loop never runs. Guards the "emit without touching the reference cursor" path.
     */
    @Test
    public void writeDelta_otherKeyBelowSmallestReferenceKey_emitted() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] low = hash160(1);
        byte[] high = hash160(9);

        StringBuilder out = new StringBuilder();
        // reference holds only the high key; the other holds a strictly smaller one.
        long written = writeDelta(delta, iterableOf(high), List.of(iterableOf(low)), out);

        assertThat(written, is(equalTo(1L)));
        assertThat(lines(out), contains(keyUtility.toBase58(low)));
    }

    /**
     * The reference may hold keys beyond the largest "other" key (it is the bigger database). Those
     * trailing keys must be skipped over and then simply ignored once the others are exhausted, while a
     * genuine delta between them is still emitted. Guards the "reference cursor outlives the others" path.
     */
    @Test
    public void writeDelta_referenceKeysBeyondOthersIgnored_deltaStillEmitted() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h2 = hash160(2);
        byte[] h3 = hash160(3);
        byte[] h4 = hash160(4);
        byte[] h5 = hash160(5);

        StringBuilder out = new StringBuilder();
        // reference = {h2, h3, h5}; other = {h2, h4}. Only h4 is new; h3 and the trailing h5 are ignored.
        long written = writeDelta(delta, iterableOf(h2, h3, h5), List.of(iterableOf(h2, h4)), out);

        assertThat(written, is(equalTo(1L)));
        assertThat(lines(out), contains(keyUtility.toBase58(h4)));
    }

    /**
     * Asymmetric case A: the "other" database (A) has many entries that are absent from the smaller
     * reference (B). Every A-only key is emitted, in order, and the single shared key is withheld.
     */
    @Test
    public void writeDelta_otherHasManyEntriesAbsentFromReference_allEmitted() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);
        byte[] shared = hash160(3);
        byte[] h4 = hash160(4);
        byte[] h5 = hash160(5);

        StringBuilder out = new StringBuilder();
        // reference (B) = {shared}; other (A) = {h1, h2, shared, h4, h5} → delta = {h1, h2, h4, h5}.
        long written = writeDelta(delta, iterableOf(shared), List.of(iterableOf(h1, h2, shared, h4, h5)), out);

        assertThat(written, is(equalTo(4L)));
        assertThat(
                lines(out),
                contains(
                        keyUtility.toBase58(h1),
                        keyUtility.toBase58(h2),
                        keyUtility.toBase58(h4),
                        keyUtility.toBase58(h5)));
    }

    /**
     * Asymmetric case B (the mirror image): the reference (B) is the larger database and the "other" (A)
     * is almost a subset — only its one extra key is emitted, even though many reference keys sort both
     * before and after it.
     */
    @Test
    public void writeDelta_referenceLargerThanOther_onlyExtraEmitted() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);
        byte[] shared = hash160(3);
        byte[] h4 = hash160(4);
        byte[] h5 = hash160(5);
        byte[] extra = hash160(6);

        StringBuilder out = new StringBuilder();
        // reference (B) = {h1, h2, shared, h4, h5}; other (A) = {shared, extra} → delta = {extra}.
        long written = writeDelta(delta, iterableOf(h1, h2, shared, h4, h5), List.of(iterableOf(shared, extra)), out);

        assertThat(written, is(equalTo(1L)));
        assertThat(lines(out), contains(keyUtility.toBase58(extra)));
    }

    /**
     * Two "other" databases with fully disjoint key sets must interleave correctly in the k-way merge —
     * every key from both is emitted exactly once, in global ascending order.
     */
    @Test
    public void writeDelta_disjointOtherDatabases_bothContributeInterleaved() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);
        byte[] h3 = hash160(3);
        byte[] h4 = hash160(4);

        StringBuilder out = new StringBuilder();
        // empty reference; other_1 = {h1, h4}, other_2 = {h2, h3} → delta = {h1, h2, h3, h4}.
        long written = writeDelta(delta, iterableOf(), List.of(iterableOf(h1, h4), iterableOf(h2, h3)), out);

        assertThat(written, is(equalTo(4L)));
        assertThat(
                lines(out),
                contains(
                        keyUtility.toBase58(h1),
                        keyUtility.toBase58(h2),
                        keyUtility.toBase58(h3),
                        keyUtility.toBase58(h4)));
    }

    /**
     * A delta address may live in several "other" databases at once. The provenance sink must list every
     * source that held it (comma-separated, in source order), and the per-source counts must tally each
     * source's delta hits.
     */
    @Test
    public void writeDelta_recordsPerAddressProvenanceAndPerSourceCounts() throws Exception {
        LMDBDelta delta = new LMDBDelta(new CLMDBDelta());
        byte[] h1 = hash160(1);
        byte[] h2 = hash160(2);
        byte[] h3 = hash160(3);

        AddressIterable reference = iterableOf(h3); // h3 is in the reference, so excluded from the delta
        List<AddressIterable> others = List.of(iterableOf(h1, h2, h3), iterableOf(h2));
        List<String> labels = List.of("dbA", "dbB");

        StringBuilder out = new StringBuilder();
        StringBuilder provenance = new StringBuilder();
        LMDBDelta.DeltaResult result = delta.writeDelta(reference, others, labels, out, provenance);

        assertThat(result.written(), is(equalTo(2L)));
        assertThat(lines(out), contains(keyUtility.toBase58(h1), keyUtility.toBase58(h2)));
        // h1 was only in dbA; h2 was in both dbA and dbB (listed in source order).
        assertThat(
                lines(provenance), contains(keyUtility.toBase58(h1) + "\tdbA", keyUtility.toBase58(h2) + "\tdbA,dbB"));
        long[] perSource = result.perSourceContained();
        assertThat(perSource[0], is(equalTo(2L))); // dbA contained h1 and h2
        assertThat(perSource[1], is(equalTo(1L))); // dbB contained h2
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="run() integration — writes to a real file">

    @Test
    public void run_writesReferenceComplementToFile() throws Exception {
        byte[] hA = hash160(1);
        byte[] hB = hash160(2);
        byte[] hC = hash160(3);
        File referenceDir = writeLmdb("reference", List.of(hA, hB));
        File otherDir = writeLmdb("other", List.of(hB, hC));
        File deltaFile = folder.resolve("delta.txt").toFile();

        CLMDBDelta config = new CLMDBDelta();
        config.referenceLmdbConfigurationReadOnly.lmdbDirectory = referenceDir.getAbsolutePath();
        CLMDBConfigurationReadOnly other = new CLMDBConfigurationReadOnly();
        other.lmdbDirectory = otherDir.getAbsolutePath();
        config.lmdbConfigurationReadOnlyList.add(other);
        config.deltaAddressesFile = deltaFile.getAbsolutePath();

        new LMDBDelta(config).run();

        // Only hC is in the other database but not the reference.
        assertThat(Files.readAllLines(deltaFile.toPath()), contains(keyUtility.toBase58(hC)));
        // The command also writes the per-address provenance sidecar next to the delta file.
        File provenanceFile = folder.resolve("delta.txt.provenance.tsv").toFile();
        assertThat(
                Files.readAllLines(provenanceFile.toPath()),
                contains(keyUtility.toBase58(hC) + "\t" + otherDir.getAbsolutePath()));
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="helpers">

    /** Calls the full {@code writeDelta} with generated source labels and no provenance sink. */
    private static long writeDelta(
            LMDBDelta delta, AddressIterable reference, List<? extends AddressIterable> others, Appendable out)
            throws IOException {
        List<String> labels = new ArrayList<>(others.size());
        for (int i = 0; i < others.size(); i++) {
            labels.add("db" + i);
        }
        return delta.writeDelta(reference, others, labels, out, null).written();
    }

    private static byte[] hash160(int firstByte) {
        byte[] hash160 = new byte[20];
        hash160[0] = (byte) firstByte;
        return hash160;
    }

    private static AddressIterable iterableOf(byte[]... hash160sAscending) {
        List<byte[]> list = List.of(hash160sAscending);
        return new AddressIterable() {
            @Override
            public Stream<ByteBuffer> addresses() {
                return list.stream().map(ByteBuffer::wrap);
            }

            @Override
            public long count() {
                return list.size();
            }
        };
    }

    private static List<String> lines(CharSequence out) {
        String text = out.toString();
        if (text.isEmpty()) {
            return List.of();
        }
        return Stream.of(text.split("\n", -1)).filter(line -> !line.isEmpty()).toList();
    }

    private File writeLmdb(String name, List<byte[]> hash160s) throws Exception {
        File directory = Files.createDirectory(folder.resolve("lmdb-" + name)).toFile();
        CLMDBConfigurationWrite write = new CLMDBConfigurationWrite();
        write.lmdbDirectory = directory.getAbsolutePath();
        write.useStaticAmount = true;
        write.staticAmount = 0L;
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        try (LMDBPersistence persistence = new LMDBPersistence(write, new PersistenceUtils(network))) {
            persistence.init();
            List<ByteBuffer> keys = new ArrayList<>();
            List<Coin> amounts = new ArrayList<>();
            for (byte[] hash160 : hash160s) {
                keys.add(byteBufferUtility.byteArrayToByteBuffer(hash160));
                amounts.add(Coin.ZERO);
            }
            persistence.putNewAmounts(keys, amounts);
        }
        return directory;
    }

    // </editor-fold>
}
