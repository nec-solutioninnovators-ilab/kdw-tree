/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;

/**
 * Compressed structure for storing positive monotone number sequence.
 */
class Monotone implements Serializable {

	private static final long serialVersionUID = 1L;

	private boolean strict = true;
	private int length;
	private int firstValue;
	private int lastValue;
	private int lowbitsize;
	private FixedBitsArray mLowBits;
	private DenseSBV mHighBits;
	private int highRank0;

	/**
	 * Constructor
	 * @param values	number sequence to store. must be sorted.
	 */
	Monotone(int[] values) {
		build(values);
	}

	private void build(int[] values) {
		if (values == null || values.length == 0) {
			throw new IllegalArgumentException("empty.");
		}
		if (values[0] < 0 || values[values.length-1] < 0) {
			throw new IllegalArgumentException("negative.");
		}
		if (values[0] > values[values.length-1]) {
			throw new IllegalArgumentException("not monotone.");
		}

		length = values.length;
		firstValue = values[0];
		lastValue = values[length-1];
		int lastDisplacement = lastValue - firstValue;
		int m = length;
		int n = lastDisplacement;

		int bM = 32 - Integer.numberOfLeadingZeros(m - 1);
		int bN = n == 0 ? 1 : 32 - Integer.numberOfLeadingZeros(n);
		int bL = bN < bM ? 0 : bN - bM;
		lowbitsize = bL;
		int lowbitmask = (1 << lowbitsize) - 1;
		int lastDisplacementHigh = lastDisplacement >>> lowbitsize;
		if (lowbitsize > 0) {
			mLowBits = new FixedBitsArray(m, lowbitsize);
		}
		mHighBits = new DenseSBV(m + lastDisplacementHigh);
		highRank0 = lastDisplacementHigh;

		int prevvalue = Integer.MIN_VALUE;
		int prevhigh = 0;
		for (int i = 0; i < m; i++) {
			int value = values[i];
			if (value < 0) {
				throw new IllegalArgumentException("negative.");
			}
			if (prevvalue > value) {
				throw new IllegalArgumentException("not monotone.");
			}
			if (prevvalue == value) {
				strict = false;
			}
			int displacement = value - firstValue;
			if (lowbitsize > 0) {
				mLowBits.set(i, displacement & lowbitmask);
			}
			int high = displacement >>> lowbitsize;
			int highDelta = high - prevhigh;
			for (int j = 0; j < highDelta; j++) {
				mHighBits.append(0);
			}
			mHighBits.append(1);
			prevvalue = value;
			prevhigh = high;
		}
		mHighBits.build();
	}

	/**
	 * Returns the overall number of bits allocated by this.
	 * @return	the overall number of bits allocated by this.
	 */
	long usedBits() {
		return mHighBits.usedBits() + length * (long)lowbitsize;
	}

	/**
	 * Returns number of elements.
	 * @return	number of elements.
	 */
	int length() {
		return length;
	}

	/**
	 * Returns the value at index i.
	 * @param i	index
	 * @return	the value at index i
	 */
	int access(int i) {
		int high = mHighBits.select1(i) - i;
		if (lowbitsize == 0) {
			return firstValue + high;
		}
		int low = mLowBits.get(i);
		return firstValue + ((high << lowbitsize) | low);
	}

	/**
	 * Returns first value.
	 * @return	first value.
	 */
	int firstValue() {
		return firstValue;
	}

	/**
	 * Returns last value.
	 * @return	last value.
	 */
	int lastValue() {
		return lastValue;
	}

	/**
	 * Returns true if this instance contains specified number.
	 * @param v	number to be tested
	 * @return	true if this instance contains number v.
	 */
	boolean contains(int v) {
		int c = v - firstValue;
		if (c < 0) {
			return false;
		}
		if (c == 0) {
			return true;
		}

		// given high-bits
		int cH = c >>> lowbitsize;
		if (highRank0 < cH) {
			// all of stored high-bits are less than given high-bits
			return false;
		}

		int pos0H = cH == 0 ? -1 : mHighBits.select0(cH - 1);
		if (mHighBits.access(pos0H + 1) == 0) {
			// given high-bits are not exists in stored high-bits
			return false;
		}
		// now given high-bits are exists in stored high-bits

		if (lowbitsize == 0) {
			return true;
		}

		// given low-bits
		int cL = c & ((1 << lowbitsize) - 1);
		// search given low-bits in range [sL, eL)
		int sL = pos0H - (cH - 1);
		int eL = highRank0 == cH ? length : mHighBits.select0(cH) - cH;
		int posL = mLowBits.binarySearch(sL, eL, cL);
		return posL >= 0;
	}

