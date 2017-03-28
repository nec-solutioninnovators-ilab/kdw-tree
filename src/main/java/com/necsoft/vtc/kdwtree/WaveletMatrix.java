/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;

/**
 * Implementation of WaveletMatrix.
 * <p>NOTICE, can not handle negative number.
 */
class WaveletMatrix implements Serializable {

	private static final long serialVersionUID = 1L;

	private static final boolean DENSE_ONLY = true;

	/** minimum value */
	private int mVmin;

	/** maximum value */
	private int mVmax;

	/** number of elements */
	private int mLength;

	/** depth of tree (number of rows) */
	private int mDepth;

	/** bit-vectors which organizes each rows of WaveletMatrix */
	SuccinctBitVector[] mWM;

	/** number of zero-bits including each bit-vectors */
	int[] mZ;

	/**
	 * Constructor.
	 * Create a new WaveletMatrix instance from array of int.
	 * <p>NOTICE, array elements passed will be replaced.
	 * @param data	values to store. (values range must between 0 and Integer.MAXVALUE)
	 * @exception IllegalArgumentException	if data is null, or empty, or contains negative number
	 */
	public WaveletMatrix(int[] data) {
		build(data, -1);
	}

	/**
	 * For internal use.
	 * Create a new WaveletMatrix instance from array of int. Depth of created WaveletMatrix is set to specified depth, and only store lower depth bits of specified data.
	 * <p>NOTICE, array elements passed will be replaced.
	 * @param data	values to store. (values range must between 0 and Integer.MAXVALUE)
	 * @param depth	depth to store
	 * @exception IllegalArgumentException	if data is null, or empty, or contains negative number, if depth is larger than 31
	 */
	WaveletMatrix(int[] data, int depth) {
		build(data, depth);
	}

	private void build(int[] data, int depth) {
		if (data == null) {
			throw new IllegalArgumentException("data is null");
		}
		if (data.length == 0) {
			throw new IllegalArgumentException("data is empty");
		}
		if (depth > 31) {
			throw new IllegalArgumentException("depth is larger than 31: " + depth);
		}
		mLength = data.length;

		if (mLength == 0) {
			mVmin = -1;
			mVmax = -1;
			mDepth = depth < 0 ? 0 : depth;
		}
		else {
			mVmin = Integer.MAX_VALUE;
			mVmax = 0;
			for (int i = 0; i < mLength; i++) {
				int v = data[i];
				if (v < 0) {
					throw new IllegalArgumentException("data contains negative number");
				}
				if (v < mVmin) {
					mVmin = v;
				}
				if (v > mVmax) {
					mVmax = v;
				}
			}
			if (depth < 0) {
				mDepth = mVmax == 0 ? 1 : 32 - Integer.numberOfLeadingZeros(mVmax);
			}
			else {
				mDepth = depth;
			}
		}

		mWM = new SuccinctBitVector[mDepth];
		mZ = new int[mDepth];

		int[] buf1 = new int[mLength];

		for (int lv = mDepth - 1; lv >= 0; lv--) {
			int len0 = 0;
			int len1 = 0;

			SuccinctBitVector sbv;
			if (DENSE_ONLY) {
				sbv = new DenseSBV(mLength);
			}
			else {
				// experimental code.
				// create bit-vector which size is most minimum.
				SBVBitsEstimator est = new SBVBitsEstimator();
				for (int i = 0; i < mLength; i++) {
					int bit = (data[i] >>> lv) & 1;
					est.append(bit);
				}
				switch (est.bestSizeType()) {
				case DENSE:
					sbv = new DenseSBV(mLength);
					break;
				case SPARSE0:
					sbv = new Sparse0SBV(mLength);
					break;
				case SPARSE1:
					sbv = new Sparse1SBV(mLength);
					break;
				case RRR16:
					sbv = new RRR16SBV(mLength);
					break;
				case BIASED:
					sbv = new BiasedSBV(mLength);
					break;
				case ALL0:
					sbv = new All0SBV(mLength);
					break;
				case ALL1:
					sbv = new All1SBV(mLength);
					break;
				default:
					sbv = new DenseSBV(mLength);
					break;
				}
			}

			for (int i = 0; i < mLength; i++) {
				int value = data[i];
				int bit = (value >>> lv) & 1;
				sbv.append(bit);
				if (bit == 0) {
					data[len0++] = value;
				}
				else {
					buf1[len1++] = value;
				}
			}
			sbv.build();
			mWM[lv] = sbv;
			mZ[lv] = len0;
			System.arraycopy(buf1, 0, data, len0, len1);
		}
	}

