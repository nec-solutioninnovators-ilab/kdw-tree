/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * This class stores pre-computed values for bit-vector operations.
 */
class SBVTables {
	/**
	 * An array stores n Combination m. Used in RRR bit-vector.
	 * <p>nCmTBL16[n][m] = n Combination m
	 */
	private final static short[][] nCmTBL16 = {
		{0,0},
		{0,1,0},
		{0,2,1,0},
		{0,3,3,1,0},
		{0,4,6,4,1,0},
		{0,5,10,10,5,1,0},
		{0,6,15,20,15,6,1,0},
		{0,7,21,35,35,21,7,1,0},
		{0,8,28,56,70,56,28,8,1,0},
		{0,9,36,84,126,126,84,36,9,1,0},
		{0,10,45,120,210,252,210,120,45,10,1,0},
		{0,11,55,165,330,462,462,330,165,55,11,1,0},
		{0,12,66,220,495,792,924,792,495,220,66,12,1,0},
		{0,13,78,286,715,1287,1716,1716,1287,715,286,78,13,1,0},
		{0,14,91,364,1001,2002,3003,3432,3003,2002,1001,364,91,14,1,0},
		{0,15,105,455,1365,3003,5005,6435,6435,5005,3003,1365,455,105,15,1,0},
		{0,16,120,560,1820,4368,8008,11440,12870,11440,8008,4368,1820,560,120,16,1,0},
	};

	/**
	 * An array stores number of bits required class c's offset. Used in RRR bit-vector.
	 * <p>RRR16_OFFSET_WIDTH[c] = number of bits required class c's offset
	 */
	final static int[] RRR16_OFFSET_WIDTH = {
		1,
		4,7,10,11,
		13,13,14,14,
		14,13,13,11,
		10,7,4,1,
	};

	/**
	 * An array stores offset of values. Used in RRR bit-vector.
	 * <p>RRR16_VAL2OFS[v] = offset of value v .
	 */
	final static short[] RRR16_VAL2OFS = new short[65536];

	/**
	 * An array stores value corresponding to class/offset pair. Used in RRR bit-vector.
	 * <p>RRR16_OFS2VAL[c][o] = value corresponding to class c / offset o pair.
	 */
	final static short[][] RRR16_OFS2VAL = {
		new short[1],
		new short[16], new short[120], new short[560], new short[1820],
		new short[4368], new short[8008], new short[11440], new short[12870],
		new short[11440], new short[8008], new short[4368], new short[1820],
		new short[560], new short[120], new short[16], new short[1],
	};

	/**
	 * An array stores number of one-bits.
	 * <p>R1_16[B] = number of one-bits in B .
	 */
	final static byte[] R1_16 = new byte[65536];

	/**
	 * An array stores position of (i + 1)-th one-bit.
	 * <p>S1_16[B * 16 + i] = position of (i + 1)-th one-bit in B .
	 */
	final static byte[] S1_16 = new byte[65536*16];

	/**
	 * Initializer
	 */
	static {
		for (int v = 0; v < 65536; v++) {
			int count1 = 0;
			for (int j = 0; j < 16; j++) {
				if ((v & (1 << j)) != 0) {
					count1++;
				}
			}
			R1_16[v] = (byte)count1;
		}
		for (int v = 0; v < 65536; v++) {
			int count1 = 0;
			for (int j = 0; j < 16; j++) {
				S1_16[(v << 4) + j] = (byte)-1;
				if ((v & (0x8000 >>> j)) != 0) {
					S1_16[(v << 4) + count1] = (byte)j;
					count1++;
				}
			}
		}
		for (int v = 0; v < 65536; v++) {
			int cls = R1_16[v];
			int ofs = encodeOffset(cls, v);
			RRR16_VAL2OFS[v] = (short)ofs;
			RRR16_OFS2VAL[cls][ofs] = (short)v;
		}
	}

	/**
	 * Returns number of zero-bits in v.
	 * @param v
	 * @return	number of zero-bits
	 */
	static int rank0_64(long v) {
		return rank1_64(~v);
	}

	/**
	 * Returns number of one-bits in v.
	 * @param v
	 * @return	number of one-bits.
	 */
	static int rank1_32(final int v) {
		return R1_16[v >>> 16] + R1_16[v & 0xffff];
	}

	/**
	 * Returns number of one-bits in v.
	 * @param v
	 * @return	number of one-bits.
	 */
	static int rank1_64(long v) {
		return R1_16[(int)(v >>> 48)]
				+ R1_16[(int)(v >>> 32) & 0xffff]
				+ R1_16[(int)(v >>> 16) & 0xffff]
				+ R1_16[(int)v & 0xffff];
	}

	/**
	 * Returns position of (i + 1)-th zero-bit in v.
	 * @param v
	 * @param i
	 * @return	position of (i + 1)-th zero-bit in v
	 */
	static int select0_64(long v, int i) {
		return select1_64(~v, i);
	}

	/**
	 * Returns position of (i + 1)-th one-bit in v.
	 * @param v
	 * @param i
	 * @return	position of (i + 1)-th one-bit in v.
	 */
	static int select1_64(long v, int i) {
		int v16 = (int)(v >>> 48);
		int r16 = R1_16[v16];
		if (i < r16) {
			return S1_16[(v16 << 4) + i];
		}
		i -= r16;

		v16 = (int)(v >>> 32) & 0xffff;
		r16 = R1_16[v16];
		if (i < r16) {
			return S1_16[(v16 << 4) + i] + 16;
		}
		i -= r16;

		v16 = (int)(v >>> 16) & 0xffff;
		r16 = R1_16[v16];
		if (i < r16) {
			return S1_16[(v16 << 4) + i] + 32;
		}
		i -= r16;

		v16 = (int)v & 0xffff;
		return S1_16[(v16 << 4) + i] + 48;
	}

	/**
	 * Used in RRR bit-vector. Returns offset from class and value.
	 * @param c	class
	 * @param v	value
	 * @return	offset
	 */
	private static int encodeOffset(int c, int v) {
		int offset = 0;
		int m = 1;
		for (int n = 0; m <= c; n++) {
			if ((v & (1L << n)) != 0) {
				offset += nCmTBL16[n][m];
				m++;
			}
		}
		return offset;
	}

}
