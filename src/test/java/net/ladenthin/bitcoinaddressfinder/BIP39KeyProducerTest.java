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

import net.ladenthin.bitcoinaddressfinder.keyproducer.NoMoreSecretsAvailableException;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.time.Instant;
import java.util.List;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import net.ladenthin.bitcoinaddressfinder.configuration.CKeyProducerJavaBip39;
import static org.hamcrest.Matchers.*;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.HDKeyDerivation;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.wallet.DeterministicSeed;
import static org.junit.Assert.fail;
import org.junit.runner.RunWith;

@RunWith(DataProviderRunner.class)
public class BIP39KeyProducerTest {

    @Test
    public void nextKey_givenKnownMnemonic_returnsExpectedPrivateKey() {
        // arrange
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String passphrase = "";
        String bip32Path = CKeyProducerJavaBip39.DEFAULT_BIP32_PATH;
        Instant creationTime = Instant.ofEpochSecond(0);
        boolean hardened = false;

        BIP39KeyProducer producer = new BIP39KeyProducer(mnemonic, passphrase, bip32Path, creationTime, hardened);

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
        String bip32Path = CKeyProducerJavaBip39.DEFAULT_BIP32_PATH;
        Instant creationTime = Instant.ofEpochSecond(0);
        boolean hardened = false;

        BIP39KeyProducer producer = new BIP39KeyProducer(mnemonic, passphrase, bip32Path, creationTime, hardened);

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
    public void appendPath_givenBasePathAndIndex_returnsExtendedHDPath() {
        // arrange
        String path = CKeyProducerJavaBip39.DEFAULT_BIP32_PATH;
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
    
    @Test
    public void bip39Vector_givenEnglishMnemonic_returnsExpectedSeedAndXprv() throws Exception {
        // Arrange
        String passphrase = "TREZOR";
        String entropyHex = "00000000000000000000000000000000";
        String expectedMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String expectedSeedHex = "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04";
        String expectedXprv = "xprv9s21ZrQH143K3h3fDYiay8mocZ3afhfULfb5GX8kCBdno77K4HiA15Tg23wpbeF1pLfs1c5SPmYHrEpTuuRhxMwvKDwqdKiGJS9XFKzUsAF";

        byte[] entropy = Hex.decodeHex(entropyHex);
        List<String> mnemonic = MnemonicCode.INSTANCE.toMnemonic(entropy);
        assertThat(String.join(" ", mnemonic), is(expectedMnemonic));

        DeterministicSeed seed = DeterministicSeed.ofMnemonic(expectedMnemonic, passphrase);
        assertThat(Hex.encodeHexString(seed.getSeedBytes()), is(expectedSeedHex));

        // Act
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());

        // Assert
        assertThat(masterKey.serializePrivB58(MainNetParams.get().network()), is(expectedXprv));
    }
    
    @Test
    @UseDataProvider(value = BIP39DataProvider.DATA_PROVIDER_BIP39_TEST_VECTORS, location = BIP39DataProvider.class)
    public void bip39Vector_givenTestVector_returnsExpectedSeedAndXprvForLanguage(String language, String entropyHex, String mnemonicStr, String passphrase, String expectedSeedHex, String expectedXprv) throws Exception {
        // Arrange
        byte[] entropy = Hex.decodeHex(entropyHex);
        
        final BIP39Wordlist wordList = BIP39Wordlist.fromLanguageName(language);
        
        MnemonicCode mnemonicCode = new MnemonicCode(
            wordList.getWordListStream(),
            null
        );

        List<String> mnemonic = mnemonicCode.toMnemonic(entropy);
        
        String normalizedMnemonic = Normalizer.normalize(mnemonicStr, Form.NFKD);
        String normalizedPassphrase = Normalizer.normalize(passphrase, Form.NFKD);

        // Assert mnemonic
        assertThat("Language: " + language, String.join(wordList.getSeparator(), mnemonic), is(mnemonicStr));

        // Generate seed
        DeterministicSeed seed = DeterministicSeed.ofMnemonic(normalizedMnemonic, normalizedPassphrase);

        // Assert seed bytes
        assertThat("Language: " + language, Hex.encodeHexString(seed.getSeedBytes()), is(expectedSeedHex));

        // Generate master key from seed
        DeterministicKey masterKey = HDKeyDerivation.createMasterPrivateKey(seed.getSeedBytes());

        // Assert xprv
        assertThat("Language: " + language, masterKey.serializePrivB58(MainNetParams.get().network()), is(expectedXprv));
    }
    
    @Test(expected = NoMoreSecretsAvailableException.class)
    public void nextKey_counterOverflow_throwsException() {
        // arrange
        String mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";
        String passphrase = "";
        String bip32Path = "M/44H/0H/0H/0";
        boolean hardened = false;

        BIP39KeyProducer producer = new BIP39KeyProducer(mnemonic, passphrase, bip32Path, Instant.ofEpochSecond(0), hardened);

        producer.counter.set(Integer.MAX_VALUE);

        try {
            // act
            producer.nextKey();
        } catch (NoMoreSecretsAvailableException e) {
            fail("Exception thrown too early: " + e.getMessage());
        }

        // This call should overflow and throw NoMoreSecretsAvailableException
        producer.nextKey();
    }
    
    @Test
    public void testDefaultBip32PathConstant() {
        assertThat(CKeyProducerJavaBip39.DEFAULT_BIP32_PATH, is("M/44H/0H/0H/0"));
    }
}