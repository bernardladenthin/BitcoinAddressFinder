// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat.BIG_INTEGER;
import static net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat.SHA256;
import static net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat.STRING_DO_SHA256;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

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
import java.util.stream.Collectors;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaSecretsFiles;
import net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat;
import net.ladenthin.bitcoinaddressfinder.configuration.UnknownSecretFormatException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.base.Network;
import org.bitcoinj.crypto.ECKey;
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
                cProducerJavaSecretsFiles, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
                cProducerJavaSecretsFiles, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
                cProducerJavaSecretsFiles, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

        // act
        producerJavaSecretsFiles.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(0)));
    }

    @ParameterizedTest
    @MethodSource(CommonDataProvider.DATA_PROVIDER_CSECRET_FORMAT)
    public void produceKeys_filesConfigured_keysCreated(CSecretFormat cSecretFormat) throws Exception {
        CProducerJavaSecretsFiles cProducerJavaSecretsFiles = new CProducerJavaSecretsFiles();
        List<File> secretsFiles = createSecretsFiles(cSecretFormat);
        List<String> secretsFilesAsStringList =
                secretsFiles.stream().map(file -> file.getAbsolutePath()).collect(Collectors.toList());
        cProducerJavaSecretsFiles.files = secretsFilesAsStringList;
        cProducerJavaSecretsFiles.secretFormat = cSecretFormat;
        cProducerJavaSecretsFiles.batchSizeInBits = 0;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        MockKeyProducer mockKeyProducer = new MockKeyProducer(keyUtility, random);
        ProducerJavaSecretsFiles producerJavaSecretsFiles = new ProducerJavaSecretsFiles(
                cProducerJavaSecretsFiles, mockConsumer, keyUtility, mockKeyProducer, bitHelper);

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
                case STRING_DO_SHA256:
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