	/**
	 * Returns number of elements which is less than specified value.
	 * @param v	value
	 * @return	number of elements which is less than v.
	 */
	int ranklt(int v) {
		int c = v - firstValue;
		if (c <= 0) {
			return 0;
		}

		// given high-bits
		int cH = c >>> lowbitsize;
		if (highRank0 < cH) {
			// all of stored high-bits are less than given high-bits
			return length;
		}

		int pos0H = cH == 0 ? -1 : mHighBits.select0(cH - 1);
		if (mHighBits.access(pos0H + 1) == 0) {
			// given high-bits are not exists in stored high-bits.
			// and now, pos0H indicate position of first stored high-bits over given high-bits.
			// then, rank1(pos0H) equivalent to ranklt.
			return pos0H - (cH - 1); // equivalent to mHighBits.rank1(pos0H)
		}
		// now, given high-bits are exists in stored high-bits

		if (lowbitsize == 0) {
			return pos0H - (cH - 1);
		}

		// given low-bits
		int cL = c & ((1 << lowbitsize) - 1);
		// search given low-bits in range [sL, eL)
		int sL = pos0H - (cH - 1);
		int eL = highRank0 == cH ? length : mHighBits.select0(cH) - cH;
		if (strict) {
			return mLowBits.binarySearchGE(sL, eL, cL);
		}
		else {
			return mLowBits.binarySearchGEfirst(sL, eL, cL);
		}
	}

	/**
	 * Searches for the specified value.
	 * @param v	the value to be searched for
	 * @return	index of the last one with the search value, if it is contained in this instance;
	 * otherwise, (-(insertion point) - 1).
	 * <br>the insertion point is defined as the point at which the value would be inserted into this:
	 * the index of the first element greater than the search value, or length() if all elements are less than the search value.
	 * NOTE that this guarantees that the return value will be >= 0 if and only if the search value is found.
	 */
	int find(int v) {
		int c = v - firstValue;
		if (c < 0) {
			return ~0;
		}
		if (c == 0) {
			return 0;
		}

		// given high-bits
		int cH = c >>> lowbitsize;
		if (highRank0 < cH) {
			// all of stored high-bits are less than given high-bits
			return ~length; // equivalent to (- length) - 1
		}

		int pos0H = cH == 0 ? -1 : mHighBits.select0(cH - 1);
		if (mHighBits.access(pos0H + 1) == 0) {
			// given high-bits are not exists in stored high-bits.
			// and now, pos0H indicate position of first stored high-bits over given high-bits.
			// then, rank1(pos0H) equivalent to ranklt.
			return ~(pos0H - (cH - 1)); // equivalent to (- (pos0H - (cH - 1)) - 1, equivalent to (- mHighBits.rank1(pos0H)) - 1
		}
		// now, given high-bits are exists in stored high-bits

		if (lowbitsize == 0) {
			if (strict) {
				return pos0H - (cH - 1);
			}
			else {
				// search the last one
				return highRank0 == cH ? length - 1 : mHighBits.select0(cH) - cH - 1;
			}
		}

		// given low-bits
		int cL = c & ((1 << lowbitsize) - 1);
		// search given low-bits in range [sL, eL)
		int sL = pos0H - (cH - 1);
		int eL = highRank0 == cH ? length : mHighBits.select0(cH) - cH;
		if (strict) {
			return mLowBits.binarySearch(sL, eL, cL);
		}
		else {
			return mLowBits.binarySearchLast(sL, eL, cL);
		}
	}

	class AccessCache {
		private int ca_0;
		private int ca_prevIndex;
		private int ca_prevSelect = -1;
	}

	/**
	 * for internal use.
	 */
	int access(int i, AccessCache cache) {
		int select1;
		if (cache.ca_prevSelect < 0) {
			select1 = mHighBits.select1(i);
		}
		else {
			int diff = i - cache.ca_prevIndex;
			if (diff == 0) {
				return cache.ca_0;
			}
			else if (diff == 1) {
				select1 = mHighBits.next1(cache.ca_prevSelect);
			}
			else if (diff == 2) {
				select1 = mHighBits.next1(cache.ca_prevSelect);
				select1 = mHighBits.next1(select1);
			}
			else if (diff == -1) {
				select1 = mHighBits.prev1(cache.ca_prevSelect);
			}
			else {
				select1 = mHighBits.select1(i);
			}
		}
		cache.ca_prevSelect = select1;
		cache.ca_prevIndex = i;

		int high = select1 - i;
		if (lowbitsize == 0) {
			return cache.ca_0 = firstValue + high;
		}
		int low = mLowBits.get(i);
		return cache.ca_0 = firstValue + ((high << lowbitsize) | low);
	}

	class SequentialContext {
		private int lastIndex = -1;
		private int lastSelect = -1;
	}

