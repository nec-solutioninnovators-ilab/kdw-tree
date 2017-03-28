/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;

/**
 * (long version) Array like structure which stores each element in specified number of bits.
 */
class LongFixedBitsArray implements Serializable {

	private static final long serialVersionUID = 1L;

	private long mLength;
	private int mBitSize;
	private long mBitMask;
	private long[] mBits;

	/**
	 * Constructor.
	 * @param length	the number of elements
	 * @param bitSize	the number of bits of one element
	 */
	LongFixedBitsArray(long length, int bitSize) {
		if (bitSize < 1 || bitSize > 64) {
			throw new IllegalArgumentException("bitSize=" + bitSize);
		}
		mLength = length;
		mBitSize = bitSize;
		mBitMask = (1L << bitSize) - 1L;
		mBits = new long[(int)(mLength * mBitSize + 63 >>> 6)];
	}

	/**
	 * Returns the overall number of bits allocated by this.
	 * @return	the overall number of bits
	 */
	long usedBits() {
		return mLength * mBitSize;
	}

	/**
	 * Returns the number of elements in this array.
	 * @return the number of elements in this array
	 */
	long length() {
		return mLength;
	}

	/**
	 * Returns the number of bits of one element.
	 * @return	the number of bits
	 */
	int bitSize() {
		return mBitSize;
	}

	/**
	 * Returns the element at the specified position in this array.
	 * @param index	index of the element to return
	 * @return the element at the specified position.
	 */
	long get(final long index) {
		if (index < 0 || index >= mLength) {
			throw new IllegalArgumentException("index=" + index);
		}

		long bitPos = index * mBitSize; 
		int bitsIdx = (int)(bitPos >>> 6);
		int leftOfs = (int)bitPos & 0x3f;
		int rightOfs = 64 - leftOfs - mBitSize;
		if (rightOfs < 0) {
			return (
					(mBits[bitsIdx] << (-rightOfs))
					| (mBits[bitsIdx+1] >>> (64 + rightOfs))
			) & mBitMask;
		}
		else {
			return (
					mBits[bitsIdx] >>> (rightOfs)
			) & mBitMask;
		}
	}

	/**
	 * Replaces the element at the specified position in this array with the specified element.
	 * @param index	index of the element to replace
	 * @param value	element to be stored at the specified position
	 */
	void set(long index, long value) {
		if (index < 0 || index >= mLength) {
			throw new IllegalArgumentException("index=" + index);
		}

		value &= mBitMask;

		long bitPos = index * mBitSize; 
		int bitsIdx = (int)(bitPos >>> 6);
		int leftOfs = (int)bitPos & 0x3f;
		int rightOfs = 64 - leftOfs - mBitSize;
		if (rightOfs < 0) {
			mBits[bitsIdx] = (mBits[bitsIdx] & ~(mBitMask >>> -rightOfs)) | (value >>> -rightOfs);
			mBits[bitsIdx+1] = (mBits[bitsIdx+1] & ~(mBitMask << (64 + rightOfs))) | (value << (64 + rightOfs));
		}
		else {
			mBits[bitsIdx] = (mBits[bitsIdx] & ~(mBitMask << rightOfs)) | (value << rightOfs);
		}
	}

	/**
	 * Searches a range for the specified value.
	 * @param s	the index of the first element (inclusive) to be searched
	 * @param e	the index of the last element (exclusive) to be searched
	 * @param value	the value to be searched for
	 * @return	index of the search value, if it is contained in this instance;
	 * otherwise, (-(insertion point) - 1).
	 * <br>the insertion point is defined as the point at which the value would be inserted into this:
	 * the index of the first element greater than the search value, or e if all elements in the range are less than the search value.
	 * NOTE that this guarantees that the return value will be >= 0 if and only if the search value is found.
	 */
	long binarySearch(long s, long e, final long value) {
		e--;
		while (s <= e) {
			long m = (s + e) >>> 1;
			long v = get(m);
			if (value < v) {
				e = m - 1;
			}
			else if (v < value) {
				s = m + 1;
			}
			else {
				return m;
			}
		}
		// now, s is insertion point: the index of the first element greater than the search value.
		// in Java, ~x are equals to (-x)-1
		// -(s) - 1 ---> (-s)-1 ---> ~s
		return ~s;
	}

	/**
	 * Searches a range for the specified value.
	 * @param s	the index of the first element (inclusive) to be searched
	 * @param e	the index of the last element (exclusive) to be searched
	 * @param value	the value to be searched for
	 * @return	index of the search value, if it is contained in this instance;
	 * otherwise, the index of the first element greater than the search value, or e if all elements in the range are less than the search value.
	 */
	long binarySearchGE(long s, long e, final long value) {
		e--;
		while (s <= e) {
			long m = (s + e) >>> 1;
			long v = get(m);
			if (value < v) {
				e = m - 1;
			}
			else if (v < value) {
				s = m + 1;
			}
			else {
				return m;
			}
		}
		// now, s is insertion point: the index of the first element greater than the search value.
		return s;
	}

	/**
	 * Searches a range for the specified value.
	 * @param s	the index of the first element (inclusive) to be searched
	 * @param e	the index of the last element (exclusive) to be searched
	 * @param value	the value to be searched for
	 * @return	index of the last one with the search value, if it is contained in this instance;
	 * otherwise, (-(insertion point) - 1).
	 * <br>the insertion point is defined as the point at which the value would be inserted into this:
	 * the index of the first element greater than the search value, or e if all elements in the range are less than the search value.
	 * NOTE that this guarantees that the return value will be >= 0 if and only if the search value is found.
	 */
	long binarySearchLast(long s, long e, final long value) {
		e--;
		while (s <= e) {
			long m = (s + e) >>> 1;
			long v = get(m);
			if (value < v) {
				e = m - 1;
			}
			else if (v < value) {
				s = m + 1;
			}
			else {
				for (; m < e; m++) {
					if (get(m + 1) != value) {
						return m;
					}
				}
				return m;
			}
		}
		// now, s is insertion point: the index of the first element greater than the search value.
		return ~s;
	}

	/**
	 * Searches a range for the specified value.
	 * @param s	the index of the first element (inclusive) to be searched
	 * @param e	the index of the last element (exclusive) to be searched
	 * @param value	the value to be searched for
	 * @return	index of the first one with the search value, if it is contained in this instance;
	 * otherwise, the index of the first element greater than the search value, or e if all elements in the range are less than the search value.
	 */
	long binarySearchGEfirst(long s, long e, final long value) {
		e--;
		while (s <= e) {
			long m = (s + e) >>> 1;
			long v = get(m);
			if (value < v) {
				e = m - 1;
			}
			else if (v < value) {
				s = m + 1;
			}
			else {
				if (m > s) {
					if (get(m-1) != value) {
						return m;
					}
				}
				else {
					return m;
				}
				e = m - 1;
			}
		}
		// now, s is insertion point: the index of the first element greater than the search value.
		return s;
	}

}
