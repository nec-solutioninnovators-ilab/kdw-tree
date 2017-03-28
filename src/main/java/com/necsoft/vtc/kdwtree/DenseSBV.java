/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import static com.necsoft.vtc.kdwtree.SBVTables.*;

/**
 * Implementation of Bit-vector which stores all bit-sequence and auxiliary structures for O(1) time operations.
 * <pre>
 * Constructing sample.
 * SuccinctBitVector sbv = new DenseSBV(length); // call constructor specifying length (number of bits)
 * sbv.append(b);	// append bit value (0 or 1).
 * 	:	// (repeat length times)
 * 
 * sbv.build();	// call build() at last.
 * </pre>
 */
class DenseSBV implements SuccinctBitVector {

	private static final long serialVersionUID = 1L;

	// |-----------------------------------------------------------|
	// |32768                       ...                            | => LB large block stores rank every 32768 bits.
	// |-----------------------------------------------------------|
	// |256                    |256 ...    |256                    | => MB middle block stores difference of rank from LB every 256 bits. 
	// |-----------------------|-----------|-----------------------|
	// |32|32|32|32|32|32|32|32|32| ... |32|32|32|32|32|32|32|32|32| => SB 32-bit integer array stores bit-sequence.
	// |-----------------------------------------------------------|

	private int[] bitPack;
	private int bitLength;
	private int rank0all;
	private int rank1all;

	private int[] rank1lb;
	private short[] rank1mb;

	private int[] select0mb;
	private int[] select1mb;
	private int log2s0interval;
	private int log2s1interval;
	private int[] all0Pos;
	private int[] all1Pos;

	transient private int appendPos = 0;

