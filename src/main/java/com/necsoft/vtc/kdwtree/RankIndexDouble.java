/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;

/**
 * Implementation of RankIndex.
 */
class RankIndexDouble implements RankIndex, Serializable {

	private static final long serialVersionUID = 1L;

	private int cardinality;
	private long firstValue;
	private long lastValue;
	private int lowbitsize;
	private LongFixedBitsArray mLowBits;
	private LongFID mHighBits;
	private long highRank0;

	/**
	 * estimate overall number of bits when store numbers to instance.
	 * @return	overall number of bits
	 */
	static long estimateBits(int cardinality, long firstValue, long lastValue) {
		long n = lastValue - firstValue;
		int m = cardinality;
		int bM = 32 - Integer.numberOfLeadingZeros(m - 1);
		int bN = n == 0L ? 1 : 64 - Long.numberOfLeadingZeros(n);
		int bL = bN < bM ? 0 : bN - bM;
		long lastDisplacementHigh = n >>> bL;
		return (long)m * bL + m + lastDisplacementHigh;
	}

	/**
	 * Constructor
	 * @param sortedArray	number sequence to store. must be sorted.
	 * @param cardinality	cardinality of number sequence.
	 */
	RankIndexDouble(long[] sortedArray, int cardinality) {
		// generate compressed structure from monotone sequence which is removed duplicates from sortedArray.
		int length = sortedArray.length;
		this.cardinality = cardinality;
		firstValue = sortedArray[0];
		lastValue = sortedArray[length - 1];

		long n = lastValue - firstValue;
		int m = cardinality;

		int bM = 32 - Integer.numberOfLeadingZeros(m - 1);
		int bN = n == 0L ? 1 : 64 - Long.numberOfLeadingZeros(n);
		int bL = bN < bM ? 0 : bN - bM;
		lowbitsize = bL;
		long lowbitmask = (1L << lowbitsize) - 1L;
		long lastDisplacementHigh = n >>> lowbitsize;

		if (lowbitsize > 0) {
			mLowBits = new LongFixedBitsArray(m, lowbitsize);
		}
		mHighBits = new LongFID(m + lastDisplacementHigh);
		highRank0 = lastDisplacementHigh;

		long prevvalue = 0L;
		long prevhigh = 0L;
		for (int ptr = 0, i = 0; i < length; i++) {
			long value = sortedArray[i];

			if (i > 0) {
				if (prevvalue == value) {
					continue;
				}
			}

			long displacement = value - firstValue;
			if (lowbitsize > 0) {
				mLowBits.set(ptr++, displacement & lowbitmask);
			}
			long high = displacement >>> lowbitsize;
			long highDelta = high - prevhigh;
			for (long j = 0; j < highDelta; j++) {
				mHighBits.append(0);
			}
			mHighBits.append(1);
			prevvalue = value;
			prevhigh = high;
		}

		mHighBits.build();
	}

	@Override
	public int real2denserank(double real) {
		return dense_ranklt(Utils.encodeDL(real));
	}

	@Override
	public double denserank2double(int denserank) {
		return Utils.decodeLD(dense_rank_value(denserank));
	}

	@Override
	public int denserankMax() {
		return cardinality - 1;
	}

	private long dense_rank_value(int dense_rank) {
		long high = mHighBits.select1(dense_rank) - dense_rank;
		if (lowbitsize == 0) {
			return firstValue + high;
		}
		long low = mLowBits.get(dense_rank);
		return firstValue + ((high << lowbitsize) | low);
	}

	private int dense_ranklt(long v) {
		if (v <= firstValue) {
			return 0;
		}
		long c = v - firstValue;

		// given high-bits
		long cH = c >>> lowbitsize;
		if (highRank0 < cH) {
			// all of stored high-bits are less than given high-bits
			return cardinality;
		}

		long pos0H = cH == 0L ? -1L : mHighBits.select0(cH - 1L);
		if (mHighBits.access(pos0H + 1L) == 0) {
			// given high-bits are not exists in stored high-bits.
			// and now, pos0H indicate position of first stored high-bits over given high-bits.
			// then, rank1(pos0H) equivalent to ranklt.
			return (int)(pos0H - (cH - 1L)); // equivalent to mHighBits.rank1(pos0H)
		}
		// now, given high-bits are exists in stored high-bits

		if (lowbitsize == 0) {
			return (int)(pos0H - (cH - 1L));
		}

		// given low-bits
		long cL = c & ((1L << lowbitsize) - 1L);
		// search given low-bits in range [sL, eL)
		long sL = pos0H - (cH - 1L);
		long eL = highRank0 == cH ? cardinality : mHighBits.select0(cH) - cH;
		return (int)mLowBits.binarySearchGE(sL, eL, cL);
	}

}
