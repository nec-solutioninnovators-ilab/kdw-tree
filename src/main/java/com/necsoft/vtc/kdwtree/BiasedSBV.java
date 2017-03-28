/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * Implementation of Bit-vector which is less times of bit alternate . Internally the positions of bit alternate 0 to 1 are stored.
 * <pre>
 * constructing sample.
 * SuccinctBitVector sbv = new BiasedSBV(length); // call constructor specifying length (number of bits)
 * sbv.append(b);	// append bit value (0 or 1).
 * 	:	// (repeat length times)
 * 
 * sbv.build();	// call build() at last.
 * </pre>
 */
class BiasedSBV implements SuccinctBitVector {

	private static final long serialVersionUID = 1L;

	private int bitLength;
	private int rank0all;
	private int rank1all;

	transient private int appendPos = 0;

	private Monotone startidx1;
	private Monotone startrank1;
	private Monotone startrank0;
	transient private IntBuffer pos1work;
	transient private IntBuffer rank1work;
	transient private int prevBit = -1;

	/**
	 * Constructor.
	 * @param length	length of bit-vector
	 */
	public BiasedSBV(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length=" + length);
		}
		bitLength = length;
		pos1work = new IntBuffer();
		rank1work = new IntBuffer();
	}

	@Override
	public void append(int bit) {
		if (appendPos >= bitLength) {
			throw new IllegalStateException("over length.");
		}
		if (bit == 1) {
			if (prevBit != 1) {
				pos1work.add(appendPos);
				rank1work.add(rank1all);
			}
			rank1all++;
		}
		else {
			rank0all++;
		}
		prevBit = bit;
		appendPos++;
	}

	@Override
	public void build() {
		if (rank0all + rank1all != bitLength) {
			throw new IllegalStateException("append call incomplete.");
		}
		pos1work.add(appendPos);
		rank1work.add(rank1all);
		if ((pos1work.length() & 1) != 0) {
			pos1work.add(appendPos);
			rank1work.add(rank1all);
		}
		startidx1 = new Monotone(pos1work.toArray());
		int arr1len = rank1work.length() >>> 1;
		int arr0len = arr1len;
		int[] arr0 = new int[arr0len];
		int[] arr1 = new int[arr1len];
		for (int i = 0; i < rank1work.length(); i++) {
			if ((i & 1) == 0) {
				arr0[i>>>1] = pos1work.get(i) - rank1work.get(i);
			}
			else {
				arr1[i>>>1] = rank1work.get(i);
			}
		}
		startrank1 = new Monotone(arr1);
		startrank0 = new Monotone(arr0);
		arr0 = null;
		arr1 = null;
		pos1work = null;
		rank1work = null;
	}

	@Override
	public long usedBits() {
		return startidx1.usedBits() + startrank0.usedBits() + startrank1.usedBits();
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
		Monotone.AccessHint hint = startidx1.new AccessHint();
		int gepos1 = startidx1.find(i, hint);
		if (gepos1 >= 0) {
			return 1;
		}
		int gtpos1 = ~gepos1;
		// now, gtpos1 greater than i

		if (gtpos1 == 0) {
			// now, one-bit not exists before gtpos1
			return 0; 
		}
		int rpos = gtpos1 >>> 1;
		if ((gtpos1 & 1) == 0) {
			int idx0 = startidx1.access(gtpos1 - 1, hint) - startrank1.access(rpos - 1) + startidx1.access(gtpos1, hint) - startrank0.access(rpos);
			return i < idx0 ? 1 : 0;
		}
		else {
			int idx0 = startrank0.access(rpos) + startrank1.access(rpos);
			return i < idx0 ? 1 : 0;
		}
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
		Monotone.AccessHint hint = startidx1.new AccessHint();
		int gepos1 = startidx1.find(i, hint);
		if (gepos1 >= 0) {
			if ((gepos1 & 1) == 0) {
				return i - startrank0.access(gepos1 >>> 1);
			}
			else {
				return startrank1.access(gepos1 >>> 1);
			}
		}
		int gtpos1 = ~gepos1;
		// now, gtpos1 greater than i

		if (gtpos1 == 0) {
			// now, one-bit not exists before gtpos1
			return 0; 
		}
		int rpos = gtpos1 >>> 1;
		if ((gtpos1 & 1) == 0) {
			int prevrank0 = startidx1.access(gtpos1 - 1, hint) - startrank1.access(rpos - 1);
			int nextrank1 = startidx1.access(gtpos1, hint) - startrank0.access(rpos);
			int idx0 = prevrank0 + nextrank1;
			return i < idx0 ? i - prevrank0 : nextrank1;
		}
		else {
			int prevrank0 = startrank0.access(rpos);
			int nextrank1 = startrank1.access(rpos);
			int idx0 = prevrank0 + nextrank1;
			return i < idx0 ? i - prevrank0 : nextrank1;
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

		Monotone.AccessHint hint = startrank0.new AccessHint();
		int gepos0 = startrank0.find(i, hint);
		if (gepos0 >= 0) {
			return i + startrank1.access(gepos0);
		}
		int gtpos0 = ~gepos0;
		// now gtpos0 greater than i

		if (gtpos0 == 0) {
			// now, before first position of bit alternate 0 to 1
			return i;
		}

		int prevrank1 = startrank1.access(gtpos0 - 1);
		int idxpos = gtpos0 << 1;
		Monotone.SequentialContext ctx = startidx1.new SequentialContext();
		int derivedRank0 = startidx1.sequentialAccessStart(idxpos - 1, ctx) - prevrank1;
		if (i < derivedRank0) {
			return prevrank1 + i;
		}

		return (startidx1.sequentialAccessNext(ctx) - startrank0.access(gtpos0, hint)) + i;
	}

	@Override
	public int select1(int i) {
		if (i < 0 || i >= rank1all) {
			throw new IllegalArgumentException("i=" + i);
		}

		Monotone.AccessHint hint = startrank1.new AccessHint();
		int gepos1 = startrank1.find(i, hint);
		if (gepos1 >= 0) {
			return startidx1.access((gepos1 << 1) + 1);
		}
		int gtpos1 = ~gepos1;
		// now gtpos1 greater than i

		int prevrank0 = startrank0.access(gtpos1);
		int idxpos = (gtpos1 << 1) + 1;
		Monotone.SequentialContext ctx = startidx1.new SequentialContext();
		int derivedRank1 = startidx1.sequentialAccessStart(idxpos - 1, ctx) - prevrank0;
		if (i < derivedRank1) {
			return (startidx1.sequentialAccessPrev(ctx) - startrank1.access(gtpos1 - 1, hint)) + i;
		}

		return prevrank0 + i;
	}

	@Override
	public void selectRanges0(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		int nextrank1 = 0;
		int nextnextrank1 = 0;
		int rank0 = Integer.MAX_VALUE;
		int nextrank0 = 0;
		int nextnextrank0 = 0;
		int lastindex1pos = 0;
		Monotone.AccessCache rank0ac = startrank0.new AccessCache();
		Monotone.AccessCache rank1ac = startrank1.new AccessCache();
		Monotone.AccessCache idx1ac = startidx1.new AccessCache();

		for (int j = begin; j < end; j++) {
			int jlsb = j & 1;
			int value = se.get(j) - bias - jlsb;
			if (value >= rank0 && value < nextrank0) {
				// start or end
				result.add(nextrank1 + value + jlsb);
			}
			else if (value >= nextrank0 && value < nextnextrank0) {
				if (jlsb == 0) {
					// start
					result.add(nextnextrank1 + value);
				}
				else {
					// end
					result.add(nextrank1 + nextrank0);
					// start
					result.add(nextrank0 + nextnextrank1);
					// end
					result.add(nextnextrank1 + value + 1);
				}
				nextrank1 = nextnextrank1;
				rank0 = nextrank0;
				nextrank0 = nextnextrank0;
				nextnextrank0 = 0;
			}
			else if (jlsb == 0) {
				// search start side
				int gepos0 = startrank0.find(value);
				if (gepos0 >= 0) {
					rank0 = value;
					nextrank1 = startrank1.access(gepos0, rank1ac);
					int nextindex1 = startidx1.access((gepos0 << 1) + 1, idx1ac);
					lastindex1pos = (gepos0 << 1) + 1;
					nextrank0 = nextindex1 - nextrank1;
					nextnextrank0 = 0;
				}
				else {
					gepos0 = ~gepos0;
					if (gepos0 == 0) {
						rank0 = 0;
						nextrank1 = 0;
						nextrank0 = startrank0.access(gepos0, rank0ac);
						nextnextrank0 = 0;
						lastindex1pos = 0;
					}
					else {
						int index1 = startidx1.access((gepos0 << 1) - 1, idx1ac);
						lastindex1pos = (gepos0 << 1) - 1;
						int rank1 = startrank1.access(gepos0 - 1, rank1ac);
						rank0 = index1 - rank1;
						if (value < rank0) {
							int prevrank0 = startrank0.access(gepos0 - 1, rank0ac);
							if (gepos0 < startrank0.length()) {
								nextnextrank0 = startrank0.access(gepos0, rank0ac);
								nextnextrank1 = startidx1.access(gepos0 << 1, idx1ac) - nextnextrank0;
								lastindex1pos = (gepos0 << 1);
							}
							else {
								nextnextrank0 = rank0all;
								nextnextrank1 = rank1all;
							}
							nextrank0 = rank0;
							nextrank1 = rank1;
							rank0 = prevrank0;
						}
						else {
							nextrank0 = startrank0.access(gepos0, rank0ac);
							int nextindex1 = startidx1.access(gepos0 << 1, idx1ac);
							lastindex1pos = (gepos0 << 1);
							nextrank1 = nextindex1 - nextrank0;
							nextnextrank0 = 0;
						}
					}
				}
				// start
				result.add(nextrank1 + value);
			}
			else {
				// search bit alternate positions till the end.
				int lastend;
				// end
				result.add(lastend = nextrank1 + nextrank0);
				if (nextnextrank0 > 0) {
					// start
					result.add(nextrank0 + nextnextrank1);
					// end
					result.add(lastend = nextnextrank1 + nextnextrank0);
				}
				int index1pos = lastindex1pos;
				int index1;
				int r0, nr0, nr1;
				for (;; index1pos++) {
					int previndex1 = index1pos > 0 ? startidx1.access(index1pos - 1, idx1ac) : 0;
					index1 = startidx1.access(index1pos, idx1ac);
					if (index1 <= lastend) {
						continue;
					}
					if ((index1pos & 1) == 0) {
						r0 = previndex1 - startrank1.access((index1pos >>> 1) - 1, rank1ac);
						nr0 = startrank0.access(index1pos >>> 1, rank0ac);
						nr1 = index1 - nr0;
						// start
						result.add(nr1 + r0);

						if (value < nr0) {
							// end
							result.add(nr1 + value + 1);
							nextrank1 = nr1;
							rank0 = r0;
							nextrank0 = nr0;
							nextnextrank0 = 0;
							lastindex1pos = index1pos;
							break;
						}
						else {
							// end
							result.add(index1);
						}
					}
					else {
						r0 = startrank0.access(index1pos >>> 1, rank0ac);
						nr1 = startrank1.access(index1pos >>> 1, rank1ac);
						nr0 = index1 - nr1;
						// start
						result.add(nr1 + r0);

						if (value < nr0) {
							// end
							result.add(nr1 + value + 1);
							nextrank1 = nr1;
							rank0 = r0;
							nextrank0 = nr0;
							nextnextrank0 = 0;
							lastindex1pos = index1pos;
							break;
						}
						else {
							// end
							result.add(index1);
						}
					}
				}
			}
		}
		return;
	}

	@Override
	public void selectRanges1(IntBuffer se, int begin, int end, int bias, IntBuffer result) {
		int rank0 = 0;
		int nextrank0 = 0;
		int rank1 = Integer.MAX_VALUE;
		int nextrank1 = 0;
		int nextnextrank1 = 0;
		int lastindex1pos = 0;
		Monotone.AccessCache rank0ac = startrank0.new AccessCache();
		Monotone.AccessCache rank1ac = startrank1.new AccessCache();
		Monotone.AccessCache idx1ac = startidx1.new AccessCache();

		for (int j = begin; j < end; j++) {
			int jlsb = j & 1;
			int value = se.get(j) - bias - jlsb;
			if (value >= rank1 && value < nextrank1) {
				// start or end
				result.add(rank0 + value + jlsb);
			}
			else if (value >= nextrank1 && value < nextnextrank1) {
				if (jlsb == 0) {
					// start
					result.add(nextrank0 + value);
				}
				else {
					// end
					result.add(rank0 + nextrank1);
					// start
					result.add(nextrank1 + nextrank0);
					// end
					result.add(nextrank0 + value + 1);
				}
				rank0 = nextrank0;
				rank1 = nextrank1;
				nextrank1 = nextnextrank1;
				nextnextrank1 = 0;
			}
			else if (jlsb == 0) {
				// search start side
				int gepos1 = startrank1.find(value);
				if (gepos1 >= 0) {
					rank1 = value;
					int index1 = startidx1.access((gepos1 << 1) + 1, idx1ac);
					rank0 = index1 - rank1;
					int nextindex1 = startidx1.access((gepos1 << 1) + 2, idx1ac);
					lastindex1pos = (gepos1 << 1) + 2;
					nextrank1 = nextindex1 - startrank0.access(gepos1 + 1, rank0ac);
					nextnextrank1 = 0;
				}
				else {
					gepos1 = ~gepos1;
					rank0 = startrank0.access(gepos1, rank0ac);
					int index1prev = gepos1 != 0 ? startidx1.access((gepos1 << 1) - 1, idx1ac) : -1;
					int index1 = startidx1.access(gepos1 << 1, idx1ac);
					lastindex1pos = (gepos1 << 1);
					rank1 = index1 - rank0;
					if (value < rank1) {
						nextrank0 = rank0;
						nextrank1 = rank1;
						rank1 = startrank1.access(gepos1 - 1, rank1ac);
						nextnextrank1 = startrank1.access(gepos1, rank1ac);
						rank0 = index1prev - rank1;
					}
					else {
						nextrank1 = startrank1.access(gepos1, rank1ac);
						nextnextrank1 = 0;
					}
				}
				// start
				result.add(rank0 + value);
			}
			else {
				// search bit alternate positions till the end.
				int lastend;
				// end
				result.add(lastend = rank0 + nextrank1);
				if (nextnextrank1 > 0) {
					// start
					result.add(nextrank1 + nextrank0);
					// end
					result.add(lastend = nextrank0 + nextnextrank1);
				}
				int index1pos = lastindex1pos;
				int index1;
				int nr1, r0;
				for (;; index1pos++) {
					index1 = startidx1.access(index1pos, idx1ac);
					if (index1 <= lastend) {
						continue;
					}
					// start
					result.add(index1);
					if ((index1pos & 1) == 0) {
						r0 = startrank0.access(index1pos >>> 1, rank0ac);
						nr1 = startrank1.access(index1pos >>> 1, rank1ac);
						if (value < nr1) {
							// end
							result.add(r0 + value + 1);
							rank0 = r0;
							rank1 = index1 - r0;
							nextrank1 = nr1;
							nextnextrank1 = 0;
							lastindex1pos = index1pos;
							break;
						}
						else {
							// end
							result.add(r0 + nr1);
						}
					}
					else {
						r0 = index1 - startrank1.access(index1pos >>> 1, rank1ac);
						nr1 = startidx1.access(index1pos + 1, idx1ac)
								- startrank0.access((index1pos >>> 1) + 1, rank0ac);
						if (value < nr1) {
							// end
							result.add(r0 + value + 1);
							rank0 = r0;
							rank1 = index1 - r0;
							nextrank1 = nr1;
							nextnextrank1 = 0;
							lastindex1pos = index1pos + 1;
							break;
						}
						else {
							// end
							result.add(r0 + nr1);
						}
					}
				}
			}
		}
		return;
	}

}
