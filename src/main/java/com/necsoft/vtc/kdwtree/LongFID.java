/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import static com.necsoft.vtc.kdwtree.SBVTables.*;

import java.io.Serializable;

/**
 * for internal use.
 * implementation of dense bit-vector which is support necessary operations for rank-space.
 */
class LongFID implements Serializable {

	private static final long serialVersionUID = 1L;

	// |-----------------------------------|
	// |32768           ...                | => LB large block stores rank every 32768 bits.
	// |-----------------------------------|
	// |256        |256 ...    |256        | => MB middle block stores difference of rank from LB every 256 bits.
	// |-----------|-----------|-----------|
	// |64|64|64|64|64| ... |64|64|64|64|64| => SB 64-bit integer array stores bit-sequence.
	// |-----------------------------------|

	private long[] bitPack;
	private long bitLength;
	private long rank0all;
	private long rank1all;

	private long[] rank1lb;
	private short[] rank1mb;

	private int[] select0mb;
	private int[] select1mb;
	private int log2s0interval;
	private int log2s1interval;
	private long[] all0Pos;
	private long[] all1Pos;

	transient private long appendPos = 0;

	/**
	 * Constructor.
	 * @param length	number of bits
	 */
	LongFID(long length) {
		if (length <= 0L) {
			throw new IllegalArgumentException("length=" + length);
		}
		if (length > 0x1000000000L) {// 2^36
			throw new IllegalArgumentException("length limit over " + length);
		}
		bitLength = length;
		// allocate to MB bounds (256 bits) and plus 1
		int packLen = (int)((length + 0xffL & 0xffffffffffffff00L) >>> 6) + 1;
		bitPack = new long[packLen];
		// stores zero to last (256 + 64 bits)
		for (int i = 1; i <= 5; i++) {
			bitPack[packLen-i] = 0;
		}
	}

	/**
	 * Append bit 0 or 1.
	 * @param b	0 or 1
	 */
	void append(int bit) {
		if (appendPos >= bitLength) {
			throw new IllegalStateException("over length.");
		}
		if (bit == 0) {
			bitPack[(int)(appendPos >>> 6)] &= ~(0x8000000000000000L >>> appendPos);
			rank0all++;
		}
		else {
			bitPack[(int)(appendPos >>> 6)] |= 0x8000000000000000L >>> appendPos;
			rank1all++;
		}
		appendPos++;
	}

	/**
	 * Build internal structures for query operations.
	 */
	void build() {
		if (rank0all + rank1all != bitLength) {
			throw new IllegalStateException("append call incomplete.");
		}
		buildRank1();
		buildSelect0();
		buildSelect1();
	}

	/**
	 * Returns the overall number of bits allocated by this.
	 * @return	the overall number of bits allocated by this.
	 */
	long usedBits() {
		long bits = bitPack.length * 64L;
		bits += rank1lb.length * 64L;
		bits += rank1mb.length * 16L;
		if (select0mb != null) {
			bits += select0mb.length * 32L;
		}
		if (select1mb != null) {
			bits += select1mb.length * 32L;
		}
		if (all0Pos != null) {
			bits += all0Pos.length * 32L;
		}
		if (all1Pos != null) {
			bits += all1Pos.length * 32L;
		}
		return bits;
	}

	// build auxiliary structures for rank operation.
	private void buildRank1() {
		rank1mb = new short[bitPack.length + 0x3 >>> 2];
		rank1lb = new long[rank1mb.length + 0x7f >>> 7];
		long rank = 0;
		for (int ip = 0; ip < bitPack.length; ip++) {
			if ((ip & 0x1ff) == 0) {
				rank1lb[ip >>> 9] = rank;
			}
			if ((ip & 0x3) == 0) {
				rank1mb[ip >>> 2] = (short)(rank - rank1lb[ip >>> 9]);
			}

			rank += Long.bitCount(bitPack[ip]);
		}
	}

	// build auxiliary structures for select 0 operation.
	private void buildSelect0() {
		int log2 = 0;
		long elements = rank0all;
		do {
			if (elements < (bitLength >>> 8)) {// elm*32 < len*32/256
				break;
			}
			log2++;
			elements = (rank0all + (1L << log2) - 1L) >>> log2;
		} while (log2 < 30);

		log2s0interval = log2;
		if (log2s0interval == 0) {
			create0Pos();
			return;
		}
		select0mb = new int[(int)(elements + 1)];

		int irmb = 0;
		for (int ismb = 0; ismb < select0mb.length; ismb++) {
			long sample = (long)ismb << log2s0interval;
			for (; irmb < rank1mb.length; irmb++) {
				if (((long)irmb << 8) - (rank1lb[irmb >>> 7] + rank1mb[irmb]) > sample) {
					break;
				}
			}
			// stores index of last MB which rank is at most every select sample 
			select0mb[ismb] = irmb - 1;
		}
	}