	/**
	 * Returns the overall number of bits allocated by this WaveletMatrix.
	 * @return	the overall number of bits
	 */
	public long usedBits() {
		long used = 0L;
		for (int lv = 0; lv < mDepth; lv++) {
			used += mWM[lv].usedBits();
		}
		return used;
	}

	/**
	 * Returns the depth of this WaveletMatrix (number of rows).
	 * @return depth of this WaveletMatrix
	 */
	public int depth() {
		return mDepth;
	}

	/**
	 * Returns the length of this WaveletMatrix (number of elements).
	 * @return length of this WaveletMatrix
	 */
	public int length() {
		return mLength;
	}

	/**
	 * Returns minimum value in this WaveletMatrix.
	 * @return minimum value
	 */
	public int minValue() {
		return mVmin;
	}

	/**
	 * Returns maximum value in this WaveletMatrix.
	 * @return maximum value
	 */
	public int maxValue() {
		return mVmax;
	}

	/**
	 * Returns the value at the specified position.
	 * @param i	position of the value to return
	 * @return	the value at the specified position
	 * @exception IllegalArgumentException	if i is negative or not less than length
	 */
	public int access(final int i) {
		if (i < 0 || i >= mLength) {
			throw new IllegalArgumentException("i is out of range: " + i);
		}

		int c = 0;
		int p = i;

		for (int lv = mDepth - 1; lv >= 0; lv--) {
			SuccinctBitVector sbv = mWM[lv];

			int bit = sbv.access(p);
			c |= (bit << lv);

			p = sbv.rank(bit, p);
			if (bit != 0) {
				p += mZ[lv];
			}
		}

		return c;
	}

	/**
	 * Returns occurrence of specified value c in position range [0, e) (the position 0 is inclusive, the position e is exclusive)
	 * @param c	value
	 * @param e	e of position range [0, e)
	 * @return occurrence of c
	 * @exception IllegalArgumentException	if e is negative, or larger than length.
	 */
	public int rank(final int c, int e) {
		return rank(c, 0, e);
	}

	/**
	 * Returns occurrence of specified value c in position range [s, e) (the position s is inclusive, the position e is exclusive)
	 * @param c	value
	 * @param s	s of position range [s, e) (inclusive)
	 * @param e	e of position range [s, e) (exclusive)
	 * @return occurrence of c
	 * @exception IllegalArgumentException	if s is negative, or e is larger than length, or s is larger than e.
	 */
	public int rank(final int c, int s, int e) {
		if (s < 0) {
			throw new IllegalArgumentException("s is negative: " + s);
		}
		if (e > mLength) {
			throw new IllegalArgumentException("e is larger than length: " + e);
		}
		if (s > e) {
			throw new IllegalArgumentException("s is larger than e: " + s + " " + e);
		}
		if (s == e || mLength == 0) {
			return 0;
		}
		if (c < mVmin || c > mVmax) {
			return 0;
		}

		int start = s;
		int end = e;

		for (int lv = mDepth - 1; lv >= 0; lv--) {
			SuccinctBitVector sbv = mWM[lv];
			int bit = (c >>> lv) & 1;
			start = sbv.rank(bit, start);
			end = sbv.rank(bit, end);
			if (start == end) {
				return 0;
			}
			if (bit != 0) {
				start += mZ[lv];
				end += mZ[lv];
			}
		}

		return end - start;
	}

