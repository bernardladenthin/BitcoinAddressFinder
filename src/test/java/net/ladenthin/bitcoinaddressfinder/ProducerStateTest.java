// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

public class ProducerStateTest {

    // <editor-fold defaultstate="collapsed" desc="values">
    @Test
    public void values_allStatesPresent_countIsFour() {
        // arrange, act
        ProducerState[] states = ProducerState.values();

        // assert
        assertThat(states.length, is(equalTo(4)));
    }

    @Test
    public void values_ordinalOfUninitialized_isZero() {
        // arrange, act, assert
        assertThat(ProducerState.UNINITIALIZED.ordinal(), is(equalTo(0)));
    }

    @Test
    public void values_ordinalOfInitialized_isOne() {
        // arrange, act, assert
        assertThat(ProducerState.INITIALIZED.ordinal(), is(equalTo(1)));
    }

    @Test
    public void values_ordinalOfRunning_isTwo() {
        // arrange, act, assert
        assertThat(ProducerState.RUNNING.ordinal(), is(equalTo(2)));
    }

    @Test
    public void values_ordinalOfNotRunning_isThree() {
        // arrange, act, assert
        assertThat(ProducerState.NOT_RUNNING.ordinal(), is(equalTo(3)));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="name">
    @Test
    public void name_uninitialized_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.UNINITIALIZED.name(), is(equalTo("UNINITIALIZED")));
    }

    @Test
    public void name_initialized_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.INITIALIZED.name(), is(equalTo("INITIALIZED")));
    }

    @Test
    public void name_running_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.RUNNING.name(), is(equalTo("RUNNING")));
    }

    @Test
    public void name_notRunning_returnsExpectedString() {
        // arrange, act, assert
        assertThat(ProducerState.NOT_RUNNING.name(), is(equalTo("NOT_RUNNING")));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="valueOf">
    @Test
    public void valueOf_uninitializedString_returnsUninitializedState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("UNINITIALIZED"), is(equalTo(ProducerState.UNINITIALIZED)));
    }

    @Test
    public void valueOf_initializedString_returnsInitializedState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("INITIALIZED"), is(equalTo(ProducerState.INITIALIZED)));
    }

    @Test
    public void valueOf_runningString_returnsRunningState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("RUNNING"), is(equalTo(ProducerState.RUNNING)));
    }

    @Test
    public void valueOf_notRunningString_returnsNotRunningState() {
        // arrange, act, assert
        assertThat(ProducerState.valueOf("NOT_RUNNING"), is(equalTo(ProducerState.NOT_RUNNING)));
    }

    @Test
    public void valueOf_unknownString_throwsIllegalArgumentException() {
        // arrange, act, assert
        assertThrows(IllegalArgumentException.class, () -> ProducerState.valueOf("UNKNOWN_STATE"));
    }
    // </editor-fold>
}
