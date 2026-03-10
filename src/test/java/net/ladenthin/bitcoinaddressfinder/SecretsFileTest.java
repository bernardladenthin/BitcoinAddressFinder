// @formatter:off
/**
 * Copyright 2026 Bernard Ladenthin bernard.ladenthin@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
// @formatter:on
package net.ladenthin.bitcoinaddressfinder;

import java.io.File;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.configuration.CSecretFormat;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link SecretsFile}.
 */
public class SecretsFileTest {

    private final Network network = new NetworkParameterFactory().getNetwork();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // <editor-fold defaultstate="collapsed" desc="processLine">
    @Test
    public void processLine_bigIntegerFormat_consumerReceivesExpectedSecret() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.BIG_INTEGER, readStatistic, captured::add);

        // act
        secretsFile.processLine("12345");

        // assert
        assertThat(captured.size(), is(equalTo(1)));
        assertThat(captured.get(0)[0], is(equalTo(BigInteger.valueOf(12345))));
    }

    @Test
    public void processLine_sha256Format_consumerReceivesExpectedSecret() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.SHA256, readStatistic, captured::add);

        // act
        secretsFile.processLine("000000000000000000000000000000000000000000000000000000000000000f");

        // assert
        assertThat(captured.size(), is(equalTo(1)));
        assertThat(captured.get(0)[0], is(equalTo(BigInteger.valueOf(15))));
    }

    @Test
    public void processLine_sha256Format_singleByteValue_consumerReceivesExpected() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.SHA256, readStatistic, captured::add);

        // act
        secretsFile.processLine("0000000000000000000000000000000000000000000000000000000000000001");

        // assert
        assertThat(captured.size(), is(equalTo(1)));
        assertThat(captured.get(0)[0], is(equalTo(BigInteger.ONE)));
    }

    @Test
    public void processLine_stringDoSha256Format_consumerReceivesHashOfInput() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.STRING_DO_SHA256, readStatistic, captured::add);
        // SHA256("test") = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        BigInteger expectedSecret = new BigInteger("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", 16);

        // act
        secretsFile.processLine("test");

        // assert
        assertThat(captured.size(), is(equalTo(1)));
        assertThat(captured.get(0)[0], is(equalTo(expectedSecret)));
    }

    @Test
    public void processLine_dumpedPrivateKeyFormat_consumerReceivesNonNullPositiveSecret() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.DUMPED_RIVATE_KEY, readStatistic, captured::add);
        // 5HueCGU8rMjxECyDialwujzZLVzfKkmvNoBm41ktLzmVqKS3khF is WIF for private key = 1 (mainnet, uncompressed)

        // act
        secretsFile.processLine("5HueCGU8rMjxECyDialwujzZLVzfKkmvNoBm41ktLzmVqKS3khF");

        // assert
        assertThat(captured.size(), is(equalTo(1)));
        assertThat(captured.get(0), is(notNullValue()));
        assertThat(captured.get(0)[0], is(greaterThan(BigInteger.ZERO)));
    }

    @Test
    public void processLine_dumpedPrivateKeyFormat_consumerReceivesPrivateKeyOne() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.DUMPED_RIVATE_KEY, readStatistic, captured::add);
        // 5HueCGU8rMjxECyDialwujzZLVzfKkmvNoBm41ktLzmVqKS3khF is WIF for private key = 1 (mainnet, uncompressed)

        // act
        secretsFile.processLine("5HueCGU8rMjxECyDialwujzZLVzfKkmvNoBm41ktLzmVqKS3khF");

        // assert
        assertThat(captured.get(0)[0], is(equalTo(BigInteger.ONE)));
    }

    @Test
    public void processLine_bigIntegerFormat_calledTwice_consumerCalledTwice() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.BIG_INTEGER, readStatistic, captured::add);

        // act
        secretsFile.processLine("1");
        secretsFile.processLine("2");

        // assert
        assertThat(captured.size(), is(equalTo(2)));
        assertThat(captured.get(0)[0], is(equalTo(BigInteger.ONE)));
        assertThat(captured.get(1)[0], is(equalTo(BigInteger.valueOf(2))));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="readFile">
    @Test
    public void readFile_fileWithTwoLines_consumerCalledTwice() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        FileUtils.writeStringToFile(file, "1\n2\n", StandardCharsets.UTF_8.name());
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.BIG_INTEGER, readStatistic, captured::add);

        // act
        secretsFile.readFile();

        // assert
        assertThat(captured.size(), is(equalTo(2)));
        assertThat(captured.get(0)[0], is(equalTo(BigInteger.ONE)));
        assertThat(captured.get(1)[0], is(equalTo(BigInteger.valueOf(2))));
    }

    @Test
    public void readFile_emptyFile_consumerNeverCalled() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.BIG_INTEGER, readStatistic, captured::add);

        // act
        secretsFile.readFile();

        // assert
        assertThat(captured.size(), is(equalTo(0)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interrupt">
    @Test
    public void interrupt_calledBeforeReadFile_noLinesProcessed() throws Exception {
        // arrange
        File file = folder.newFile("secrets.txt");
        FileUtils.writeStringToFile(file, "1\n2\n3\n", StandardCharsets.UTF_8.name());
        ReadStatistic readStatistic = new ReadStatistic();
        List<BigInteger[]> captured = new ArrayList<>();
        SecretsFile secretsFile = new SecretsFile(network, file, CSecretFormat.BIG_INTEGER, readStatistic, captured::add);

        // act
        secretsFile.interrupt();
        secretsFile.readFile();

        // assert
        assertThat(captured.size(), is(equalTo(0)));
    }
    // </editor-fold>
}
