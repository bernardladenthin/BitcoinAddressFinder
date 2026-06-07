// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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

    @Test
    public void valueOf_unknownConstantName_throwsIllegalArgumentException() {
        // act
        assertThrows(IllegalArgumentException.class, () -> AddressType.valueOf("UNKNOWN_TYPE"));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="declaration order">
    @Test
    public void values_declarationOrder_isP2pkhOrP2shThenP2wpkh() {
        // act, assert
        // Pin the declaration order directly; values() returns constants in
        // declaration order, so this avoids depending on Enum.ordinal() index
        // values (Error Prone EnumOrdinal) while still locking the order in.
        assertThat(AddressType.values(), is(arrayContaining(AddressType.P2PKH_OR_P2SH, AddressType.P2WPKH)));
    }
    // </editor-fold>
}
