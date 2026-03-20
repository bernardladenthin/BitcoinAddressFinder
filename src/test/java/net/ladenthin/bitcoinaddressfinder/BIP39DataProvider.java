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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tngtech.java.junit.dataprovider.DataProvider;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;

public class BIP39DataProvider {

    /**
     * For {@link net.ladenthin.bitcoinaddressfinder.BIP39KeyProducerTest}.
     */
    public final static String DATA_PROVIDER_BIP39_TEST_VECTORS = "bip39TestVectors";
    
    public final static String FILENAME = "vectors.json";
    public final static String PASSPHRASE = "TREZOR";

    @DataProvider
    /**
     * from https://github.com/trezor/python-mnemonic/blob/master/vectors.json
     */
    public static Object[][] bip39TestVectors() throws Exception {
        InputStream inputStream = BIP39DataProvider.class.getResourceAsStream("/" + FILENAME);
        if (inputStream == null) {
            throw new IllegalStateException(FILENAME + " not found in classpath");
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(inputStream);
        int totalVectors = 0;
        for (Iterator<Map.Entry<String, JsonNode>> fields = root.fields(); fields.hasNext();) {
            Map.Entry<String, JsonNode> entry = fields.next();
            totalVectors += entry.getValue().size();
        }

        Object[][] result = new Object[totalVectors][];
        int index = 0;

        for (Iterator<Map.Entry<String, JsonNode>> fields = root.fields(); fields.hasNext();) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String language = entry.getKey();
            JsonNode vectors = entry.getValue();

            for (JsonNode vectorElement : vectors) {
                String entropy = vectorElement.get(0).asText();
                String mnemonic = vectorElement.get(1).asText();
                String seed = vectorElement.get(2).asText();
                String xprv = vectorElement.get(3).asText();
                result[index++] = new Object[]{language, entropy, mnemonic, PASSPHRASE, seed, xprv};
            }
        }

        return result;
    }
}
