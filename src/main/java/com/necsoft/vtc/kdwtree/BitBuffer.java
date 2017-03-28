/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;

/**
 * variable size structure which stores bits.
 */
class BitBuffer implements Serializable {

	private static final long serialVersionUID = 1L;

	private final static float INC_RATE = 1.2f; 
	private int[] mBits;
	private int length = 0;

	/**
	 * Constructor.
	 * @param initialCapacity	initial capacity in bits.
	 */
	BitBuffer(int initialCapacity) {
		int elms = (initialCapacity + 31) >>> 5;
		mBits = new int[elms];
	}
	/**
	 * Constructor. create instance with initial capacity 8192 bits. 
	 * @param initialCapacity	initial capacity in bits.
	 */
	BitBuffer() {
		this(8192);
	}
	private void moreElements() {
		int needSize = (length + 31) >>> 5;
		if (mBits == null) {
			int iniSize = 8192;
			mBits = new int[needSize > iniSize ? needSize : iniSize];
		}
		else if (needSize > mBits.length) {
			int incSize = (int)(mBits.length * INC_RATE);
			int[] newBits = new int[needSize > incSize ? needSize : incSize];
			System.arraycopy(mBits, 0, newBits, 0, mBits.length);
			mBits = newBits;
		}
	}
	/**
	 * Trims the capacity of this instance to be the current size.
	 */
	void trim() {
		int needSize = (length + 31) >>> 5;
		if (mBits == null) {
			int iniSize = 8192;
			mBits = new int[needSize > iniSize ? needSize : iniSize];
		}
		else if (needSize < mBits.length) {
			int[] newBits = new int[needSize];
			System.arraycopy(mBits, 0, newBits, 0, needSize);
			mBits = newBits;
		}
	}
	/**
	 * Returns the number of bits in this instance.
	 * @return the number of bits
	 */
	int length() {
		return length;
	}
	/**
	 * Appends the specified bit to the end of this.
	 * @param bit	bit to be appended
	 */
	void add(int bit) {
		set(length, bit);
	}
	/**
	 * Appends lower n bits of the specified value to the end of this.
	 * @param value	value to be appended. lower n bits are used.
	 * @param n	number of bits to append
	 */
	void addnbits(int v, int n) {
		if (n < 1 || n > 32) {
			throw new IllegalArgumentException("n=" + n);
		}
		int index = length;
		length += n;
		moreElements();
		for (int j = n - 1; j >= 0; j--) {
			if ((v >>> j & 1) == 0) {
				mBits[index >>> 5] &= ~(0x80000000 >>> index);
			}
			else {
				mBits[index >>> 5] |= 0x80000000 >>> index;
			}
			index++;
		}
	}
	/**
	 * Replaces a bit at the specified position in this with the specified bit.
	 * @param index	index of the bit to replace
	 * @param bit	bit to be stored at the specified position
	 */
	void set(int index, int bit) {
		if (index < 0) {
			throw new IllegalArgumentException("index=" + index);
		}
		if (index >= length) {
			length = index + 1;
		}
		moreElements();
		if (bit == 0) {
			mBits[index >>> 5] &= ~(0x80000000 >>> index);
		}
		else {
			mBits[index >>> 5] |= 0x80000000 >>> index;
		}
	}
	/**
	 * Returns a bit at the specified position in this.
	 * @param index	position of a bit to return
	 * @return a bit at the specified position.
	 */
	int get(int index) {
		if (index < 0 || index >= length) {
			throw new IllegalArgumentException("index=" + index);
		}
		return (mBits[index >>> 5] >>> ~index) & 1;
	}
	/**
	 * Returns n bits at the specified position in this.
	 * @param index	position of the first bit to return
	 * @param n	number of bits to return
	 * @return n bits at the specified position.
	 * returned n bits are right-aligned.
	 * bit at specified index are returned at n-th bit from right.
	 * bit at (index + n - 1) are returned at right-most bit (LSB).
	 */
	int getnbits(int index, int n) {
		if (index < 0 || index >= length) {
			throw new IllegalArgumentException("index=" + index);
		}
		if (n <= 0 || n > 32) {
			throw new IllegalArgumentException("n=" + n);
		}

		int bitsIdx = index >>> 5;
		int leftOfs = index & 0x1f;
		int rightOfs = 32 - leftOfs - n;
		if (rightOfs < 0) {
			return (
					(mBits[bitsIdx] << (-rightOfs))
					| (mBits[bitsIdx+1] >>> (32 + rightOfs))
			) & ((1 << n) - 1);
		}
		else {
			return (
					mBits[bitsIdx] >>> (rightOfs)
			) & ((1 << n) - 1);
		}
	}

}
