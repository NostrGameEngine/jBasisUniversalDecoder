package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies exact solid-color BC7 output and shared table isolation.
 */
public class TestBc7SolidEndpointCache {
    @Test
    public void all512Mode6EntriesPreserveOriginalEndpointsErrorAndTieOrder() {
        for (int pbit = 0; pbit < 2; pbit++) {
            for (int value = 0; value < 256; value++) {
                int[] expected = exhaustive(value, pbit);
                assertArrayEquals(expected, Bc7LdrBlockPacker.mode6OptimalEndpoint(value, pbit),
                        "value=" + value + " pbit=" + pbit);
                assertPacked(expected, Bc7LdrBlockPacker.mode6OptimalEndpointPacked(value, pbit));
            }
        }
    }

    @Test
    public void all256Mode5EntriesPreserveOriginalEndpointsErrorAndTieOrder() {
        for (int value = 0; value < 256; value++) {
            int[] expected = exhaustive(value, -1);
            assertArrayEquals(expected, Bc7LdrBlockPacker.mode5OptimalEndpoint(value),
                    "value=" + value);
            assertPacked(expected, Bc7LdrBlockPacker.mode5OptimalEndpointPacked(value));
        }
    }

    @Test
    public void actualUastcSolidBlocksPreserveBc7BytesAndEveryRgbaChannel() {
        int[][] colors = {
            {0, 0, 0, 255}, {0, 0, 0, 0}, {255, 255, 255, 255}, {255, 0, 0, 255},
            {17, 85, 204, 123}, {5, 250, 127, 64}, {1, 1, 1, 1}, {254, 254, 254, 254},
            {0, 1, 254, 255}, {64, 128, 192, 255}
        };
        byte[] blocks = new byte[colors.length * 16];
        for (int index = 0; index < colors.length; index++) {
            System.arraycopy(uastcSolid(colors[index]), 0, blocks, index * 16, 16);
        }
        byte[] actual = UastcLdrAstcTranscoder.transcodeToBc7(blocks, colors.length * 4, 4);
        for (int index = 0; index < colors.length; index++) {
            assertArrayEquals(exhaustiveSolid(colors[index]),
                    Arrays.copyOfRange(actual, index * 16, (index + 1) * 16),
                    "RGBA=" + Arrays.toString(colors[index]));
            int[] rgba = new int[64];
            Bc7LdrBlockPacker.decodeBlock(actual, index * 16, rgba);
            for (int pixel = 0; pixel < 16; pixel++) {
                assertArrayEquals(colors[index], Arrays.copyOfRange(rgba, pixel * 4, pixel * 4 + 4));
            }
        }
    }

    @Test
    public void repeatedOpaqueBlackEmissionBlocksUseTheSameExactBytes() {
        int[] black = {0, 0, 0, 255};
        byte[] block = uastcSolid(black);
        byte[] expected = exhaustiveSolid(black);
        byte[] input = new byte[64 * 16];
        for (int index = 0; index < 64; index++) {
            System.arraycopy(block, 0, input, index * 16, 16);
        }
        byte[] actual = UastcLdrAstcTranscoder.transcodeToBc7(input, 32, 32);
        for (int index = 0; index < 64; index++) {
            assertArrayEquals(expected, Arrays.copyOfRange(actual, index * 16, index * 16 + 16));
        }
    }

