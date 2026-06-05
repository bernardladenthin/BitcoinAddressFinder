// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.util.function.Consumer;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.bitcoinj.base.Network;
import org.jspecify.annotations.NonNull;

/**
 * Reads an address text file line by line and forwards each entry to address or unsupported consumers.
 *
 * <p>Lombok-generated equals/hashCode use only the {@code keyUtility} field; the two
 * {@link Consumer} fields are excluded because lambda equality is implementation-defined
 * (compiler-synthesized class identity), not value-shaped. {@code callSuper} stays at the
 * lombok.config default of {@code skip} — comparing parent {@code shouldRun}
 * (an {@link java.util.concurrent.atomic.AtomicBoolean} lifecycle flag) and
 * {@code readStatistic} (a counter bag mutated during reading) would make equality
 * dependent on transient runtime state, not on the reader's configuration.
 */
@ToString(callSuper = true)
@EqualsAndHashCode
public class AddressFile extends AbstractPlaintextFile {

    private final @NonNull KeyUtility keyUtility;

    // Lambda Consumer — toString is the implementation hash, not useful in logs;
    // equals is compiler-synthesized class identity, not value-shaped.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final @NonNull Consumer<AddressToCoin> addressConsumer;

    // Lambda Consumer — same reasoning as addressConsumer.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private final @NonNull Consumer<String> unsupportedConsumer;

    /**
     * Creates a new reader for the given address file.
     *
     * @param file                the file to read
     * @param readStatistic       statistic updated while reading
     * @param network             network used to decode addresses
     * @param addressConsumer     receives parsed {@link AddressToCoin} entries
     * @param unsupportedConsumer receives lines that could not be parsed
     */
    public AddressFile(
            @NonNull File file,
            ReadStatistic readStatistic,
            @NonNull Network network,
            @NonNull Consumer<AddressToCoin> addressConsumer,
            @NonNull Consumer<String> unsupportedConsumer) {
        super(file, readStatistic);
        this.addressConsumer = addressConsumer;
        this.unsupportedConsumer = unsupportedConsumer;
        keyUtility = new KeyUtility(network, new ByteBufferUtility(true));
    }

    @Override
    protected void processLine(String line) {
        AddressTxtLine addressTxtLine = new AddressTxtLine();
        try {
            AddressToCoin addressToCoin = addressTxtLine.fromLine(line, keyUtility);
            addressConsumer.accept(addressToCoin);
            readStatistic.successful++;
        } catch (AddressFormatNotAcceptedException e) {
            unsupportedConsumer.accept(line);
            readStatistic.incrementUnsupported(e.getReason());
        }
    }
}
