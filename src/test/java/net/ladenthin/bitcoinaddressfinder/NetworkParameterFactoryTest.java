// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.bitcoinj.base.Network;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NetworkParameterFactory}.
 */
public class NetworkParameterFactoryTest {

    // <editor-fold defaultstate="collapsed" desc="getNetwork">
    @Test
    public void getNetwork_noArguments_returnsNonNull() {
        // arrange
        NetworkParameterFactory factory = new NetworkParameterFactory();

        // act
        Network network = factory.getNetwork();

        // assert
        assertThat(network, is(notNullValue()));
    }

    @Test
    public void getNetwork_calledTwice_returnsSameNetworkId() {
        // arrange
        NetworkParameterFactory factory = new NetworkParameterFactory();

        // act
        Network first = factory.getNetwork();
        Network second = factory.getNetwork();

        // assert
        assertThat(first.id(), is(equalTo(second.id())));
    }

    @Test
    public void getNetwork_differentFactoryInstances_returnSameNetworkId() {
        // arrange
        NetworkParameterFactory factory1 = new NetworkParameterFactory();
        NetworkParameterFactory factory2 = new NetworkParameterFactory();

        // act
        Network network1 = factory1.getNetwork();
        Network network2 = factory2.getNetwork();

        // assert
        assertThat(network1.id(), is(equalTo(network2.id())));
    }

    @Test
    public void getNetwork_noArguments_returnsMainNet() {
        // arrange
        NetworkParameterFactory factory = new NetworkParameterFactory();

        // act
        Network network = factory.getNetwork();

        // assert
        assertThat(network.id(), is(equalTo("org.bitcoin.production")));
    }
    // </editor-fold>
}