	/**
	 * for internal use.
	 */
	int sequentialAccessStart(int i, SequentialContext ctx) {
		int high = (ctx.lastSelect = mHighBits.select1(i)) - i;
		ctx.lastIndex = i;
		if (lowbitsize == 0) {
			return firstValue + high;
		}
		int low = mLowBits.get(i);
		return firstValue + ((high << lowbitsize) | low);
	}
	/**
	 * for internal use.
	 */
	int sequentialAccessPrev(SequentialContext ctx) {
		int i;
		int high = (ctx.lastSelect = mHighBits.prev1(ctx.lastSelect)) - (i = --ctx.lastIndex);
		if (lowbitsize == 0) {
			return firstValue + high;
		}
		int low = mLowBits.get(i);
		return firstValue + ((high << lowbitsize) | low);
	}
	/**
	 * for internal use.
	 */
	int sequentialAccessNext(SequentialContext ctx) {
		int i;
		int high = (ctx.lastSelect = mHighBits.next1(ctx.lastSelect)) - (i = ++ctx.lastIndex);
		if (lowbitsize == 0) {
			return firstValue + high;
		}
		int low = mLowBits.get(i);
		return firstValue + ((high << lowbitsize) | low);
	}

	class AccessHint {
		private int prevAccessHint = -1;
		private int prevAccessIndex;
		private int nextAccessHint = -1;
		private int nextAccessIndex;
		private int fwdAccessHint = -1;
		private int fwdAccessIndex;
		private int highValue = -1;
		private int highStart;
		private int highEnd;
	}

	/**
	 * for internal use.
	 */
	int access(int i, AccessHint hint) {
		int high;
		if (hint.highValue >= 0 && i >= hint.highStart && i < hint.highEnd){
			high = hint.highValue;
		}
		else if (i == hint.prevAccessIndex && hint.prevAccessHint >= 0) {
			high = mHighBits.prev1(hint.prevAccessHint) - i;
		}
		else if (i == hint.nextAccessIndex && hint.nextAccessHint >= 0) {
			high = (hint.fwdAccessHint = mHighBits.next1(hint.nextAccessHint)) - i;
			hint.fwdAccessIndex = i + 1;
		}
		else if (hint.fwdAccessHint >= 0 && i == hint.fwdAccessIndex) {
			high = (hint.fwdAccessHint = mHighBits.next1(hint.fwdAccessHint)) - i;
			hint.fwdAccessIndex = i + 1;
		}
		else {
			high = mHighBits.select1(i) - i;
		}

		if (lowbitsize == 0) {
			return firstValue + high;
		}
		int low = mLowBits.get(i);
		return firstValue + ((high << lowbitsize) | low);
	}

	/**
	 * for internal use.
	 */
	int find(int v, AccessHint hint) {
		int c = v - firstValue;

		if (c < 0) {
			return ~0;
		}
		if (c == 0) {
			return 0;
		}

		// given high-bits
		int cH = c >>> lowbitsize;
		if (highRank0 < cH) {
			// all of stored high-bits are less than given high-bits
			return ~length;
		}

		int pos0H = cH == 0 ? -1 : mHighBits.select0(cH - 1);
		int rank1 = pos0H - (cH - 1);
		hint.prevAccessHint = pos0H;
		hint.prevAccessIndex = rank1 - 1;
		if (mHighBits.access(pos0H + 1) == 0) {
			// given high-bits are not exists in stored high-bits.
			// and now, pos0H indicate position of first stored high-bits over given high-bits.
			// then, rank1(pos0H) equivalent to ranklt.
			hint.nextAccessHint = pos0H + 1;
			hint.nextAccessIndex = rank1;
			return ~rank1;
		}
		// now, given high-bits are exists in stored high-bits

		if (lowbitsize == 0) {
			if (strict) {
				return rank1;
			}
			else {
				// search the last one
				return highRank0 == cH ? length - 1 : mHighBits.next0(pos0H) - cH - 1;
			}
		}

		// given low-bits
		int cL = c & ((1 << lowbitsize) - 1);
		// search given low-bits in range [sL, eL)
		int sL = rank1;
		int eL;
		if (highRank0 == cH) {
			eL = length;
		}
		else {
			int pos0Hnext = mHighBits.next0(pos0H);
			eL = pos0Hnext - cH;
			hint.nextAccessHint = pos0Hnext;
			hint.nextAccessIndex = eL;
		}
		hint.highValue = cH;
		hint.highStart = sL;
		hint.highEnd = eL;
		if (strict) {
			return mLowBits.binarySearch(sL, eL, cL);
		}
		else {
			return mLowBits.binarySearchLast(sL, eL, cL);
		}
	}

}
