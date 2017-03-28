/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import static com.necsoft.vtc.kdwtree.SBVTables.*;

/**
 * class for internal use.
 * estimate overall number of bits used in bit-vectors.
 */
class SBVBitsEstimator {

	private int length;
	private int pos;
	private int prevbit;

	private int rank0;
	private int first0;
	private int last0;

	private int rank1;
	private int first1;
	private int last1;

	private int sw01;
	private int firstsw;
	private int lastsw;
	private int firstswrank1;
	private int lastswrank1;

	private int rrr16cls;
	private int rrr16ofspos;
	private int rrr256ofspos;
	private int rrr256rank;

	SBVBitsEstimator() {
		length = 0;
		pos = 0;
		prevbit = -1;

		rank0 = 0;
		first0 = -1;
		last0 = -1;

		rank1 = 0;
		first1 = -1;
		last1 = -1;

		sw01 = 0;
		firstsw = -1;
		lastsw = -1;
		firstswrank1 = -1;
		lastswrank1 = -1;

		rrr16cls = 0;
		rrr16ofspos = 0;
	}
	void append(int bit) {
		bit &= 1;
		if (bit == 1) {
			if (first1 < 0) {
				first1 = pos;
			}
			last1 = pos;
			if (prevbit != bit) {
				sw01++;
				if (firstsw < 0) {
					firstsw = pos;
					firstswrank1 = rank1;
				}
				lastsw = pos;
				lastswrank1 = rank1;
			}
		}
		else {
			if (first0 < 0) {
				first0 = pos;
			}
			last0 = pos;
		}

		if (pos != 0 && (pos & 0xf) == 0) {
			rrr16ofspos += RRR16_OFFSET_WIDTH[rrr16cls];
			rrr16cls = 0;
		}
		if ((pos & 0xff) == 0) {
			rrr256rank = rank1;
			rrr256ofspos = rrr16ofspos;
		}

		if (bit == 1) {
			rank1++;
			rrr16cls++;
		}
		else {
			rank0++;
		}
		length++;
		pos++;
		prevbit = bit;
	}
	private int estimateMonotone(int m, int first, int last) {
		if (m == 0) {
			return 0;
		}
		if (first < 0 || last < 0) {
			throw new IllegalArgumentException("negative.");
		}
		if (first > last) {
			throw new IllegalArgumentException("not monotone.");
		}
		int n = last - first;
		int bM = 32 - Integer.numberOfLeadingZeros(m - 1);
		int bN = n == 0 ? 1 : 32 - Integer.numberOfLeadingZeros(n);
		int bL = bN < bM ? 0 : bN - bM;
		int nH = n >>> bL;
		return m * bL + m + nH;
	}
	boolean isAll0() {
		return rank0 == length;
	}
	boolean isAll1() {
		return rank1 == length;
	}
	int getFlipUpCount() {
		return sw01;
	}
	int estimateSparse0() {
		return estimateMonotone(rank0, first0, last0);
	}
	int estimateSparse1() {
		return estimateMonotone(rank1, first1, last1);
	}
	int estimateBiased() {
		int total = 0;
		total += estimateMonotone(sw01, firstsw, lastsw);
		total += estimateMonotone(sw01, firstswrank1, lastswrank1);
		return total;
	}
	int estimateRRR16() {
		int total = 0;
		total += ((length + 0xf) >>> 4) * 4;
		total += rrr16ofspos;
		total += estimateMonotone((length + 0xff) >>> 8, 0, rrr256ofspos);
		total += estimateMonotone((length + 0xff) >>> 8, 0, rrr256rank);
		return total;
	}
	enum SBVType {
		DENSE, SPARSE0, SPARSE1, RRR16, BIASED, ALL0, ALL1
	}
	SBVType bestSizeType() {
		if (isAll0()) {
			return SBVType.ALL0;
		}
		if (isAll1()) {
			return SBVType.ALL1;
		}
		SBVType type = SBVType.DENSE;
		int bits = length;
		int sparse0Bits = estimateSparse0();
		int sparse1Bits = estimateSparse1();
		int rrr16Bits = estimateRRR16();
		int biasedBits = estimateBiased();
		if (sparse0Bits < bits) {
			type = SBVType.SPARSE0;
			bits = sparse0Bits;
		}
		if (sparse1Bits < bits) {
			type = SBVType.SPARSE1;
			bits = sparse1Bits;
		}
		if (rrr16Bits < bits) {
			type = SBVType.RRR16;
			bits = rrr16Bits;
		}
		if (biasedBits < bits) {
			type = SBVType.BIASED;
			bits = biasedBits;
		}
		return type;
	}

}