	// build auxiliary structures for select 1 operation.
	private void buildSelect1() {
		int log2 = 0;
		long elements = rank1all;
		do {
			if (elements < (bitLength >>> 8)) {// elm*32 < len*32/256
				break;
			}
			log2++;
			elements = (rank1all + (1L << log2) - 1L) >>> log2;
		} while (log2 < 30);

		log2s1interval = log2;
		if (log2s1interval == 0) {
			create1Pos();
			return;
		}
		select1mb = new int[(int)(elements + 1)];

		int irmb = 0;
		for (int ismb = 0; ismb < select1mb.length; ismb++) {
			long sample = (long)ismb << log2s1interval;
			for (; irmb < rank1mb.length; irmb++) {
				if (rank1lb[irmb >>> 7] + rank1mb[irmb] > sample) {
					break;
				}
			}
			// stores index of last MB which rank is at most every select sample 
			select1mb[ismb] = irmb - 1;
		}
	}

	// stores all of zero-bit occurrences.
	private void create0Pos() {
		all0Pos = new long[(int)rank0all];
		int ia = 0;
		for (long i = 0; i < bitLength; i++) {
			if (access(i) == 0) {
				all0Pos[ia++] = i;
			}
		}
	}

	// stores all of one-bit occurrences.
	private void create1Pos() {
		all1Pos = new long[(int)rank1all];
		int ia = 0;
		for (long i = 0; i < bitLength; i++) {
			if (access(i) != 0) {
				all1Pos[ia++] = i;
			}
		}
	}

	/**
	 * Returns the bit value at the specified index.
	 * @param i	the index of the bit value.
	 * @return	the bit value at the specified index. 0 or 1.
	 */
	int access(final long i) {
		if (i < 0 || i >= bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return (int)(bitPack[(int)(i >>> 6)] << i >>> 63);
	}

	/**
	 * Returns the position of the (i + 1)-th occurrence of zero-bit.
	 * @param i	
	 * @return	the position of the (i + 1)-th occurrence of zero-bit.
	 */
	long select0(final long i) {
		if (i < 0 || i >= rank0all) {
			throw new IllegalArgumentException("i=" + i);
		}
		if (log2s0interval == 0) {
			return all0Pos[(int)i];
		}

		// binary-search for first MB which overs argument i
		int mbstart = select0mb[(int)(i >>> log2s0interval)];
		int mbend = select0mb[(int)(i >>> log2s0interval) + 1];
		long rank = 0;
		do {
			final int mbhalf = (mbstart + mbend) >>> 1;
			final long r = ((long)mbhalf << 8) - (rank1lb[mbhalf >>> 7] + rank1mb[mbhalf]);
			if (r <= i) {
				mbstart = mbhalf + 1;
				rank = r;
			}
			else { // r > i
				mbend = mbhalf - 1;
			}
		} while (mbstart <= mbend);
		// then search start from previous MB
		mbstart--;

		// linear-search in MB
		int ii = (int)(i - rank);
		int ip = mbstart << 2;
		for (; ; ip++) {
			final long bits = ~bitPack[ip];
			int b16 = (int)(bits >>> 48);
			int r16 = R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + ((long)ip << 6);
			}

			b16 = (int)(bits >>> 32) & 0xffff;
			r16 =  R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + 16 + ((long)ip << 6);
			}

			b16 = (int)bits >>> 16;
			r16 =  R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + 32 + ((long)ip << 6);
			}

			b16 = (int)bits & 0xffff;
			r16 =  R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + 48 + ((long)ip << 6);
			}
		}
	}

	/**
	 * Returns the position of the (i + 1)-th occurrence of one-bit.
	 * @param i	
	 * @return	the position of the (i + 1)-th occurrence of one-bit.
	 */
	long select1(long i) {
		if (i < 0 || i >= rank1all) {
			throw new IllegalArgumentException("i=" + i);
		}
		if (log2s1interval == 0) {
			return all1Pos[(int)i];
		}

		// binary-search for first MB which overs argument i
		int mbstart = select1mb[(int)(i >>> log2s1interval)];
		int mbend = select1mb[(int)(i >>> log2s1interval) + 1];
		long rank = 0;
		do {
			final int mbhalf = (mbstart + mbend) >>> 1;
			final long r = rank1lb[mbhalf >>> 7] + rank1mb[mbhalf];
			if (r <= i) {
				mbstart = mbhalf + 1;
				rank = r;
			}
			else { // r > i
				mbend = mbhalf - 1;
			}
		} while (mbstart <= mbend);
		// then search start from previous MB
		mbstart--;

		// linear-search in MB
		int ii = (int)(i - rank);
		int ip = mbstart << 2;
		for (; ; ip++) {
			final long bits = bitPack[ip];
			int b16 = (int)(bits >>> 48);
			int r16 = R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + ((long)ip << 6);
			}

			b16 = (int)(bits >>> 32) & 0xffff;
			r16 = R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + 16 + ((long)ip << 6);
			}

			b16 = (int)bits >>> 16;
			r16 = R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + 32 + ((long)ip << 6);
			}

			b16 = (int)bits & 0xffff;
			r16 = R1_16[b16];
			ii -= r16;
			if (ii < 0) {
				return S1_16[(b16 << 4) + ii + r16] + 48 + ((long)ip << 6);
			}
		}
	}

}
