// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
// Copyright 2024 Bernard Ladenthin bernard.ladenthin@gmail.com
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import org.bitcoinj.base.Network;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;

public class NetworkParameterFactory {
    
    public Network getNetwork() {
        return getNetworkParameters().network();
    }
    
    private NetworkParameters getNetworkParameters() {
        NetworkParameters networkParameters = MainNetParams.get();
        return networkParameters;
    }
}
