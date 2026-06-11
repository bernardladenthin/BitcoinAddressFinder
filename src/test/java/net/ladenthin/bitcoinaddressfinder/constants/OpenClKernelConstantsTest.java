// SPDX-FileCopyrightText: 2017-2026 Bernard Ladenthin <bernard.ladenthin@gmail.com>
//
// SPDX-License-Identifier: Apache-2.0
package net.ladenthin.bitcoinaddressfinder.constants;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

/**
 * Byte-exact pin for the constants that flow into the OpenCL kernel's result
 * buffer layout (and the derived Java array-capacity bound).
 *
 * <p>These values were aligned to match the {@code .cl} kernel sources over
 * many iterations. Any accidental edit that changes their numeric value would
 * silently corrupt the GPU output parsing. This test fails the build with a
 * precise message instead.
 *
 * <p>If a {@code .cl} kernel change deliberately moves a value, update this
 * test in the same commit so the new pin reflects the intentional change.
 */
public class OpenClKernelConstantsTest {

    public OpenClKernelConstantsTest() {}

    @Test
    public void byteExact_secKernelPrimitives() {
        assertThat(OpenClKernelConstants.BITS_PER_BYTE, is(8));
        assertThat(OpenClKernelConstants.U32_PER_WORD, is(1));
        assertThat(OpenClKernelConstants.U32_NUM_BYTES, is(4));
        assertThat(OpenClKernelConstants.BYTE_SHIFT_TO_U32_MSB, is(24));
    }

    @Test
    public void byteExact_privateKeyDerivations() {
        assertThat(OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_BYTES, is(32));
        assertThat(OpenClKernelConstants.PRIVATE_KEY_MAX_NUM_WORDS, is(8));
    }

    @Test
    public void byteExact_secPrefixBytes() {
        assertThat(OpenClKernelConstants.SEC_PREFIX_NUM_BITS, is(8));
        assertThat(OpenClKernelConstants.SEC_PREFIX_NUM_BYTES, is(1));
        assertThat(OpenClKernelConstants.SEC_PREFIX_NUM_WORDS, is(1));
        assertThat(OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT, is(0x04));
        assertThat(OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y, is(0x02));
        assertThat(OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y, is(0x03));
        assertThat(OpenClKernelConstants.SEC_PREFIX_SHIFTED_NUM_BYTES, is(4));
        assertThat(OpenClKernelConstants.SEC_PREFIX_UNCOMPRESSED_ECDSA_POINT_SHIFTED, is(0x04_00_00_00));
        assertThat(OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_EVEN_Y_SHIFTED, is(0x02_00_00_00));
        assertThat(OpenClKernelConstants.SEC_PREFIX_COMPRESSED_ECDSA_POINT_ODD_Y_SHIFTED, is(0x03_00_00_00));
    }

    @Test
    public void byteExact_coordinateLengths() {
        assertThat(OpenClKernelConstants.ONE_COORDINATE_NUM_BITS, is(256));
        assertThat(OpenClKernelConstants.ONE_COORDINATE_NUM_BYTES, is(32));
        assertThat(OpenClKernelConstants.TWO_COORDINATES_NUM_BITS, is(512));
        assertThat(OpenClKernelConstants.TWO_COORDINATES_NUM_BYTES, is(64));
        assertThat(OpenClKernelConstants.ONE_COORDINATE_NUM_WORDS, is(8));
        assertThat(OpenClKernelConstants.TWO_COORDINATE_NUM_WORDS, is(16));
    }

    @Test
    public void byteExact_publicKeyLengths() {
        assertThat(OpenClKernelConstants.SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BITS, is(520));
        assertThat(OpenClKernelConstants.SEC_PUBLIC_KEY_UNCOMPRESSED_NUM_BYTES, is(65));
        assertThat(OpenClKernelConstants.SEC_PUBLIC_KEY_UNCOMPRESSED_WORDS, is(17));
        assertThat(OpenClKernelConstants.SEC_PUBLIC_KEY_COMPRESSED_NUM_BITS, is(264));
        assertThat(OpenClKernelConstants.SEC_PUBLIC_KEY_COMPRESSED_NUM_BYTES, is(33));
        assertThat(OpenClKernelConstants.SEC_PUBLIC_KEY_COMPRESSED_WORDS, is(9));
    }

    @Test
    public void byteExact_hashBlockSizes() {
        assertThat(OpenClKernelConstants.SHA256_INPUT_BLOCK_SIZE_BITS, is(512));
        assertThat(OpenClKernelConstants.SHA256_INPUT_BLOCK_SIZE_BYTES, is(64));
        assertThat(OpenClKernelConstants.SHA256_INPUT_BLOCK_SIZE_WORDS, is(16));
        assertThat(OpenClKernelConstants.RIPEMD160_INPUT_BLOCK_SIZE_BITS, is(512));
        assertThat(OpenClKernelConstants.RIPEMD160_INPUT_BLOCK_SIZE_BYTES, is(64));
        assertThat(OpenClKernelConstants.RIPEMD160_INPUT_BLOCK_SIZE_WORDS, is(16));
        assertThat(OpenClKernelConstants.SHA256_HASH_NUM_BITS, is(256));
        assertThat(OpenClKernelConstants.SHA256_HASH_NUM_BYTES, is(32));
        assertThat(OpenClKernelConstants.SHA256_HASH_NUM_WORDS, is(8));
        assertThat(OpenClKernelConstants.RIPEMD160_HASH_NUM_BITS, is(160));
        assertThat(OpenClKernelConstants.RIPEMD160_HASH_NUM_BYTES, is(20));
        assertThat(OpenClKernelConstants.RIPEMD160_HASH_NUM_WORDS, is(5));
    }

