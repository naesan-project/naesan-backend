package com.naesan.passport.domain;

final class Keccak256 {
    private static final int RATE_BYTE_LENGTH = 136;
    private static final int OUTPUT_BYTE_LENGTH = 32;
    private static final int LANE_BYTE_LENGTH = Long.BYTES;
    private static final long[] ROUND_CONSTANTS = {
            0x0000000000000001L, 0x0000000000008082L,
            0x800000000000808aL, 0x8000000080008000L,
            0x000000000000808bL, 0x0000000080000001L,
            0x8000000080008081L, 0x8000000000008009L,
            0x000000000000008aL, 0x0000000000000088L,
            0x0000000080008009L, 0x000000008000000aL,
            0x000000008000808bL, 0x800000000000008bL,
            0x8000000000008089L, 0x8000000000008003L,
            0x8000000000008002L, 0x8000000000000080L,
            0x000000000000800aL, 0x800000008000000aL,
            0x8000000080008081L, 0x8000000000008080L,
            0x0000000080000001L, 0x8000000080008008L
    };
    private static final int[] ROTATION_OFFSETS = {
            0, 1, 62, 28, 27,
            36, 44, 6, 55, 20,
            3, 10, 43, 25, 39,
            41, 45, 15, 21, 8,
            18, 2, 61, 56, 14
    };

    private Keccak256() {
    }

    static byte[] digest(byte[] input) {
        long[] state = new long[25];
        int offset = 0;

        while (input.length - offset >= RATE_BYTE_LENGTH) {
            absorbBlock(state, input, offset);
            permute(state);
            offset += RATE_BYTE_LENGTH;
        }

        byte[] finalBlock = new byte[RATE_BYTE_LENGTH];
        System.arraycopy(input, offset, finalBlock, 0, input.length - offset);
        finalBlock[input.length - offset] = 0x01;
        finalBlock[RATE_BYTE_LENGTH - 1] |= (byte) 0x80;
        absorbBlock(state, finalBlock, 0);
        permute(state);

        return squeeze(state);
    }

    private static void absorbBlock(long[] state, byte[] block, int offset) {
        for (int laneIndex = 0; laneIndex < RATE_BYTE_LENGTH / LANE_BYTE_LENGTH; laneIndex++) {
            state[laneIndex] ^= readLittleEndianLong(
                    block,
                    offset + laneIndex * LANE_BYTE_LENGTH
            );
        }
    }

    private static long readLittleEndianLong(byte[] bytes, int offset) {
        long value = 0;
        for (int byteIndex = 0; byteIndex < LANE_BYTE_LENGTH; byteIndex++) {
            value |= (long) (bytes[offset + byteIndex] & 0xff)
                    << Byte.SIZE * byteIndex;
        }
        return value;
    }

    private static void permute(long[] state) {
        long[] columnParity = new long[5];
        long[] mixedState = new long[25];

        for (long roundConstant : ROUND_CONSTANTS) {
            applyTheta(state, columnParity);
            applyRhoAndPi(state, mixedState);
            applyChi(state, mixedState);
            state[0] ^= roundConstant;
        }
    }

    private static void applyTheta(long[] state, long[] columnParity) {
        for (int x = 0; x < 5; x++) {
            columnParity[x] = state[x]
                    ^ state[x + 5]
                    ^ state[x + 10]
                    ^ state[x + 15]
                    ^ state[x + 20];
        }
        for (int x = 0; x < 5; x++) {
            long mixedParity = columnParity[(x + 4) % 5]
                    ^ Long.rotateLeft(columnParity[(x + 1) % 5], 1);
            for (int y = 0; y < 5; y++) {
                state[x + 5 * y] ^= mixedParity;
            }
        }
    }

    private static void applyRhoAndPi(long[] state, long[] mixedState) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                int sourceIndex = x + 5 * y;
                int targetIndex = y + 5 * ((2 * x + 3 * y) % 5);
                mixedState[targetIndex] = Long.rotateLeft(
                        state[sourceIndex],
                        ROTATION_OFFSETS[sourceIndex]
                );
            }
        }
    }

    private static void applyChi(long[] state, long[] mixedState) {
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                state[x + 5 * y] = mixedState[x + 5 * y]
                        ^ (~mixedState[(x + 1) % 5 + 5 * y]
                        & mixedState[(x + 2) % 5 + 5 * y]);
            }
        }
    }

    private static byte[] squeeze(long[] state) {
        byte[] output = new byte[OUTPUT_BYTE_LENGTH];
        for (int byteIndex = 0; byteIndex < OUTPUT_BYTE_LENGTH; byteIndex++) {
            output[byteIndex] = (byte) (
                    state[byteIndex / LANE_BYTE_LENGTH]
                            >>> Byte.SIZE * (byteIndex % LANE_BYTE_LENGTH)
            );
        }
        return output;
    }
}