	/**
	 * Constructor.
	 * @param length	number of bits
	 */
	public DenseSBV(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length=" + length);
		}
		bitLength = length;
		// allocate to MB bounds (256 bits) and plus 1
		int packLen = ((length + 0xff & 0xffffff00) >>> 5) + 1;
		bitPack = new int[packLen];
		// stores zero to last (256 + 32 bits)
		for (int i = 1; i <= 9; i++) {
			bitPack[packLen-i] = 0;
		}
	}

	@Override
	public void append(int bit) {
		if (appendPos >= bitLength) {
			throw new IllegalStateException("over length.");
		}
		if (bit == 0) {
			bitPack[appendPos >>> 5] &= ~(0x80000000 >>> appendPos);
			rank0all++;
		}
		else {
			bitPack[appendPos >>> 5] |= 0x80000000 >>> appendPos;
			rank1all++;
		}
		appendPos++;
	}

	@Override
	public void build() {
		if (rank0all + rank1all != bitLength) {
			throw new IllegalStateException("append call incomplete.");
		}
		buildRank1();
		buildSelect0();
		buildSelect1();
	}

	@Override
	public long usedBits() {
		long bits = bitPack.length * 32L;
		bits += rank1lb.length * 32L;
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
		rank1mb = new short[bitPack.length + 0x7 >>> 3];
		rank1lb = new int[rank1mb.length + 0x7f >>> 7];
		int rank = 0;
		for (int ip = 0; ip < bitPack.length; ip++) {
			if ((ip & 0x3ff) == 0) {
				rank1lb[ip >>> 10] = rank;
			}
			if ((ip & 0x7) == 0) {
				rank1mb[ip >>> 3] = (short)(rank - rank1lb[ip >>> 10]);
			}

			int bits = bitPack[ip];
			rank += R1_16[bits >>> 16] + R1_16[bits & 0xffff];
		}
	}

	// build auxiliary structures for select 0 operation.
	private void buildSelect0() {
		int log2 = 0;
		int elements = rank0all;
		do {
			if (elements < (bitLength >>> 8)) {// elm*32 < len*32/256
				break;
			}
			log2++;
			elements = (rank0all + (1 << log2) - 1) >>> log2;
		} while (log2 < 30);

		log2s0interval = log2;
		if (log2s0interval == 0) {
			create0Pos();
			return;
		}
		select0mb = new int[elements + 1];

		int irmb = 0;
		for (int ismb = 0; ismb < select0mb.length; ismb++) {
			int sample = ismb << log2s0interval;
			for (; irmb < rank1mb.length; irmb++) {
				if ((irmb << 8) - (rank1lb[irmb >>> 7] + rank1mb[irmb]) > sample) {
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
		int elements = rank1all;
		do {
			if (elements < (bitLength >>> 8)) {// elm*32 < len*32/256
				break;
			}
			log2++;
			elements = (rank1all + (1 << log2) - 1) >>> log2;
		} while (log2 < 30);

		log2s1interval = log2;
		if (log2s1interval == 0) {
			create1Pos();
			return;
		}
		select1mb = new int[elements + 1];

		int irmb = 0;
		for (int ismb = 0; ismb < select1mb.length; ismb++) {
			int sample = ismb << log2s1interval;
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
		all0Pos = new int[rank0all];
		int ia = 0;
		for (int i = 0; i < bitLength; i++) {
			if (access(i) == 0) {
				all0Pos[ia++] = i;
			}
		}
	}

	// stores all of one-bit occurrences.
	private void create1Pos() {
		all1Pos = new int[rank1all];
		int ia = 0;
		for (int i = 0; i < bitLength; i++) {
			if (access(i) == 1) {
				all1Pos[ia++] = i;
			}
		}
	}

	@Override
	public int length() {
		return bitLength;
	}

	@Override
	public int access(final int i) {
		if (i < 0 || i >= bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return bitPack[i >>> 5] << i >>> 31;
	}

	@Override
	public int rank(final int b, final int i) {
		if (b == 0) {
			return i - rank1(i);
		}
		else {
			return rank1(i);
		}
	}

	@Override
	public int rank0(final int i) {
		return i - rank1(i);
	}

	@Override
	public int rank1(int i) {
		if (i < 0 || i > bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}

		if ((i & 0x80) == 0) {
			// 128-bits of first half
			final int ip = i >>> 5;
			int bits = bitPack[ip] & ~(0xffffffff >>> i);
			int rank = rank1lb[i >>> 15] + rank1mb[i >>> 8]
					+ R1_16[bits >>> 16] + R1_16[bits & 0xffff];
			for (i = ip & 0xfffffff8; i < ip; i++) {
				bits = bitPack[i];
				rank += R1_16[bits >>> 16] + R1_16[bits & 0xffff];
			}
			return rank;
		}
		else {
			// 128-bits of last half
			final int ip = i >>> 5;
			int bits = bitPack[ip] << i;//& 0xffffffff >>> i;
			int rank = rank1lb[i + 256 >>> 15] + rank1mb[i + 256 >>> 8]
					- R1_16[bits >>> 16] - R1_16[bits & 0xffff];
			for (i = ip | 0x7; i > ip; i--) {
				bits = bitPack[i];
				rank -= R1_16[bits >>> 16] + R1_16[bits & 0xffff];
			}
			return rank;
		}
	}

	@Override
	public int rank0() {
		return rank0all;
	}

	@Override
	public int rank1() {
		return rank1all;
	}

	@Override
	public int select(final int b, final int i) {
		if (b == 0) {
			return select0(i);
		}
		else {
			return select1(i);
		}
	}

	@Override
	public int select0(int i) {
		if (i < 0 || i >= rank0all) {
			throw new IllegalArgumentException("i=" + i);
		}
		if (log2s0interval == 0) {
			return all0Pos[i];
		}

		// binary-search for first MB which overs argument i
		int mbstart = select0mb[i >>> log2s0interval];
		int mbend = select0mb[(i >>> log2s0interval) + 1];
		int rank = 0;
		do {
			final int mbhalf = (mbstart + mbend) >>> 1;
			final int r = (mbhalf << 8) - (rank1lb[mbhalf >>> 7] + rank1mb[mbhalf]);
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
		i -= rank;
		int ip = mbstart << 3;
		for (; ; ip++) {
			final int bits = ~bitPack[ip];
			int b16 = bits >>> 16;
			int r16 = R1_16[b16];
			i -= r16;
			if (i < 0) {
				return S1_16[(b16 << 4) + (i + r16)] + (ip << 5);
			}
			b16 = bits & 0xffff;
			r16 =  R1_16[b16];
			i -= r16;
			if (i < 0) {
				return S1_16[(b16 << 4) + (i + r16)] + (ip << 5) + 16;
			}
		}
	}

	@Override
	public int select1(int i) {
		if (i < 0 || i >= rank1all) {
			throw new IllegalArgumentException("i=" + i);
		}
		if (log2s1interval == 0) {
			return all1Pos[i];
		}

		// binary-search for first MB which overs argument i
		int mbstart = select1mb[i >>> log2s1interval];
		int mbend = select1mb[(i >>> log2s1interval) + 1];
		int rank = 0;
		do {
			final int mbhalf = (mbstart + mbend) >>> 1;
			final int r = rank1lb[mbhalf >>> 7] + rank1mb[mbhalf];
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
		i -= rank;
		int ip = mbstart << 3;
		for (; ; ip++) {
			final int bits = bitPack[ip];
			int b16 = bits >>> 16;
			int r16 = R1_16[b16];
			i -= r16;
			if (i < 0) {
				return S1_16[(b16 << 4) + i + r16] + (ip << 5);
			}
			b16 = bits & 0xffff;
			r16 = R1_16[b16];
			i -= r16;
			if (i < 0) {
				return S1_16[(b16 << 4) + i + r16] + (ip << 5) + 16;
			}
		}
	}

	@Override
	public void selectRanges0(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		int index0, index1;
		int nextrank0 = -1;
		int rank1 = -1;
		for (int j = begin; j < end; j++) {
			int jlsb = j & 1;
			int value = se.get(j) - bias - jlsb;
			if (value < nextrank0) {
				// start or end
				result.add(rank1 + value + jlsb);
			}
			else if (jlsb == 0) {
				// search start side
				index0 = select0(value);
				// start
				result.add(index0);
				rank1 = index0 - value;
				if (rank1 < rank1all) {
					index1 = next1(index0);
					nextrank0 = value + index1 - index0;
				}
				else {
					nextrank0 = rank0all;
				}
			}
			else {
				// search bit alternate positions till the end.
				index1 = rank1 + nextrank0;
				// end
				result.add(index1);
				for (;;) {
					index0 = next0(index1);
					// start
					result.add(index0);
					rank1 = index0 - nextrank0;
					if (rank1 < rank1all) {
						index1 = next1(index0);
						nextrank0 += index1 - index0;
					}
					else {
						nextrank0 = rank0all;
					}
					if (value < nextrank0) {
						// end
						result.add(rank1 + value + 1);
						break;
					}
					else {
						// end
						result.add(index1);
					}
				}
			}
		}
		return;
	}

	@Override
	public void selectRanges1(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		int index1, index0;
		int nextrank1 = -1;
		int rank0 = -1;
		for (int j = begin; j < end; j++) {
			int jlsb = j & 1;
			int value = se.get(j) - bias - jlsb;
			if (value < nextrank1) {
				// start or end
				result.add(rank0 + value + jlsb);
			}
			else if (jlsb == 0) {
				// search start side
				index1 = select1(value);
				// start
				result.add(index1);
				rank0 = index1 - value;
				if (rank0 < rank0all) {
					index0 = next0(index1);
					nextrank1 = value + index0 - index1;
				}
				else {
					nextrank1 = rank1all;
				}
			}
			else {
				// search bit alternate positions till the end.
				index0 = rank0 + nextrank1;
				// end
				result.add(index0);
				for (;;) {
					index1 = next1(index0);
					// start
					result.add(index1);
					rank0 = index1 - nextrank1;
					if (rank0 < rank0all) {
						index0 = next0(index1);
						nextrank1 += index0 - index1;
					}
					else {
						nextrank1 = rank1all;
					}
					if (value < nextrank1) {
						// end
						result.add(rank0 + value + 1);
						break;
					}
					else {
						// end
						result.add(index0);
					}
				}
			}
		}
		return;
	}

	/**
	 * For internal use.
	 * Returns position of first occurrence of zero-bit after i.
	 * @param i	search starting position (exclusive).
	 * @return	position of first occurrence of zero-bit after i
	 */
	int next0(int i) {
		int j = i + 1;
		int bits = ~bitPack[j >>> 5] & 0xffffffff >>> j;
		j &= 0xffffffe0;
		int loop = 0;
		for (;;) {
			int b16 = bits >>> 16;
			if (b16 != 0) {
				return j + S1_16[b16 << 4];
			}
			b16 = bits & 0xffff;
			if (b16 != 0) {
				return j + 16 + S1_16[b16 << 4];
			}
			j += 32;
			if (++loop >= 8) {
				return select0(rank0(i + 1));
			}
			if (j > bitLength) {
				throw new IllegalArgumentException("not found. i=" + i);
			}
			bits = ~bitPack[j >>> 5];
		}
	}

	/**
	 * For internal use.
	 * Returns position of first occurrence of one-bit after i.
	 * @param i	search starting position (exclusive).
	 * @return	position of first occurrence of one-bit after i
	 */
	int next1(int i) {
		int j = i + 1;
		int bits = bitPack[j >>> 5] & 0xffffffff >>> j;
		j &= 0xffffffe0;
		int loop = 0;
		for (;;) {
			int b16 = bits >>> 16;
			if (b16 != 0) {
				return j + S1_16[b16 << 4];
			}
			b16 = bits & 0xffff;
			if (b16 != 0) {
				return j + 16 + S1_16[b16 << 4];
			}
			j += 32;
			if (++loop >= 8) {
				return select1(rank1(i + 1));
			}
			if (j > bitLength) {
				throw new IllegalArgumentException("not found. i=" + i);
			}
			bits = bitPack[j >>> 5];
		}
	}

	/**
	 * For internal use.
	 * Returns position of last occurrence of one-bit before i.
	 * @param i	search starting position (exclusive).
	 * @return	position of last occurrence of one-bit before i
	 */
	int prev1(int i) {
		int j = i - 1;
		int bits = bitPack[j >>> 5] & 0x80000000 >> j;
		j &= 0xffffffe0;
		int loop = 0;
		for (;;) {
			int b16 = bits & 0xffff;
			if (b16 != 0) {
				return j + 16 + S1_16[(b16 << 4) + (R1_16[b16] - 1)];
			}
			b16 = bits >>> 16;
			if (b16 != 0) {
				return j + S1_16[(b16 << 4) + (R1_16[b16] - 1)];
			}
			j -= 32;
			if (++loop >= 8) {
				return select1(rank1(i) - 1);
			}
			if (j < 0) {
				throw new IllegalArgumentException("not found. i=" + i);
			}
			bits = bitPack[j >>> 5];
		}
	}

}
