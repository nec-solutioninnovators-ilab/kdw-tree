/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * Implementation of Bit-vector which contains sparse one-bits. Internally the position of one-bits is stored.
 * <pre>
 * Constructing sample.
 * SuccinctBitVector sbv = new Sparse1SBV(length); // call constructor specifying length (number of bits)
 * sbv.append(b);	// append bit value (0 or 1).
 * 	:	// (repeat length times)
 * 
 * sbv.build();	// call build() at last.
 * </pre>
 */
class Sparse1SBV implements SuccinctBitVector{

	private static final long serialVersionUID = 1L;

	private int bitLength;
	private int rank0all;
	private int rank1all;

	private Monotone index;

	transient private int appendPos = 0;

	transient private IntBuffer poswork;

	/**
	 * Constructor.
	 * @param length	length of bit-vector
	 */
	public Sparse1SBV(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length=" + length);
		}
		bitLength = length;
		poswork = new IntBuffer();
	}

	@Override
	public void append(int bit) {
		if (appendPos >= bitLength) {
			throw new IllegalStateException("over length.");
		}
		if (bit == 1) {
			poswork.add(appendPos);
			rank1all++;
		}
		else {
			rank0all++;
		}
		appendPos++;
	}

	@Override
	public void build() {
		if (rank0all + rank1all != bitLength) {
			throw new IllegalStateException("append call incomplete.");
		}

		if (rank1all > 0) {
			index = new Monotone(poswork.toArray());
		}
		poswork = null;
	}

	@Override
	public long usedBits() {
		return index != null ? index.usedBits() : 0L;
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
		if (rank1all == 0) {
			return 0;
		}
		return index.contains(i) ? 1 : 0;
	}

	@Override
	public int rank(int b, int i) {
		if (b == 0) {
			return i - rank1(i);
		}
		else {
			return rank1(i);
		}
	}

	@Override
	public int rank0(int i) {
		return i - rank1(i);
	}

	@Override
	public int rank1(int i) {
		if (i < 0 || i > bitLength) {
			throw new IllegalArgumentException("i=" + i);
		}
		if (rank1all == 0) {
			return 0;
		}
		return index.ranklt(i);
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
		if (i < 0 || i >= rank0all) {
			throw new IllegalArgumentException("i=" + i);
		}

		int l = 0;
		int h = rank1all - 1;
		while (l <= h) {
			int m = (l + h) >>> 1;
			int rank = index.access(m) - m;
			if (rank < i) {
				l = m + 1;
			}
			else if (rank > i) {
				h = m - 1;
			}
			else {
				Monotone.SequentialContext ctx = index.new SequentialContext();
				int prevPos = index.sequentialAccessStart(m, ctx);
				for (int j = m + 1; j < rank1all; j++) {
					int pos = index.sequentialAccessNext(ctx);
					if (pos != prevPos + 1) {
						return prevPos + 1;
					}
					prevPos = pos;
				}
				// now, prevPos indicate position of last one-bit
				prevPos++;
				return prevPos;
			}
		}
		// now, l indicate first element over i.

		return i + l;
	}

	@Override
	public int select1(int i) {
		if (i < 0 || i >= rank1all) {
			throw new IllegalArgumentException("i=" + i);
		}
		return index.access(i);
	}

	@Override
	public void selectRanges0(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		if (index == null) {
			for (int j = begin; j < end; j++) {
				result.add(se.get(j) - bias);
			}
			return;
		}
		int next1 = -1;
		int index0;
		int nextrank0 = -1;
		int rank1 = -1;
		Monotone.AccessCache ac = index.new AccessCache();
		int pos1 = -1;

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
					pos1 = rank1;
					next1 = index.access(pos1++, ac);
					nextrank0 = next1 - rank1;
				}
				else {
					nextrank0 = rank0all;
				}
			}
			else {
				// search bit alternate positions till the end.
				// end
				result.add(next1);
				for (;;) {
					index0 = next1 + 1;
					for (; pos1 < rank1all && index0 == (next1 = index.access(pos1++, ac)); index0++);
					// start
					result.add(index0);
					rank1 = index0 - nextrank0;
					if (rank1 < rank1all) {
						nextrank0 += next1 - index0;
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
						result.add(next1);
					}
				}
			}
		}
		return;
	}

	@Override
	public void selectRanges1(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		if (index == null) {
			return;
		}
		int next1 = -1;
		int index1;
		int nextrank1 = -1;
		int rank0 = -1;
		Monotone.AccessCache ac = index.new AccessCache();
		int pos1 = -1;

		for (int j = begin; j < end; j++) {
			int jlsb = j & 1;
			int value = se.get(j) - bias - jlsb;
			if (value < nextrank1) {
				// start or end
				result.add(rank0 + value + jlsb);
			}
			else if (jlsb == 0) {
				// search start side
				pos1 = value;
				index1 = index.access(pos1++, ac);
				// start
				result.add(index1);
				rank0 = index1 - value;
				if (rank0 < rank0all) {
					int next0 = index1 + 1;
					for (; pos1 < rank1all && next0 == (next1 = index.access(pos1++, ac)); next0++);
					nextrank1 = value + next0 - index1;
				}
				else {
					nextrank1 = rank1all;
				}
			}
			else {
				// search bit alternate positions till the end.
				int next0 = rank0 + nextrank1;
				// end
				result.add(next0);
				for (;;) {
					index1 = next1;
					// start
					result.add(index1);
					rank0 = index1 - nextrank1;
					if (rank0 < rank0all) {
						next0 = index1 + 1;
						for (; pos1 < rank1all && next0 == (next1 = index.access(pos1++, ac)); next0++);
						nextrank1 += next0 - index1;
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
						result.add(next0);
					}
				}
			}
		}
		return;
	}

}
