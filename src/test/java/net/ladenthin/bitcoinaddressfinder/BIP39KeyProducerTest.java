// @formatter:off
/**
 * Copyright 2025 Bernard Ladenthin bernard.ladenthin@gmail.com
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

import java.time.Instant;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaRandom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.*;
import org.junit.Test;

import org.bitcoinj.crypto.DeterministicKey;
public class BIP39KeyProducerTest {

    @Test
    public void nextKey_fromKnownMnemonic_returnsExpectedPrivateKey() {
        // arrange
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String passphrase = "";
        String bip32Path = CKeyProducerJavaRandom.DEFAULT_BIP32_PATH;
        Instant creationTime = Instant.ofEpochSecond(0);

        BIP39KeyProducer producer = new BIP39KeyProducer(mnemonic, passphrase, bip32Path, creationTime);

        // act
        DeterministicKey key = producer.nextKey(); // M/44H/0H/0H/0/0

        // assert
        assertThat(key.getPrivateKeyAsHex(), is("e284129cc0922579a535bbf4d1a3b25773090d28c909bc0fed73b5e0222cc372"));
        assertThat(key.getPathAsString(), endsWith("/0"));
    }

    @Test
    public void nextBytes_calledTwice_returnsDeterministicByteArrays() {
        // arrange
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String passphrase = "";
        String bip32Path = CKeyProducerJavaRandom.DEFAULT_BIP32_PATH;
        Instant creationTime = Instant.ofEpochSecond(0);

        BIP39KeyProducer producer = new BIP39KeyProducer(mnemonic, passphrase, bip32Path, creationTime);

        byte[] bytes1 = new byte[16];
        byte[] bytes2 = new byte[16];

        // act
        producer.nextBytes(bytes1); // from /0
        producer.nextBytes(bytes2); // from /1

        // assert
        assertThat(bytes1, is(not(equalTo(bytes2))));
        assertThat(bytes1.length, is(16));
        assertThat(bytes2.length, is(16));
    }

    @Test
    public void appendPath_createsExtendedPath() {
        // arrange
        String path = CKeyProducerJavaRandom.DEFAULT_BIP32_PATH;
        int index = 5;

        // act
        var extended = BIP39KeyProducer.append(
            org.bitcoinj.crypto.HDPath.parsePath(path),
            new org.bitcoinj.crypto.ChildNumber(index, false)
        );

        // assert
        assertThat(extended.size(), is(5));
        assertThat(extended.get(4).num(), is(index));
    }
}