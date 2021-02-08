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

import java.nio.ByteBuffer;
import java.util.List;
import org.bitcoinj.core.ECKey;

public interface TestAddresses {
    
    public int getNumberOfAddresses();
    
    List<ECKey> getECKeys();
    
    String getAsBase58Strings();
    
    List<String> getAsBase58StringList();
    
    public String getIndexAsBase58String(int index);

    public String getIndexAsHash160HexEncoded(int index);
    
    public byte[] getIndexAsHash160(int index);
    
    public ByteBuffer getIndexAsHash160ByteBuffer(int index);
}
