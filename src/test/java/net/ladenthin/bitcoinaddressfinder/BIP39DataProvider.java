// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class BIP39DataProvider {

    /**
     * For {@link net.ladenthin.bitcoinaddressfinder.BIP39KeyProducerTest}.
     */
    public final static String DATA_PROVIDER_BIP39_TEST_VECTORS = "bip39TestVectors";

    public final static String FILENAME = "vectors.json";
    public final static String PASSPHRASE = "TREZOR";

    /**
     * from https://github.com/trezor/python-mnemonic/blob/master/vectors.json
     */
    public static Stream<Arguments> bip39TestVectors() throws Exception {
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

        return java.util.Arrays.stream(result).map(row -> Arguments.of(row));
    }
}