    @Test
    public void byteExact_sha256InputTotals() {
        assertThat(OpenClKernelConstants.SHA256_INPUT_BLOCKS_FOR_UNCOMPRESSED_SEC, is(2));
        assertThat(OpenClKernelConstants.SHA256_INPUT_TOTAL_BITS_UNCOMPRESSED, is(1024));
        assertThat(OpenClKernelConstants.SHA256_INPUT_TOTAL_BYTES_UNCOMPRESSED, is(128));
        assertThat(OpenClKernelConstants.SHA256_INPUT_TOTAL_WORDS_UNCOMPRESSED, is(32));
        assertThat(OpenClKernelConstants.SHA256_INPUT_BLOCKS_FOR_COMPRESSED_SEC, is(1));
        assertThat(OpenClKernelConstants.SHA256_INPUT_TOTAL_BITS_COMPRESSED, is(512));
        assertThat(OpenClKernelConstants.SHA256_INPUT_TOTAL_BYTES_COMPRESSED, is(64));
        assertThat(OpenClKernelConstants.SHA256_INPUT_TOTAL_WORDS_COMPRESSED, is(16));
    }

    @Test
    public void byteExact_chunkSlotSizes() {
        assertThat(OpenClKernelConstants.CHUNK_SIZE_00_NUM_BYTES_BIG_ENDIAN_X, is(32));
        assertThat(OpenClKernelConstants.CHUNK_SIZE_01_NUM_BYTES_BIG_ENDIAN_Y, is(32));
        assertThat(OpenClKernelConstants.CHUNK_SIZE_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED, is(20));
        assertThat(OpenClKernelConstants.CHUNK_SIZE_11_NUM_BYTES_RIPEMD160_COMPRESSED, is(20));
    }

    @Test
    public void byteExact_chunkOffsets_accumulatorAndTotal() {
        assertThat(OpenClKernelConstants.CHUNK_OFFSET_00_NUM_BYTES_BIG_ENDIAN_X, is(0));
        assertThat(OpenClKernelConstants.CHUNK_OFFSET_01_NUM_BYTES_BIG_ENDIAN_Y, is(32));
        assertThat(OpenClKernelConstants.CHUNK_OFFSET_10_NUM_BYTES_RIPEMD160_UNCOMPRESSED, is(64));
        assertThat(OpenClKernelConstants.CHUNK_OFFSET_11_NUM_BYTES_RIPEMD160_COMPRESSED, is(84));
        assertThat(OpenClKernelConstants.CHUNK_OFFSET_99_NUM_BYTES_END_OF_CHUNK, is(104));
        assertThat(OpenClKernelConstants.CHUNK_SIZE_NUM_BYTES, is(104));
    }

    @Test
    public void byteExact_derivedArrayCapacityBounds() {
        // Bound derived from the unified 108-byte entry stride minus the 4-byte header word.
        assertThat(OpenClKernelConstants.MAXIMUM_CHUNK_ELEMENTS, is(19_884_107));
        // BIT_COUNT must remain 24 (the grid-size cap used across the project); the unified
        // stride does not change it.
        assertThat(OpenClKernelConstants.BIT_COUNT_FOR_MAX_CHUNKS_ARRAY, is(24));
    }

    @Test
    public void byteExact_unifiedOutputBufferFormat() {
        assertThat(OpenClKernelConstants.OUTPUT_HEADER_SIZE_BYTES, is(4));
        assertThat(OpenClKernelConstants.OUTPUT_COUNT_FULL_TRANSFER_SENTINEL, is(0xFFFF_FFFF));
        assertThat(OpenClKernelConstants.OUTPUT_ENTRY_INDEX_BYTE_OFFSET, is(0));
        assertThat(OpenClKernelConstants.OUTPUT_ENTRY_X_BYTE_OFFSET, is(4));
        assertThat(OpenClKernelConstants.OUTPUT_ENTRY_Y_BYTE_OFFSET, is(36));
        assertThat(OpenClKernelConstants.OUTPUT_ENTRY_HASH160_UNCOMPRESSED_BYTE_OFFSET, is(68));
        assertThat(OpenClKernelConstants.OUTPUT_ENTRY_HASH160_COMPRESSED_BYTE_OFFSET, is(88));
        assertThat(OpenClKernelConstants.OUTPUT_ENTRY_SIZE_BYTES, is(108));
    }
}
