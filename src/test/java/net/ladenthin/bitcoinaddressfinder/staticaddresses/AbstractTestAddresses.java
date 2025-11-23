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
package net.ladenthin.bitcoinaddressfinder.staticaddresses;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.ladenthin.bitcoinaddressfinder.ByteBufferUtility;
import net.ladenthin.bitcoinaddressfinder.KeyUtility;
import net.ladenthin.bitcoinaddressfinder.NetworkParameterFactory;
import net.ladenthin.bitcoinaddressfinder.PublicKeyBytes;
import org.apache.commons.codec.binary.Hex;
import org.bitcoinj.base.LegacyAddress;
import org.bitcoinj.base.Network;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.crypto.ECKey;

public abstract class AbstractTestAddresses implements TestAddresses {
    
    public final Network network = new NetworkParameterFactory().getNetwork();

    private final List<ECKey> ecKeys = new ArrayList<>();
    
    public AbstractTestAddresses(int randomSeed, int numberOfAddresses, boolean compressed) {
        Random random = new Random(randomSeed);
        for (int i = 0; i < numberOfAddresses; i++) {
            BigInteger secret = new KeyUtility(network, new ByteBufferUtility(false)).createSecret(PublicKeyBytes.PRIVATE_KEY_MAX_NUM_BITS, random);
            ECKey ecKey = ECKey.fromPrivate(secret, compressed);
            ecKeys.add(ecKey);
        }
    }
    
    @Override
    public int getNumberOfAddresses() {
        return ecKeys.size();
    }

    @Override
    public List<ECKey> getECKeys() {
        return ecKeys;
    }
    
    private LegacyAddress toLegacyAddress(ECKey ecKey) {
        return (LegacyAddress) ecKey.toAddress(ScriptType.P2PKH, network);
    }
    
    private LegacyAddress getLegacyAddressAtIndex(int index) {
        ECKey ecKey = getECKeys().get(index);
        return toLegacyAddress(ecKey);
    }

    @Override
    public String getIndexAsBase58String(int index) {
        LegacyAddress legacyAddress = getLegacyAddressAtIndex(index);
        return legacyAddress.toBase58();
    }
    
    @Override
    public byte[] getIndexAsHash160(int index) {
        LegacyAddress legacyAddress = getLegacyAddressAtIndex(index);
        return legacyAddress.getHash();
    }

    @Override
    public String getIndexAsHash160HexEncoded(int index) {
        byte[] hash = getIndexAsHash160(index);
        return Hex.encodeHexString(hash);
    }
    
    @Override
    public ByteBuffer getIndexAsHash160ByteBuffer(int index) {
        ByteBufferUtility byteBufferUtility = new ByteBufferUtility(true);
        byte[] hash160 = getIndexAsHash160(index);
        ByteBuffer byteBuffer = byteBufferUtility.byteArrayToByteBuffer(hash160);
        return byteBuffer;
    }

    @Override
    public String getAsBase58Strings() {
        StringBuilder sb = new StringBuilder();
        List<String> base58StringList = getAsBase58StringList();
        for (String base58 : base58StringList) {
            sb.append(base58);
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public List<String> getAsBase58StringList() {
        List<String> base58Strings = new ArrayList<>();
        List<ECKey> ecKeys = getECKeys();
        for (ECKey ecKey : ecKeys) {
            LegacyAddress address = toLegacyAddress(ecKey);
            String base58 = address.toBase58();
            base58Strings.add(base58);
        }
        return base58Strings;
    }
}
