/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * Implementation of Bit-vector which contains one-bits only.
 * <pre>
 * Constructing sample.
 * SuccinctBitVector sbv = new All1SBV(length); // call constructor specifying length (number of bits)
 * // Calling append(), build() are unnecessary.
 * </pre>
 */
class All1SBV implements SuccinctBitVector{

	private static final long serialVersionUID = 1L;

	private int bitLength;

	/**
	 * Constructor.
	 * @param length	length of bit-vector
	 */
	public All1SBV(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length=" + length);
		}
		bitLength = length;
	}

	@Override
	public void append(int bit) {
	}

	@Override
	public void build() {
	}

	@Override
	public long usedBits() {
		return 0L;
	}

	@Override
	public int length() {
		return bitLength;
	}

	@Override
	public int access(int i) {
		if (i < 0 || i >= bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return 1;
	}

	@Override
	public int rank(int b, int i) {
		if (i < 0 || i > bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return b == 0 ? 0 : i;
	}

	@Override
	public int rank0(int i) {
		if (i < 0 || i > bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return 0;
	}

	@Override
	public int rank1(int i) {
		if (i < 0 || i > bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return i;
	}

	@Override
	public int rank0() {
		return 0;
	}

	@Override
	public int rank1() {
		return bitLength;
	}

	@Override
	public int select(int b, int i) {
		if (b == 0) {
			return select0(i);
		}
		else {
			return select1(i);
		}
	}

	@Override
	public int select0(int i) {
		throw new IllegalArgumentException("i=" + i);
	}

	@Override
	public int select1(int i) {
		if (i < 0 || i >= bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		return i;
	}

	@Override
	public void selectRanges0(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		return;
	}

	@Override
	public void selectRanges1(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		for (int j = begin; j < end; j++) {
			result.add(se.get(j) - bias);
		}
		return;
	}

}
