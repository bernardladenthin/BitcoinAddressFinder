// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import org.bitcoinj.base.Network;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.Test;

public class NetworkParameterFactoryTest {

    // <editor-fold defaultstate="collapsed" desc="getNetwork">
    @Test
    public void getNetwork_noArguments_returnsNonNull() {
        NetworkParameterFactory factory = new NetworkParameterFactory();
        Network network = factory.getNetwork();
        assertThat(network, is(notNullValue()));
    }

    @Test
    public void getNetwork_calledTwice_returnsSameNetworkId() {
        NetworkParameterFactory factory = new NetworkParameterFactory();
        Network first = factory.getNetwork();
        Network second = factory.getNetwork();
        assertThat(first.id(), is(equalTo(second.id())));
    }

    @Test
    public void getNetwork_differentFactoryInstances_returnSameNetworkId() {
        NetworkParameterFactory factory1 = new NetworkParameterFactory();
        NetworkParameterFactory factory2 = new NetworkParameterFactory();
        Network network1 = factory1.getNetwork();
        Network network2 = factory2.getNetwork();
        assertThat(network1.id(), is(equalTo(network2.id())));
    }

    @Test
    public void getNetwork_noArguments_returnsMainNet() {
        NetworkParameterFactory factory = new NetworkParameterFactory();
        Network network = factory.getNetwork();
        assertThat(network.id(), is(equalTo("org.bitcoin.production")));
    }
    // </editor-fold>
}
