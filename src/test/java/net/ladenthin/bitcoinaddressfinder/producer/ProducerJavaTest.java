// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants.INVALID_PRIVATE_KEY_REPLACEMENT;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.ToStringTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.constants.Secp256k1Constants;
import net.ladenthin.bitcoinaddressfinder.keyproducer.KeyProducerJavaIncremental;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ProducerJavaTest {

    @TempDir
    public Path folder;

    private final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        AbstractProducerTest.verifyInitProducer(producerJava);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="produceKeys">
    /**
     * With explicit keys ({@code batchUsePrivateKeyIncrement=false}) an incremental range whose end
     * falls inside a batch is checked up to and including {@code endPrivateKey}: the last batch is
     * shorter instead of failing, which used to leave up to {@code overallWorkSize - 1} keys of the
     * range unchecked.
     */
    @Test
    public void produceKeys_incrementalRangeEndingInsideABatch_checksEveryKeyUpToTheEnd() throws Exception {
        // arrange
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchSizeInBits = 2;
        cProducerJava.batchUsePrivateKeyIncrement = false;
        CKeyProducerJavaIncremental cKeyProducer = new CKeyProducerJavaIncremental();
        cKeyProducer.startPrivateKey = "2";
        cKeyProducer.endPrivateKey = "7";
        MockConsumer mockConsumer = new MockConsumer();
        ProducerJava producerJava = new ProducerJava(
                cProducerJava,
                mockConsumer,
                keyUtility,
                new KeyProducerJavaIncremental(cKeyProducer, keyUtility, bitHelper),
                bitHelper,
                new RuntimeStatistics());

        // act: [2, 3, 4, 5], then the rest [6, 7], then the range is exhausted
        producerJava.produceKeys();
        producerJava.produceKeys();
        producerJava.produceKeys();

        // assert
        List<BigInteger> checked = new ArrayList<>();
        for (PublicKeyBytes[] batch : mockConsumer.publicKeyBytesArrayList) {
            for (PublicKeyBytes publicKeyBytes : batch) {
                checked.add(publicKeyBytes.getSecretKey());
            }
        }
        assertThat(
                checked,
                contains(
                        BigInteger.valueOf(2),
                        BigInteger.valueOf(3),
                        BigInteger.valueOf(4),
                        BigInteger.valueOf(5),
                        BigInteger.valueOf(6),
                        BigInteger.valueOf(7)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="processSecrets">
    /**
     * Keys outside the valid range reach {@code processSecrets} from a secrets file read without
     * {@code batchUsePrivateKeyIncrement}, which applies no validation of its own. They are treated as
     * a grid treats them: {@link PublicKeyBytes#INVALID_KEY_ONE} in their place. Before, key 0, 1 and
     * {@code n + 1} failed in {@code fromPrivate} with an ERROR, {@code n} with an
     * {@code ArrayIndexOutOfBoundsException}, and a value above {@code n + 1} was silently computed as
     * the key {@code k − n} while reported as {@code k}.
     *
     * @param secret the key to process
     */
    @ParameterizedTest
    @MethodSource("keysOutsideTheValidRange")
    public void processSecrets_keyOutsideTheValidRange_isReplacedByKeyOneAsInAGrid(BigInteger secret) {
        // arrange
        MockConsumer mockConsumer = new MockConsumer();
        ProducerJava producerJava = new ProducerJava(
                new CProducerJava(),
                mockConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            // act
            producerJava.processSecrets(new BigInteger[] {secret});

            // assert
            assertThat(logCaptor.getErrorLogs(), is(empty()));
        }
        assertThat(mockConsumer.publicKeyBytesArrayList, hasSize(1));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(sameInstance(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(producerJava.getLastFailure(), is(nullValue()));
    }

    static Stream<BigInteger> keysOutsideTheValidRange() {
        final BigInteger n = Secp256k1Constants.MAX_PRIVATE_KEY;
        return Stream.of(
                BigInteger.ZERO,
                BigInteger.ONE,
                n,
                n.add(BigInteger.ONE),
                n.add(BigInteger.valueOf(5)),
                BigInteger.TWO.pow(256).subtract(BigInteger.ONE));
    }

    @Test
    public void processSecrets_validKeys_areComputedUnchanged() {
        // arrange
        MockConsumer mockConsumer = new MockConsumer();
        ProducerJava producerJava = new ProducerJava(
                new CProducerJava(),
                mockConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());
        final BigInteger lowest = Secp256k1Constants.MIN_VALID_PRIVATE_KEY;
        final BigInteger highest = Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE);

        // act
        producerJava.processSecrets(new BigInteger[] {lowest, highest});

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList, hasSize(1));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.fromPrivate(lowest))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[1], is(equalTo(PublicKeyBytes.fromPrivate(highest))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="toString">
    @ToStringTest
    @Test
    public void toString_whenCalled_containsClassNameAndConfig() {
        // After migrating ProducerJava to Lombok @ToString(callSuper = true) the output
        // becomes "ProducerJava(super=AbstractProducer(...), producerJava=CProducerJava(...))".
        // The old identity-style "ProducerJava@<hex>" form is gone — replaced by a
        // structured state snapshot. We assert on the class name + the producerJava field
        // since identity is no longer in the contract.
        CProducerJava cProducerJava = new CProducerJava();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        String toStringOutput = producerJava.toString();

        assertThat(toStringOutput, not(emptyOrNullString()));
        assertThat(toStringOutput, containsString("ProducerJava("));
        assertThat(toStringOutput, containsString("producerJava=CProducerJava("));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="releaseProducer">
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        AbstractProducerTest.verifyReleaseProducer(producerJava);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="createGrid">
    /**
     * The secp256k1 group order n is not a private key: n·G is the point at infinity, which has no
     * public key. A grid that reaches n must mark it invalid instead of deriving a key from it.
     */
    @Test
    public void createGrid_gridReachesTheGroupOrder_marksItInvalidAndKeepsTheKeyBelow() {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchSizeInBits = 2;
        MockConsumer mockConsumer = new MockConsumer();
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());
        // n ends in 0x41, so n - 1 is aligned to the 4-key grid: n - 1, n, n + 1, n + 2
        BigInteger groupOrderMinusOne = Secp256k1Constants.MAX_PRIVATE_KEY.subtract(BigInteger.ONE);

        PublicKeyBytes[] grid = producerJava.createGrid(groupOrderMinusOne);

        assertThat(grid[0], is(equalTo(PublicKeyBytes.fromPrivate(groupOrderMinusOne))));
        assertThat(grid[1], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(grid[2], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(grid[3], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="produceKeys">
    @Test
    public void produceKeys_BatchSizeInBitsEqualsKeyMaxNumBits_noExceptionThrown() throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchUsePrivateKeyIncrement = true;
        cProducerJava.batchSizeInBits = 2;

        MockConsumer mockConsumer = new MockConsumer();
        int maximumBitLength = 2;
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(1)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0).length,
                is(equalTo(bitHelper.convertBitsToSize(cProducerJava.batchSizeInBits))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[1], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[2],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(2)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[3],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
    }

    @Test
    public void produceKeys_KeyMaxNumBitsLowerThanBatchSizeInBits_produceBatchSizeInBitsNevertheless()
            throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchUsePrivateKeyIncrement = true;
        cProducerJava.batchSizeInBits = 4;

        MockConsumer mockConsumer = new MockConsumer();
        int maximumBitLength = 3;
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(1)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0).length,
                is(equalTo(bitHelper.convertBitsToSize(cProducerJava.batchSizeInBits))));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[1], is(equalTo(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[2],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(2)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[3],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[4],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(4)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[5],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(5)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[6],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(6)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[7],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(7)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[8],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(8)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[9],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(9)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[10],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(10)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[11],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(11)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[12],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(12)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[13],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(13)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[14],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(14)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[15],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(15)))));
    }

    @Test
    public void produceKeys_privateKeyMaxNumBitsIsVeryLowAndProduceReplacedKeys_keysEqualsReplacement()
            throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchUsePrivateKeyIncrement = true;
        cProducerJava.batchSizeInBits = 1;

        MockConsumer mockConsumer = new MockConsumer();
        int maximumBitLength = 2;
        Random random = new Random(0);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(1)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0).length,
                is(equalTo(bitHelper.convertBitsToSize(cProducerJava.batchSizeInBits))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[0],
                is(equalTo(PublicKeyBytes.fromPrivate(INVALID_PRIVATE_KEY_REPLACEMENT))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[1],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
    }

    @Test
    public void produceKeys_privateKeyMaxNumBitsIsLowAndProduceReplacedKeys_keysEqualsReplacement() throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchUsePrivateKeyIncrement = false;
        cProducerJava.batchSizeInBits = 4;

        MockConsumer mockConsumer = new MockConsumer();
        int maximumBitLength = 3;
        Random random = new Random(0);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(1)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0).length,
                is(equalTo(bitHelper.convertBitsToSize(cProducerJava.batchSizeInBits))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[0],
                is(equalTo(PublicKeyBytes.fromPrivate(INVALID_PRIVATE_KEY_REPLACEMENT))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[1],
                is(equalTo(PublicKeyBytes.fromPrivate(INVALID_PRIVATE_KEY_REPLACEMENT))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[2],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(2)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[3],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(6)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[4],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(6)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[5],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(2)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[6],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(7)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[7],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[8],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(6)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[9],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(5)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[10],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[11],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[12],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(3)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[13],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(5)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[14],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(7)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[15],
                is(equalTo(PublicKeyBytes.fromPrivate(INVALID_PRIVATE_KEY_REPLACEMENT))));
    }

    @Test
    public void produceKeys_SomeBitRanges_consumerContainsData() throws Exception {
        CProducerJava cProducerJava = new CProducerJava();
        cProducerJava.batchUsePrivateKeyIncrement = true;
        cProducerJava.batchSizeInBits = 3;

        MockConsumer mockConsumer = new MockConsumer();
        int maximumBitLength = 6;
        Random random = new Random(2);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random, maximumBitLength);
        ProducerJava producerJava = new ProducerJava(
                cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        // act
        producerJava.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(1)));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0).length,
                is(equalTo(bitHelper.convertBitsToSize(cProducerJava.batchSizeInBits))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[0],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(56)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[1],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(57)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[2],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(58)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[3],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(59)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[4],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(60)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[5],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(61)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[6],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(62)))));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(0)[7],
                is(equalTo(PublicKeyBytes.fromPrivate(BigInteger.valueOf(63)))));
    }
    // </editor-fold>

}
