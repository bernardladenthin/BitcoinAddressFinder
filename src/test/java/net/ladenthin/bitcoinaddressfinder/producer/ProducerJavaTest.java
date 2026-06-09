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
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.ToStringTest;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        AbstractProducerTest.verifyInitProducer(producerJava);
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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

        AbstractProducerTest.verifyReleaseProducer(producerJava);
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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

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
        ProducerJava producerJava =
                new ProducerJava(cProducerJava, mockConsumer, keyUtility, mockKeyProducer, bitHelper, new RuntimeStatistics());

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
