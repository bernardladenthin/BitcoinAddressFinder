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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2PKH;
import net.ladenthin.bitcoinaddressfinder.staticaddresses.enums.P2WPKH;
import org.apache.commons.io.FileUtils;
import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link AddressFile}.
 */
public class AddressFileTest {

    private final Network network = new NetworkParameterFactory().getNetwork();

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // <editor-fold defaultstate="collapsed" desc="processLine">
    @Test
    public void processLine_validBitcoinAddress_addressConsumerCalledOnce() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<AddressToCoin> addressCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressCapture::add, line -> {});

        // act
        addressFile.processLine(P2PKH.Bitcoin.getPublicAddress());

        // assert
        assertThat(addressCapture.size(), is(equalTo(1)));
        assertThat(addressCapture.get(0).type(), is(equalTo(AddressType.P2PKH_OR_P2SH)));
    }

    @Test
    public void processLine_validBitcoinAddress_readStatisticSuccessfulIncrements() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressToCoin -> {}, line -> {});

        // act
        addressFile.processLine(P2PKH.Bitcoin.getPublicAddress());

        // assert
        assertThat(readStatistic.successful, is(equalTo(1L)));
    }

    @Test
    public void processLine_validSegwitAddress_addressConsumerCalledOnce() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<AddressToCoin> addressCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressCapture::add, line -> {});

        // act
        addressFile.processLine(P2WPKH.Bitcoin.getPublicAddress());

        // assert
        assertThat(addressCapture.size(), is(equalTo(1)));
        assertThat(addressCapture.get(0).type(), is(equalTo(AddressType.P2WPKH)));
    }

    @Test
    public void processLine_emptyLine_unsupportedConsumerReceivesEmptyString() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<String> unsupportedCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressToCoin -> {}, unsupportedCapture::add);

        // act
        addressFile.processLine("");

        // assert
        assertThat(unsupportedCapture.size(), is(equalTo(1)));
        assertThat(unsupportedCapture.get(0), is(equalTo("")));
    }

    @Test
    public void processLine_emptyLine_readStatisticUnsupportedIncrements() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressToCoin -> {}, line -> {});

        // act
        addressFile.processLine("");

        // assert
        assertThat(readStatistic.getUnsupportedTotal(), is(equalTo(1L)));
    }

    @Test
    public void processLine_commentLine_unsupportedConsumerReceivesCommentString() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<String> unsupportedCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressToCoin -> {}, unsupportedCapture::add);

        // act
        addressFile.processLine("# this is a comment");

        // assert
        assertThat(unsupportedCapture.size(), is(equalTo(1)));
        assertThat(unsupportedCapture.get(0), is(equalTo("# this is a comment")));
    }

    @Test
    public void processLine_addressHeaderLine_unsupportedConsumerReceivesHeaderString() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<String> unsupportedCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressToCoin -> {}, unsupportedCapture::add);

        // act
        addressFile.processLine(AddressTxtLine.ADDRESS_HEADER);

        // assert
        assertThat(unsupportedCapture.size(), is(equalTo(1)));
        assertThat(unsupportedCapture.get(0), is(equalTo(AddressTxtLine.ADDRESS_HEADER)));
    }

    @Test
    public void processLine_calledTwiceWithValidAddresses_successfulCountIsTwo() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        List<AddressToCoin> addressCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressCapture::add, line -> {});

        // act
        addressFile.processLine(P2PKH.Bitcoin.getPublicAddress());
        addressFile.processLine(P2PKH.Litecoin.getPublicAddress());

        // assert
        assertThat(readStatistic.successful, is(equalTo(2L)));
        assertThat(addressCapture.size(), is(equalTo(2)));
        assertThat(addressCapture.get(0).type(), is(equalTo(AddressType.P2PKH_OR_P2SH)));
        assertThat(addressCapture.get(1).type(), is(equalTo(AddressType.P2PKH_OR_P2SH)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="readFile">
    @Test
    public void readFile_fileWithOneBitcoinAddress_addressConsumerCalledOnce() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        FileUtils.writeStringToFile(file, P2PKH.Bitcoin.getPublicAddress() + "\n", StandardCharsets.UTF_8.name());
        ReadStatistic readStatistic = new ReadStatistic();
        List<AddressToCoin> addressCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressCapture::add, line -> {});

        // act
        addressFile.readFile();

        // assert
        assertThat(addressCapture.size(), is(equalTo(1)));
        assertThat(addressCapture.get(0).type(), is(equalTo(AddressType.P2PKH_OR_P2SH)));
        assertThat(readStatistic.successful, is(equalTo(1L)));
    }

    @Test
    public void readFile_emptyFile_consumersNeverCalled() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        ReadStatistic readStatistic = new ReadStatistic();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressToCoin -> {}, line -> {});

        // act
        addressFile.readFile();

        // assert
        assertThat(readStatistic.successful, is(equalTo(0L)));
        assertThat(readStatistic.getUnsupportedTotal(), is(equalTo(0L)));
    }

    @Test
    public void readFile_mixedValidAndCommentLines_correctCountsUpdated() throws IOException {
        // arrange
        File file = folder.newFile("addresses.txt");
        String content = P2PKH.Bitcoin.getPublicAddress() + "\n"
                + "# comment line\n"
                + P2PKH.Litecoin.getPublicAddress() + "\n";
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8.name());
        ReadStatistic readStatistic = new ReadStatistic();
        List<AddressToCoin> addressCapture = new ArrayList<>();
        List<String> unsupportedCapture = new ArrayList<>();
        AddressFile addressFile = new AddressFile(file, readStatistic, network, addressCapture::add, unsupportedCapture::add);

        // act
        addressFile.readFile();

        // assert
        assertThat(addressCapture.size(), is(equalTo(2)));
        assertThat(addressCapture.get(0).type(), is(equalTo(AddressType.P2PKH_OR_P2SH)));
        assertThat(addressCapture.get(1).type(), is(equalTo(AddressType.P2PKH_OR_P2SH)));
        assertThat(readStatistic.successful, is(equalTo(2L)));
        assertThat(unsupportedCapture.size(), is(equalTo(1)));
        assertThat(unsupportedCapture.get(0), is(equalTo("# comment line")));
        assertThat(readStatistic.getUnsupportedTotal(), is(equalTo(1L)));
    }
    // </editor-fold>
}
