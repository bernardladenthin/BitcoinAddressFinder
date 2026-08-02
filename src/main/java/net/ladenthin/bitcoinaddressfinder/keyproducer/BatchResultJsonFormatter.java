// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.keyproducer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import lombok.ToString;
import net.ladenthin.bitcoinaddressfinder.constants.OpenClKernelConstants;
import net.ladenthin.bitcoinaddressfinder.core.BatchResult;
import net.ladenthin.bitcoinaddressfinder.core.Hit;
import org.jspecify.annotations.Nullable;

/**
 * Renders the result messages that every transport sends.
 *
 * <p>One place for the wire format, because three transports report the same thing and a format that
 * exists three times drifts. External clients parse these field names, so this class is effectively
 * a published contract even though nothing here enforces that.
 *
 * <p>Two message shapes, told apart by {@code type}:
 *
 * <ul>
 *   <li>{@code config} — sent once a client can be told what grid the producers use. A client needs
 *       {@code batchSizeInBits} to know the step it must advance by; the server aligns every
 *       incoming base down to a multiple of it, so a smaller step resubmits one block forever while
 *       the rest of the range is never touched.
 *   <li>{@code batch} — the outcome of one checked batch, sent whether or not anything was found.
 *       Reporting the empty case is what lets a client tell "checked, nothing there" from "never
 *       checked".
 * </ul>
 *
 * <p>Big integers are hex without a prefix, so a browser can read them straight into a {@code BigInt}.
 * Output is always a single line: the line-delimited transports frame on the newline.
 */
@ToString
public class BatchResultJsonFormatter {

    /** Message discriminator for the per-batch outcome. */
    private static final String TYPE_BATCH = "batch";

    /** Message discriminator for the configuration greeting. */
    private static final String TYPE_CONFIG = "config";

    /** Radix used for the big-integer fields, matching how keys and ranges are written elsewhere. */
    private static final int HEX_RADIX = 16;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Creates a new {@link BatchResultJsonFormatter}. */
    public BatchResultJsonFormatter() {}

    /**
     * Renders the outcome of one checked batch.
     *
     * @param batchResult the outcome to render
     * @return a single-line JSON message
     * @throws JsonProcessingException if the payload cannot be written
     */
    public String formatBatch(BatchResult batchResult) throws JsonProcessingException {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("type", TYPE_BATCH);

        final @Nullable BigInteger secretBase = batchResult.secretBase();
        if (secretBase == null) {
            root.putNull("secretBase");
        } else {
            root.put("secretBase", secretBase.toString(HEX_RADIX));
        }
        root.put("checkedCount", batchResult.checkedCount());

        final ArrayNode hits = root.putArray("hits");
        for (Hit hit : batchResult.hits()) {
            final ObjectNode hitNode = hits.addObject();
            hitNode.put("privateKey", hit.privateKey().toString(HEX_RADIX));
            hitNode.put("hash160Hex", hit.hash160Hex());
            hitNode.put("address", hit.address());
            hitNode.put("compressed", hit.compressed());
            hitNode.put("vanity", hit.vanity());
        }
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Renders the grid configuration a client needs in order to send usable ranges.
     *
     * @param batchSizeInBits             the grid size each base is expanded to
     * @param batchUsePrivateKeyIncrement whether one base yields a whole grid; when {@code false} the
     *                                    producer wants one secret per candidate instead
     * @return a single-line JSON message
     * @throws JsonProcessingException if the payload cannot be written
     */
    public String formatConfiguration(int batchSizeInBits, boolean batchUsePrivateKeyIncrement)
            throws JsonProcessingException {
        final ObjectNode root = objectMapper.createObjectNode();
        root.put("type", TYPE_CONFIG);
        root.put("batchSizeInBits", batchSizeInBits);
        root.put("batchUsePrivateKeyIncrement", batchUsePrivateKeyIncrement);
        root.put("secretByteLength", OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES);
        return objectMapper.writeValueAsString(root);
    }
}
