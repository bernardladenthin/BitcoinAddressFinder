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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tngtech.java.junit.dataprovider.DataProvider;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class BIP39DataProvider {

    /**
     * For {@link net.ladenthin.bitcoinaddressfinder.Bip39Test}.
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

        Gson gson = new Gson();
        JsonObject jsonObject = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), JsonObject.class);
        int totalVectors = 0;
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            totalVectors += entry.getValue().getAsJsonArray().size();
        }
        
        Object[][] result = new Object[totalVectors][];
        int index = 0;
        
        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String language = entry.getKey();
            JsonArray vectors = entry.getValue().getAsJsonArray();

            for (JsonElement vectorElement : vectors) {
                JsonArray vector = vectorElement.getAsJsonArray();
                String entropy = vector.get(0).getAsString();
                String mnemonic = vector.get(1).getAsString();
                String seed = vector.get(2).getAsString();
                String xprv = vector.get(3).getAsString();
                result[index++] = new Object[]{language, entropy, mnemonic, PASSPHRASE, seed, xprv};
            }
        }

        return result;
    }
}
