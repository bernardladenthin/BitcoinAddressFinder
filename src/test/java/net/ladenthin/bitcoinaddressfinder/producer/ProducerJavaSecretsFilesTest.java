// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.producer;

import static net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat.BIG_INTEGER;
import static net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat.SHA256;
import static net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat.STRING_SHA256;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.Matchers.startsWith;

import com.google.common.hash.Hashing;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.ladenthin.bitcoinaddressfinder.CommonDataProvider;
import net.ladenthin.bitcoinaddressfinder.MockConsumer;
import net.ladenthin.bitcoinaddressfinder.MockKeyProducer;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.UnknownSecretFormatException;
import net.ladenthin.bitcoinaddressfinder.io.SecretsFile;
import net.ladenthin.bitcoinaddressfinder.model.PublicKeyBytes;
import net.ladenthin.bitcoinaddressfinder.statistics.RuntimeStatistics;
import net.ladenthin.bitcoinaddressfinder.util.BitHelper;
import net.ladenthin.bitcoinaddressfinder.util.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.util.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.util.NetworkParameterFactory;
import nl.altindag.log.LogCaptor;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ProducerJavaSecretsFilesTest {

    @TempDir
    public Path folder;

    private static final Network network = new NetworkParameterFactory().getNetwork();
    private final KeyUtility keyUtility = new KeyUtility(network, new ByteBufferUtility(false));
    private final BitHelper bitHelper = new BitHelper();

    enum PrivateKey {
        TEST("test"),
        TEST_WITH_SPACE("test with space"),
        NUMBER_1337("1337"),
        NUMBER_73("73"),
        WITH_COMMENT("#WithComment"),
        WITH_SPECIAL_CHARACTER("schön, für schälen $%&?`´");

        private final String string;

        PrivateKey(String string) {
            this.string = string;
        }

        public String getSHA256() {
            byte[] sha256 =
                    Hashing.sha256().hashString(string, StandardCharsets.UTF_8).asBytes();
            return Hex.encodeHexString(sha256);
        }

        public BigInteger getBigInteger() {
            return new BigInteger(getSHA256(), 16);
        }

        public PublicKeyBytes getPublicKeyBytes() {
            return PublicKeyBytes.fromPrivate(getBigInteger());
        }

        public String getWiF() {
            return ECKey.fromPrivate(getBigInteger(), false).getPrivateKeyAsWiF(network);
        }

        public String getHex() {
            return ECKey.fromPrivate(getBigInteger(), false).getPrivateKeyAsHex();
        }
    }

    // <editor-fold defaultstate="collapsed" desc="initProducer">
    @Test
    public void initProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJava producerJava = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                mockConsumer,
                keyUtility,
                mockKeyProducer,
                bitHelper,
                new RuntimeStatistics());

        AbstractProducerTest.verifyInitProducer(producerJava);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="releaseProducer">
    @Test
    public void releaseProducer_configurationGiven_stateInitializedAndLogged() throws Exception {
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJava producerJava = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                mockConsumer,
                keyUtility,
                mockKeyProducer,
                bitHelper,
                new RuntimeStatistics());

        AbstractProducerTest.verifyReleaseProducer(producerJava);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="produceKeys">
    @Test
    public void produceKeys_noFileConfigured_noKeysCreated() throws Exception {
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJavaSecretsFiles producerJavaSecretsFiles = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                mockConsumer,
                keyUtility,
                mockKeyProducer,
                bitHelper,
                new RuntimeStatistics());

        // act
        producerJavaSecretsFiles.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(0)));
    }

    /** A file whose reading {@code interrupt()} stopped part way is not processed and must not be reported so. */
    @Test
    public void produceKeys_interruptedWhileReadingAFile_doesNotReportTheFileOrTheRunAsProcessed() throws Exception {
        File secretsFile = folder.resolve("secrets.txt").toFile();
        Files.writeString(secretsFile.toPath(), "one\ntwo\nthree\n", StandardCharsets.UTF_8);
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        cProducerJavaSecretsFiles.secretsFiles = List.of(secretsFile.getAbsolutePath());
        cProducerJavaSecretsFiles.secretFormat = STRING_SHA256;
        cProducerJavaSecretsFiles.batchSizeInBits = 0;
        AtomicReference<ProducerJavaSecretsFiles> producer = new AtomicReference<>();
        MockConsumer interruptingConsumer = new MockConsumer() {
            @Override
            public void consumeKeys(PublicKeyBytes[] publicKeyBytes, @Nullable BigInteger secretBase)
                    throws InterruptedException {
                super.consumeKeys(publicKeyBytes, secretBase);
                producer.get().interrupt();
            }
        };
        producer.set(new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                interruptingConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics()));

        try (LogCaptor logCaptor = LogCaptor.forClass(ProducerJavaSecretsFiles.class)) {
            producer.get().produceKeys();

            assertThat(interruptingConsumer.publicKeyBytesArrayList.size(), is(equalTo(1)));
            assertThat(logCaptor.getInfoLogs(), not(hasItem(startsWith("Finished processing"))));
            assertThat(logCaptor.getInfoLogs(), not(hasItem("All secrets files processed.")));
            assertThat(logCaptor.getWarnLogs(), hasItem(containsString("interrupted")));
        }
    }

    /**
     * An {@code interrupt()} that arrives after the loop checked the flag but before the next file's reader
     * is registered must still stop that file: it is not read at all.
     */
    @Test
    public void produceKeys_interruptBetweenFlagCheckAndReaderRegistration_doesNotReadTheFile() throws Exception {
        File secretsFile = folder.resolve("secrets.txt").toFile();
        Files.writeString(secretsFile.toPath(), "one\ntwo\nthree\n", StandardCharsets.UTF_8);
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        cProducerJavaSecretsFiles.secretsFiles = List.of(secretsFile.getAbsolutePath());
        cProducerJavaSecretsFiles.secretFormat = STRING_SHA256;
        cProducerJavaSecretsFiles.batchSizeInBits = 0;
        MockConsumer mockConsumer = new MockConsumer();
        ProducerJavaSecretsFiles producer =
                new ProducerJavaSecretsFiles(
                        cProducerJavaSecretsFiles,
                        mockConsumer,
                        keyUtility,
                        new MockKeyProducer(keyUtility, new Random(1)),
                        bitHelper,
                        new RuntimeStatistics()) {
                    @Override
                    SecretsFile createSecretsFile(File file) {
                        // interrupt() lands after the flag check, while no reader is registered yet
                        interrupt();
                        return super.createSecretsFile(file);
                    }
                };

        producer.produceKeys();

        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(0)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_CSECRET_FORMAT)
    public void produceKeys_filesConfigured_keysCreated(CSecretFormat cSecretFormat) throws Exception {
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        List<File> secretsFiles = createSecretsFiles(cSecretFormat);
        List<String> secretsFilesAsStringList =
                secretsFiles.stream().map(file -> file.getAbsolutePath()).collect(Collectors.toList());
        cProducerJavaSecretsFiles.secretsFiles = secretsFilesAsStringList;
        cProducerJavaSecretsFiles.secretFormat = cSecretFormat;
        cProducerJavaSecretsFiles.batchSizeInBits = 0;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJavaSecretsFiles producerJavaSecretsFiles = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                mockConsumer,
                keyUtility,
                mockKeyProducer,
                bitHelper,
                new RuntimeStatistics());

        // act
        producerJavaSecretsFiles.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(6)));

        assertThat(mockConsumer.publicKeyBytesArrayList.get(0).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(1).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(2).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(3).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(4).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(5).length, is(equalTo(1)));

        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PrivateKey.TEST.getPublicKeyBytes())));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(1)[0],
                is(equalTo(PrivateKey.TEST_WITH_SPACE.getPublicKeyBytes())));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(2)[0],
                is(equalTo(PrivateKey.NUMBER_1337.getPublicKeyBytes())));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(3)[0], is(equalTo(PrivateKey.NUMBER_73.getPublicKeyBytes())));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(4)[0],
                is(equalTo(PrivateKey.WITH_COMMENT.getPublicKeyBytes())));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(5)[0],
                is(equalTo(PrivateKey.WITH_SPECIAL_CHARACTER.getPublicKeyBytes())));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_CSECRET_FORMAT)
    public void produceKeys_filesConfiguredWithoutPrivateKeyIncrement_keysCreated(CSecretFormat cSecretFormat)
            throws Exception {
        // arrange — without batchUsePrivateKeyIncrement every secret goes through processSecrets
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        List<File> secretsFiles = createSecretsFiles(cSecretFormat);
        cProducerJavaSecretsFiles.secretsFiles =
                secretsFiles.stream().map(file -> file.getAbsolutePath()).collect(Collectors.toList());
        cProducerJavaSecretsFiles.secretFormat = cSecretFormat;
        cProducerJavaSecretsFiles.batchSizeInBits = 0;
        cProducerJavaSecretsFiles.batchUsePrivateKeyIncrement = false;

        MockConsumer mockConsumer = new MockConsumer();
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, new Random(1));
        ProducerJavaSecretsFiles producerJavaSecretsFiles = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                mockConsumer,
                keyUtility,
                mockKeyProducer,
                bitHelper,
                new RuntimeStatistics());

        // act
        producerJavaSecretsFiles.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(6)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PrivateKey.TEST.getPublicKeyBytes())));
        assertThat(
                mockConsumer.publicKeyBytesArrayList.get(5)[0],
                is(equalTo(PrivateKey.WITH_SPECIAL_CHARACTER.getPublicKeyBytes())));
    }

    /**
     * A secrets file read without {@code batchUsePrivateKeyIncrement} hands its lines to the producer
     * without the validation the other paths apply. Key 1 is a real key and must be checked (as a grid
     * checks it), not end in an ERROR; key 0 is no key and must not end in one either.
     */
    @Test
    public void produceKeys_withoutPrivateKeyIncrementKeysZeroAndOne_checkedAsInAGridWithoutAnError() throws Exception {
        // arrange
        File secretsFile = folder.resolve("edge-keys.txt").toFile();
        Files.write(secretsFile.toPath(), List.of("0", "1"));
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        cProducerJavaSecretsFiles.secretsFiles = List.of(secretsFile.getAbsolutePath());
        cProducerJavaSecretsFiles.secretFormat = CSecretFormat.BIG_INTEGER;
        cProducerJavaSecretsFiles.batchSizeInBits = 0;
        cProducerJavaSecretsFiles.batchUsePrivateKeyIncrement = false;
        MockConsumer mockConsumer = new MockConsumer();
        ProducerJavaSecretsFiles producerJavaSecretsFiles = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles,
                mockConsumer,
                keyUtility,
                new MockKeyProducer(keyUtility, new Random(1)),
                bitHelper,
                new RuntimeStatistics());

        try (LogCaptor logCaptor = LogCaptor.forClass(AbstractProducer.class)) {
            // act
            producerJavaSecretsFiles.produceKeys();

            // assert
            assertThat(logCaptor.getErrorLogs(), is(empty()));
        }
        assertThat(mockConsumer.publicKeyBytesArrayList, hasSize(2));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(sameInstance(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(1)[0], is(sameInstance(PublicKeyBytes.INVALID_KEY_ONE)));
        assertThat(producerJavaSecretsFiles.getLastFailure(), is(nullValue()));
    }
    // </editor-fold>

    private List<File> createSecretsFiles(CSecretFormat secretFormat) throws IOException {
        List<File> fileList = new ArrayList<>();
        {
            File secretsFile =
                    Files.createFile(folder.resolve("secretsFile0.txt")).toFile();
            fileList.add(secretsFile);
            PrivateKey[] secretsAsArray =
                    new PrivateKey[] {PrivateKey.TEST, PrivateKey.TEST_WITH_SPACE, PrivateKey.NUMBER_1337};
            List<PrivateKey> secretsAsList = Arrays.asList(secretsAsArray);
            fillSecretsFile(secretsFile, secretsAsList, secretFormat);
        }
        {
            File secretsFile =
                    Files.createFile(folder.resolve("secretsFile1.txt")).toFile();
            fileList.add(secretsFile);
            PrivateKey[] secretsAsArray =
                    new PrivateKey[] {PrivateKey.NUMBER_73, PrivateKey.WITH_COMMENT, PrivateKey.WITH_SPECIAL_CHARACTER};
            List<PrivateKey> secretsAsList = Arrays.asList(secretsAsArray);
            fillSecretsFile(secretsFile, secretsAsList, secretFormat);
        }
        return fileList;
    }

    private void fillSecretsFile(File file, Iterable<PrivateKey> secrets, CSecretFormat secretFormat)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        for (PrivateKey secret : secrets) {
            switch (secretFormat) {
                case STRING_SHA256:
                    sb.append(secret.string);
                    break;
                case BIG_INTEGER:
                    sb.append(secret.getBigInteger());
                    break;
                case SHA256:
                    sb.append(secret.getSHA256());
                    break;
                case DUMPED_PRIVATE_KEY:
                    sb.append(secret.getWiF());
                    break;
                default:
                    throw new UnknownSecretFormatException(secretFormat);
            }
            sb.append("\n");
        }
        String content = sb.toString();
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8.name());
    }
}