    @Test
    public void concurrentLookupAndCallerArrayMutationCannotChangeCachedResults() throws Exception {
        int[][] expected6 = new int[512][];
        int[][] expected5 = new int[256][];
        for (int value = 0; value < 256; value++) {
            expected6[value] = exhaustive(value, 0);
            expected6[256 + value] = exhaustive(value, 1);
            expected5[value] = exhaustive(value, -1);
        }
        ExecutorService workers = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> finished = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                final int shift = worker * 59;
                finished.add(workers.submit(() -> {
                    start.await();
                    for (int index = 0; index < 512; index++) {
                        int value = (index + shift) & 255;
                        int pbit = index >>> 8;
                        int packed = Bc7LdrBlockPacker.mode6OptimalEndpointPacked(value, pbit);
                        assertPacked(expected6[(pbit << 8) | value], packed);
                        int[] mutable6 = Bc7LdrBlockPacker.mode6OptimalEndpoint(value, pbit);
                        Arrays.fill(mutable6, -1);
                        assertPacked(expected6[(pbit << 8) | value],
                                Bc7LdrBlockPacker.mode6OptimalEndpointPacked(value, pbit));
                        int[] mutable5 = Bc7LdrBlockPacker.mode5OptimalEndpoint(value);
                        Arrays.fill(mutable5, -1);
                        assertPacked(expected5[value],
                                Bc7LdrBlockPacker.mode5OptimalEndpointPacked(value));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : finished) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void invalidComponentsCannotAliasAnotherCachedByteOrPbit() {
        assertThrows(IllegalArgumentException.class,
                () -> Bc7LdrBlockPacker.mode6OptimalEndpointPacked(256, 0));
        assertThrows(IllegalArgumentException.class,
                () -> Bc7LdrBlockPacker.mode6OptimalEndpointPacked(0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> Bc7LdrBlockPacker.mode5OptimalEndpointPacked(-1));
    }

    private static void assertPacked(int[] expected, int packed) {
        assertEquals(expected[0], Bc7LdrBlockPacker.endpointLow(packed));
        assertEquals(expected[1], Bc7LdrBlockPacker.endpointHigh(packed));
        assertEquals(expected[2], Bc7LdrBlockPacker.endpointError(packed));
    }

    /**
     * Original exhaustive search order with strict improvement; -1 selects mode 5 replication.
     */
    private static int[] exhaustive(int value, int pbit) {
        int bestError = Integer.MAX_VALUE;
        int bestLow = 0;
        int bestHigh = 0;
        for (int low = 0; low < 128; low++) {
            int lowEndpoint = (low << 1) | (pbit < 0 ? low >>> 6 : pbit);
            for (int high = 0; high < 128; high++) {
                int highEndpoint = (high << 1) | (pbit < 0 ? high >>> 6 : pbit);
                int decoded = (lowEndpoint * 43 + highEndpoint * 21 + 32) >> 6;
                int error = (decoded - value) * (decoded - value);
                if (error < bestError) {
                    bestError = error;
                    bestLow = low;
                    bestHigh = high;
                }
            }
        }
        return new int[] {bestLow, bestHigh, bestError};
    }

    /**
     * UASTC mode 8 uses the 5-bit Huffman code 0x17 followed by four 8-bit channels.
     */
    private static byte[] uastcSolid(int[] rgba) {
        byte[] block = new byte[16];
        block[0] = 0x17;
        for (int channel = 0; channel < 4; channel++) {
            for (int bit = 0; bit < 8; bit++) {
                int at = 5 + channel * 8 + bit;
                block[at >>> 3] |= (byte) (((rgba[channel] >>> bit) & 1) << (at & 7));
            }
        }
        return block;
    }

    /**
     * Preserves the original solid-color mode and pbit decisions using exhaustive endpoints.
     */
    private static byte[] exhaustiveSolid(int[] color) {
        int[][] p0 = new int[4][];
        int[][] p1 = new int[4][];
        int err0 = 0;
        int err1 = 0;
        for (int channel = 0; channel < 4; channel++) {
            p0[channel] = exhaustive(color[channel], 0);
            p1[channel] = exhaustive(color[channel], 1);
            err0 += p0[channel][2];
            err1 += p1[channel][2];
        }
        Bc7LdrBlockPacker.Block block = new Bc7LdrBlockPacker.Block(err0 > 0 && err1 > 0 ? 5 : 6);
        if (block.mode == 5) {
            for (int channel = 0; channel < 3; channel++) {
                int[] endpoints = exhaustive(color[channel], -1);
                block.low[0][channel] = endpoints[0];
                block.high[0][channel] = endpoints[1];
            }
            block.low[0][3] = color[3];
            block.high[0][3] = color[3];
            Arrays.fill(block.selectors, 1);
        } else {
            int pbit = err1 < err0 ? 1 : 0;
            int[][] endpoints = pbit == 0 ? p0 : p1;
            for (int channel = 0; channel < 4; channel++) {
                block.low[0][channel] = endpoints[channel][0];
                block.high[0][channel] = endpoints[channel][1];
            }
            block.pbits[0][0] = pbit;
            block.pbits[0][1] = pbit;
            Arrays.fill(block.selectors, 5);
        }
        byte[] output = new byte[16];
        Bc7LdrBlockPacker.writeBlock(output, 0, block);
        return output;
    }
}
