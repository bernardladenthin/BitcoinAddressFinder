// @formatter:off
/**
 * Copyright 2020 Bernard Ladenthin bernard.ladenthin@gmail.com
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
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJava;
import net.ladenthin.bitcoinaddressfinder.configuration.CProducerJavaBrainwallet;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ProducerJavaBrainwalletTest {
    
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();
    
    protected final NetworkParameters networkParameters = MainNetParams.get();
    protected final KeyUtility keyUtility = new KeyUtility(networkParameters, new ByteBufferUtility(false));
    
    @Test
    public void produceKeys_noFileConfigured_noKeysCreated() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducerJavaBrainwallet cProducerJavaBrainwallet = new CProducerJavaBrainwallet();

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        ProducerJavaBrainwallet producerJavaBrainwallet = new ProducerJavaBrainwallet(cProducerJavaBrainwallet, shouldRun, mockConsumer, keyUtility, random);

        // act
        producerJavaBrainwallet.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(0)));
    }
    
    @Test
    public void produceKeys_filesConfigured_noKeysCreated() throws IOException, InterruptedException {
        final AtomicBoolean shouldRun = new AtomicBoolean(true);

        CProducerJavaBrainwallet cProducerJavaBrainwallet = new CProducerJavaBrainwallet();
        List<File> brainwalletFiles = createBrainwalletFiles();
        List<String> brainwalletFilesAsStringList = brainwalletFiles.stream().map(file -> file.getAbsolutePath()).collect(Collectors.toList());
        cProducerJavaBrainwallet.brainwalletStringsFiles = brainwalletFilesAsStringList;

        MockConsumer mockConsumer = new MockConsumer();
        Random random = new Random(1);
        ProducerJavaBrainwallet producerJavaBrainwallet = new ProducerJavaBrainwallet(cProducerJavaBrainwallet, shouldRun, mockConsumer, keyUtility, random);

        // act
        producerJavaBrainwallet.produceKeys();

        // assert
        assertThat(mockConsumer.publicKeyBytesArrayList.size(), is(equalTo(6)));
        
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(1).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(2).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(3).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(4).length, is(equalTo(1)));
        assertThat(mockConsumer.publicKeyBytesArrayList.get(5).length, is(equalTo(1)));
        
        // test
        assertThat(mockConsumer.publicKeyBytesArrayList.get(0)[0], is(equalTo(PublicKeyBytes.fromPrivate(new BigInteger("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", 16)))));
        // test with space
        assertThat(mockConsumer.publicKeyBytesArrayList.get(1)[0], is(equalTo(PublicKeyBytes.fromPrivate(new BigInteger("58472980a1d3449939eadc2652370972d5007fa9c059ce84fb3ab98f544e4a08", 16)))));
        // 1337
        assertThat(mockConsumer.publicKeyBytesArrayList.get(2)[0], is(equalTo(PublicKeyBytes.fromPrivate(new BigInteger("5db1fee4b5703808c48078a76768b155b421b210c0761cd6a5d223f4d99f1eaa", 16)))));
        // 73
        assertThat(mockConsumer.publicKeyBytesArrayList.get(3)[0], is(equalTo(PublicKeyBytes.fromPrivate(new BigInteger("96061e92f58e4bdcdee73df36183fe3ac64747c81c26f6c83aada8d2aabb1864", 16)))));
        // #WithComment
        assertThat(mockConsumer.publicKeyBytesArrayList.get(4)[0], is(equalTo(PublicKeyBytes.fromPrivate(new BigInteger("7f6cf61e56d81a250918077b480bf568a525e2514d45e380701e68fb5ee3433c", 16)))));
        // schön, für schälen $%&?`´
        assertThat(mockConsumer.publicKeyBytesArrayList.get(5)[0], is(equalTo(PublicKeyBytes.fromPrivate(new BigInteger("338bd263dc9597858422ed759811aa251bbaae903a40a74dba1017959ae5fd34", 16)))));
    }
    
    private List<File> createBrainwalletFiles() throws IOException {
        List<File> fileList = new ArrayList<>();
        {
            File brainwallet = folder.newFile("brainwallet0.txt");
            fileList.add(brainwallet);
            String[] brainwallets = new String[] {
                "test",
                "test with space",
                "1337"
            };
            List<String> brainwalletsAsList = Arrays.asList(brainwallets);
            fillBrainwalletFile(brainwallet, brainwalletsAsList);
        }
        {
            File brainwallet = folder.newFile("brainwallet1.txt");
            fileList.add(brainwallet);
            String[] brainwallets = new String[] {
                "73",
                "#WithComment",
                "schön, für schälen $%&?`´"
            };
            List<String> brainwalletsAsList = Arrays.asList(brainwallets);
            fillBrainwalletFile(brainwallet, brainwalletsAsList);
        }
        return fileList;
    }

    private void fillBrainwalletFile(File file, List<String> brainwallets) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String brainwallet : brainwallets) {
            sb.append(brainwallet);
            sb.append("\n");
        }
        String content = sb.toString();
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8.name());
    }
}