	/**
	 * Returns occurrence which is less than specified value c in position range [s, e) (the position s is inclusive, the position e is exclusive)
	 * @param c	value
	 * @param s	s of position range [s, e) (inclusive)
	 * @param e	e of position range [s, e) (exclusive)
	 * @return occurrence which is less than c
	 * @exception IllegalArgumentException	if s is negative, or e is larger than length, or s is larger than e.
	 */
	public int ranklt(int c, int s, int e) {
		if (s < 0) {
			throw new IllegalArgumentException("s is negative: " + s);
		}
		if (e > mLength) {
			throw new IllegalArgumentException("e is larger than length: " + e);
		}
		if (s > e) {
			throw new IllegalArgumentException("s is larger than e: " + s + " " + e);
		}
		if (s == e || mLength == 0) {
			return 0;
		}
		if (c <= mVmin) {
			return 0;
		}
		if (c > mVmax) {
			return e - s;
		}

		int result = 0;
		int lv = mDepth - 1;
		// shift bit of c to MSB (sign bit)
		c <<= ~lv;
		for (; lv >= 0; c <<= 1, lv--) {
			SuccinctBitVector sbv = mWM[lv];
			int s1 = sbv.rank1(s);
			int e1 = sbv.rank1(e);
			if (c < 0) {
				// If bit of c is 1, the 0's children are always less than c. So add the number of 0's children to result.
				if (s1 < e1) {
					result += (e - e1) - (s - s1);//e0 - s0;
					s = s1 + mZ[lv];
					e = e1 + mZ[lv];
				}
				else {
					result += e - s;
					break;
				}
			}
			else {
				s -= s1;
				e -= e1;
				if (s >= e) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Returns occurrence which is greater than specified value c in position range [s, e) (the position s is inclusive, the position e is exclusive)
	 * @param c	value
	 * @param s	s of position range [s, e) (inclusive)
	 * @param e	e of position range [s, e) (exclusive)
	 * @return occurrence which is greater than c
	 * @exception IllegalArgumentException	if s is negative, or e is larger than length, or s is larger than e.
	 */
	public int rankgt(int c, int s, int e) {
		if (s < 0) {
			throw new IllegalArgumentException("s is negative: " + s);
		}
		if (e > mLength) {
			throw new IllegalArgumentException("e is larger than length: " + e);
		}
		if (s > e) {
			throw new IllegalArgumentException("s is larger than e: " + s + " " + e);
		}
		if (s == e || mLength == 0) {
			return 0;
		}
		if (c >= mVmax) {
			return 0;
		}
		if (c < mVmin) {
			return e - s;
		}

		int result = 0;
		int lv = mDepth - 1;
		// shift bit of c to MSB (sign bit)
		c <<= ~lv;
		for (; lv >= 0; c <<= 1, lv--) {
			SuccinctBitVector sbv = mWM[lv];
			int s1 = sbv.rank1(s);
			int e1 = sbv.rank1(e);
			if (c < 0) {
				if (s1 >= e1) {
					break;
				}
				s = s1 + mZ[lv];
				e = e1 + mZ[lv];
			}
			else {
				// If bit of c is 0, the 1's children are always greater than c. So add the number of 1's children to result.
				result += e1 - s1;
				s -= s1;
				e -= e1;
				if (s >= e) {
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Returns occurrence which is less than or equal to specified value c in position range [s, e) (the position s is inclusive, the position e is exclusive)
	 * @param c	value
	 * @param s	s of position range [s, e) (inclusive)
	 * @param e	e of position range [s, e) (exclusive)
	 * @return occurrence which is less than or equal to c
	 * @exception IllegalArgumentException	if s is negative, or e is larger than length, or s is larger than e.
	 */
	public int rankle(final int c, final int s, final int e) {
		return e - s - rankgt(c, s, e);
	}

	/**
	 * Returns occurrence which is greater than or equal to specified value c in position range [s, e) (the position s is inclusive, the position e is exclusive)
	 * @param c	value
	 * @param s	s of position range [s, e) (inclusive)
	 * @param e	e of position range [s, e) (exclusive)
	 * @return occurrence which is greater than or equal to c
	 * @exception IllegalArgumentException	if s is negative, or e is larger than length, or s is larger than e.
	 */
	public int rankge(final int c, final int s, final int e) {
		return e - s - ranklt(c, s, e);
	}

	/**
	 * Returns the position of (i + 1)-th occurrence of specified value c.
	 * @param c	value
	 * @param i	occurrence
	 * @return position of (i + 1)-th occurrence of c. if such c is not exists, returns -1.
	 */
	public int select(final int c, final int i) {
		return select(c, i, 0, mLength, true);
	}

	/**
	 * Returns the position of (i + 1)-th occurrence of specified value c in position range [s, e).
	 * @param c	value
	 * @param i	occurrence
	 * @param s	s of position range [s, e) (inclusive)
	 * @param e	e of position range [s, e) (exclusive)
	 * @param fwd	direction of search occurrence. specify true if search forward, false if search backward.
	 * @return position of (i + 1)-th occurrence of c, or -1 if not found the value c satisfying specified condition.
	 * @exception IllegalArgumentException	if s is negative, or e is larger than length, or s is larger than e.
	 */
	public int select(final int c, final int i, int s, int e, final boolean fwd) {
		if (s < 0) {
			throw new IllegalArgumentException("s is negative: " + s);
		}
		if (e > mLength) {
			throw new IllegalArgumentException("e is larger than length: " + e);
		}
		if (s > e) {
			throw new IllegalArgumentException("s is larger than e: " + s + " " + e);
		}
		if (s == e || mLength == 0) {
			return -1;
		}
		if (i < 0 || i >= e - s) {
			return -1;
		}
		if (c < mVmin || c > mVmax) {
			return -1;
		}

		// search c's leaf
		for (int lv = mDepth - 1; lv >= 0; lv--) {
			SuccinctBitVector sbv = mWM[lv];
			int bit = (c >>> lv) & 1;
			s = sbv.rank(bit, s);
			e = sbv.rank(bit, e);
			if (s >= e) {
				return -1;
			}
			if (bit != 0) {
				s += mZ[lv];
				e += mZ[lv];
			}
		}
		// now, [s, e) is range of c's leaf

		int p;
		if (fwd) {
			// search forward
			p = s + i;
			if (p >= e) {
				return -1;
			}
		}
		else {
			// search backward
			p = e - 1 - i;
			if (p < s) {
				return -1;
			}
		}

		for (int lv = 0; lv < mDepth; lv++) {
			SuccinctBitVector sbv = mWM[lv];
			int bit = (c >>> lv) & 1;

			if (bit != 0) {
				p -= mZ[lv];
			}
			p = sbv.select(bit, p);
		}
		return p;
	}

	/**
	 * For internal use.
	 * convert inner-intervals to root-intervals.
	 * @param ilv	level of inner-interval
	 * @param is	start of inner-interval
	 * @param ie	end of inner-interval
	 * @param rootIntervals	output argument. converted root-intervals are stored. 
	 * @param childIntervals	work area. pass IntBuffer instance.
	 * @param parentIntervals	work area. pass IntBuffer instance.
	 */
	void innerInterval2rootIntervals(int ilv, int is, int ie, Intervals rootIntervals, IntBuffer childIntervals, IntBuffer parentIntervals) {

		if (ie - is == 1) {
			// 1-length inner-interval. so, use select operation.
			int p = is;
			for (int lv = ilv + 1; lv < mDepth; lv++) {
				SuccinctBitVector sbv = mWM[lv];
				int cz = mZ[lv];
				if (p < cz) {
					// p is position of 0's child
					p = sbv.select0(p);
				}
				else {
					// p is position of 1's child
					p = sbv.select1(p - cz);
				}
			}
			rootIntervals.addRoot(p);
			return;
		}

		IntBuffer swapTmp;
		childIntervals.clear();
		parentIntervals.clear();

		childIntervals.add(is, ie);

		for (int lv = ilv + 1; lv < mDepth; lv++) {
			SuccinctBitVector sbv = mWM[lv];
			int cz = mZ[lv];
			if (childIntervals.get(0) < cz) {
				// childIntervals are 0's child
				sbv.selectRanges0(childIntervals, 0, childIntervals.length(), 0, parentIntervals);
			}
			else {
				// childIntervals are 1's child
				sbv.selectRanges1(childIntervals, 0, childIntervals.length(), cz, parentIntervals);
			}

			swapTmp = childIntervals;
			childIntervals = parentIntervals;
			parentIntervals = swapTmp;
			parentIntervals.clear();
		}

		int clen = childIntervals.length();
		for (int j = 0; j < clen; j += 2) {
			rootIntervals.addRoot(childIntervals.get(j), childIntervals.get(j+1));
		}

	}

	/**
	 * For internal use.
	 * Search for intervals which values are within range [minimum, maximum] at position range [start, end).
	 * NOTICE, result intervals are root-interval or inner-interval.
	 * @param s	start position to search (inclusive).
	 * @param e	end position to search (exclusive).
	 * @param min	minimum value to search (inclusive).
	 * @param max	maximum value to search (inclusive).
	 * @param treeId	id to set in result intervals.
	 * @param intervalsOut	output argument. result intervals are stored.
	 * @param work1	work area. pass IntBuffer instance.
	 * @param work2	work area. pass IntBuffer instance.
	 */
	void rangeIntervals(int s, int e, int min, int max, int treeId, Intervals intervalsOut, IntBuffer work1, IntBuffer work2) {
		if (s >= mLength || e <= 0 || s >= e) {
			return;
		}
		if (s < 0) {
			s = 0;
		}
		if (e > mLength) {
			e = mLength;
		}

		if (min > mVmax || max < mVmin || min > max) {
			return;
		}
		if (min < mVmin) {
			min = mVmin;
		}
		if (max > mVmax) {
			max = mVmax;
		}

		if (e - s == 1) {
			// 1-length interval
			int newmin = 0;
			int newmax;
			int p = s;
			for (int lv = mDepth - 1; lv >= 0; lv--) {
				SuccinctBitVector sbv = mWM[lv];
				int bit = sbv.access(p);
				newmin |= (bit << lv);
				newmax = newmin | ((1 << lv) - 1);
				if (newmin > max || newmax < min) {
					// out of range
					return;
				}
				else if (newmin >= min && newmax <= max) {
					// in range
					intervalsOut.addRoot(s, e);
					return;
				}
				p = sbv.rank(bit, p);
				if (bit != 0) {
					p += mZ[lv];
				}
			}
			return;
		}

		// work1, used for stack
		work1.clear();
		work1.internal_expand(4 * 4 * mDepth);
		int[] stackBuf = work1.internal_baseArray();
		int stackPtr = 0;

		// work2, used for temporary result
		work2.clear();
		work2.internal_expand(4 * 2 * mDepth);
		int[] resultBuf = work2.internal_baseArray();
		int resultPtr = 0;

		// total length of test passed
		int passCount = 0;

		// push root to stack
		stackBuf[stackPtr++] = 0;
		stackBuf[stackPtr++] = mDepth - 1;
		stackBuf[stackPtr++] = s;
		stackBuf[stackPtr++] = e;

		// repeat until stack are empty
		while (stackPtr > 0) {
			// pop from stack
			int te = stackBuf[--stackPtr];
			int ts = stackBuf[--stackPtr];
			int tlv = stackBuf[--stackPtr];
			int tpath = stackBuf[--stackPtr];

			final int bit = 1 << tlv;
			SuccinctBitVector sbv = mWM[tlv];

			int s1 = sbv.rank1(ts);
			int e1 = sbv.rank1(te);
			int w1 = e1 - s1;
			int s0 = ts - s1;
			int e0 = te - e1;
			int w0 = e0 - s0;

			// test 0's child
			if (w0 > 0) {
				final int newmin = tpath;
				final int newmax = newmin | (bit - 1);
				if (newmin > max || newmax < min) {
					// out of range
				}
				else if (newmin >= min && newmax <= max) {
					// in range
					// store inner-interval of 0's child
					resultBuf[resultPtr++] = s0;
					resultBuf[resultPtr++] = e0;
					resultBuf[resultPtr++] = treeId;
					resultBuf[resultPtr++] = tlv - 1;
					passCount += w0;
				}
				else {
					// push 0's child to stack
					stackBuf[stackPtr++] = newmin;
					stackBuf[stackPtr++] = tlv - 1;
					stackBuf[stackPtr++] = s0;
					stackBuf[stackPtr++] = e0;
				}
			}

			// test 1's child
			if (w1 > 0) {
				final int nZero = mZ[tlv];
				final int newmin = tpath | bit;
				final int newmax = newmin | (bit - 1);
				if (newmin > max || newmax < min) {
					// out of range
				}
				else if (newmin >= min && newmax <= max) {
					// in range
					// store inner-interval of 1's child
					resultBuf[resultPtr++] = nZero + s1;
					resultBuf[resultPtr++] = nZero + e1;
					resultBuf[resultPtr++] = treeId;
					resultBuf[resultPtr++] = tlv - 1;
					passCount += w1;
				}
				else {
					// push 1's child to stack
					stackBuf[stackPtr++] = newmin;
					stackBuf[stackPtr++] = tlv - 1;
					stackBuf[stackPtr++] = nZero + s1;
					stackBuf[stackPtr++] = nZero + e1;
				}
			}
		}

		if (passCount == e - s) {
			// store root-intervals if the length of result intervals are identical to length of given range [s, e)
			intervalsOut.addRoot(s, e);
			return;
		}
		else {
			// store result intervals
			for (int i = 0; i < resultPtr;) {
				intervalsOut.add(resultBuf[i++], resultBuf[i++], resultBuf[i++], resultBuf[i++]);
			}
			return;
		}
	}

}
