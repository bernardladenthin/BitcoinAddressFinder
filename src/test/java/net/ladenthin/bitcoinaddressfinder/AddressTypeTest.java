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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.Test;

/**
 * Unit tests for {@link AddressType}.
 */
public class AddressTypeTest {

    // <editor-fold defaultstate="collapsed" desc="values">
    @Test
    public void values_allConstants_countIsTwo() {
        // act
        int actual = AddressType.values().length;

        // assert
        assertThat(actual, is(equalTo(2)));
    }

    @Test
    public void values_p2pkhOrP2sh_nameIsExpected() {
        // act
        String actual = AddressType.P2PKH_OR_P2SH.name();

        // assert
        assertThat(actual, is(equalTo("P2PKH_OR_P2SH")));
    }

    @Test
    public void values_p2wpkh_nameIsExpected() {
        // act
        String actual = AddressType.P2WPKH.name();

        // assert
        assertThat(actual, is(equalTo("P2WPKH")));
    }

    @Test
    public void values_eachConstant_isNotNull() {
        for (AddressType type : AddressType.values()) {
            // assert
            assertThat(type, is(notNullValue()));
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="valueOf">
    @Test
    public void valueOf_p2pkhOrP2sh_returnsExpectedConstant() {
        // act
        AddressType actual = AddressType.valueOf("P2PKH_OR_P2SH");

        // assert
        assertThat(actual, is(equalTo(AddressType.P2PKH_OR_P2SH)));
    }

    @Test
    public void valueOf_p2wpkh_returnsExpectedConstant() {
        // act
        AddressType actual = AddressType.valueOf("P2WPKH");

        // assert
        assertThat(actual, is(equalTo(AddressType.P2WPKH)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void valueOf_unknownConstantName_throwsIllegalArgumentException() {
        // act
        AddressType.valueOf("UNKNOWN_TYPE");
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="ordinal">
    @Test
    public void ordinal_p2pkhOrP2sh_isZero() {
        // act
        int actual = AddressType.P2PKH_OR_P2SH.ordinal();

        // assert
        assertThat(actual, is(equalTo(0)));
    }

    @Test
    public void ordinal_p2wpkh_isOne() {
        // act
        int actual = AddressType.P2WPKH.ordinal();

        // assert
        assertThat(actual, is(equalTo(1)));
    }
    // </editor-fold>
}
