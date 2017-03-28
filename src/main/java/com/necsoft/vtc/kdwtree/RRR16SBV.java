/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import static com.necsoft.vtc.kdwtree.SBVTables.*;

/**
 * Implementation of Bit-vector based known RRR algorithm (block size is 16 bits).
 * <pre>
 * constructing sample.
 * SuccinctBitVector sbv = new RRR16SBV(length); // call constructor specifying length (number of bits)
 * sbv.append(b);	// append bit value (0 or 1).
 * 	:	// (repeat length times)
 * 
 * sbv.build();	// call build() at last.
 * </pre>
 */
class RRR16SBV implements SuccinctBitVector {

	private static final long serialVersionUID = 1L;

	private int bitLength;
	private int rank0all;
	private int rank1all;

	transient private int[] workbits;
	transient private int appendPos = 0;
	private FixedBitsArray mClassBits;
	private BitBuffer mOffsetBits;
	private Monotone mOffsetPos;
	private Monotone mRank1;

	/**
	 * Constructor.
	 * @param length	length of bit-vector
	 */
	public RRR16SBV(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length=" + length);
		}
		bitLength = length;
		workbits = new int[(length + 0x1f) >>> 5];
		appendPos = 0;
	}

	@Override
	public void append(int bit) {
		if (appendPos >= bitLength) {
			throw new IllegalStateException("over length.");
		}
		if (bit == 0) {
			workbits[appendPos >>> 5] &= ~(0x80000000 >>> appendPos);
		}
		else {
			workbits[appendPos >>> 5] |= 0x80000000 >>> appendPos;
		}
		appendPos++;
	}

	@Override
	public void build() {
		if (appendPos < bitLength) {
			throw new IllegalStateException("append call incomplete.");
		}
		buildClassOffset();
		workbits = null;
	}

	@Override
	public long usedBits() {
		return (long)mClassBits.length() * (long)mClassBits.bitSize()
				+ (long)mOffsetBits.length()
				+ mOffsetPos.usedBits()
				+ mRank1.usedBits();
	}

	private void buildClassOffset() {
		mClassBits = new FixedBitsArray((bitLength + 0xf) >>> 4, 4);
		mOffsetBits = new BitBuffer((bitLength + 0xf) >>> 4);
		int[] workOfsPos = new int[(bitLength + 0xff) >>> 8];
		int[] workRank1 = new int[(bitLength + 0xff) >>> 8];

		int offsetPos = 0;
		int rank1 = 0;
		for (int i = 0; i < bitLength; i += 16) {
			if ((i & 0xff) == 0) {
				workOfsPos[i >>> 8] = offsetPos;
				workRank1[i >>> 8] = rank1;
			}

			int word = (i & 0x1f) == 0 ? workbits[i>>>5] >>> 16 : workbits[i>>>5] & 0xffff;
			int class16 = R1_16[word];
			// consume 5 + 1 bit for storing class 16/offset 0 is inefficiently .
			// so, convert class 16/offset 0 to class 0/offset 1 .
			mClassBits.set(i >>> 4, class16 & 0xf);
			int offset16 = class16 == 16 ? 1 : RRR16_VAL2OFS[word];
			int width16 = RRR16_OFFSET_WIDTH[class16];
			mOffsetBits.addnbits(offset16, width16);

			offsetPos += width16;
			rank1 += class16;
		}
		mOffsetBits.trim();
		mOffsetPos = new Monotone(workOfsPos);
		mRank1 = new Monotone(workRank1);

		rank1all = rank1;
		rank0all = bitLength - rank1;
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

		// to calculate offset start position, summing offset bit width
		// from first block of large block containing i to previous block containing i
		int clsIdx = i >>> 4;
		int ofsPos = mOffsetPos.access(i >>> 8);
		for (int j = (i & 0xffffff00) >>> 4; j < clsIdx; j++) {
			ofsPos += RRR16_OFFSET_WIDTH[mClassBits.get(j)];
		}

		int cls = mClassBits.get(clsIdx);
		int ofs = mOffsetBits.getnbits(ofsPos, RRR16_OFFSET_WIDTH[cls]);
		// convert class 0/offset 1 to class 16/offset 0 (0xffff)
		int val = cls == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[cls][ofs] & 0xffff;
		return val >>> (~i & 0xf) & 1;
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
		if (i == bitLength) {
			return rank1all;
		}

		// to calculate offset start position, summing offset bit width
		// from first block of large block containing i to previous block containing i .
		// and calculate rank simultaneously.
		int clsIdx = i >>> 4;
		int ofsPos = mOffsetPos.access(i >>> 8);
		int rank1 = mRank1.access(i >>> 8);
		for (int j = (i & 0xffffff00) >>> 4; j < clsIdx; j++) {
			int cls = mClassBits.get(j);

			if (cls == 0 && mOffsetBits.get(ofsPos) == 1) {
				// class 0/offset 1 is class 16/offset 0 (0xffff). so adjust rank.
				rank1 += 16;
			}
			else {
				rank1 += cls;
			}

			ofsPos += RRR16_OFFSET_WIDTH[cls];
		}

		int cls = mClassBits.get(clsIdx);
		int ofs = mOffsetBits.getnbits(ofsPos, RRR16_OFFSET_WIDTH[cls]);
		// convert class 0/offset 1 to class 16/offset 0 (0xffff)
		int val = cls == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[cls][ofs] & 0xffff;
		val &= ~(0xffff >>> (i & 0xf));
		return rank1 + R1_16[val];
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

		int ranklt;
		BS: {
			int i_1 = i + 1;
			int s = 0;
			int e = mRank1.length() - 1;
			while (s <= e) {
				int m = (s + e) >>> 1;
				int r0 = (m << 8) - mRank1.access(m);
				if (i_1 < r0) {
					e = m - 1;
				}
				else if (i_1 > r0) {
					s = m + 1;
				}
				else {
					for (; m > s; m--) {
						if (((m - 1) << 8) - mRank1.access(m - 1) != i_1) {
							ranklt = m;
							break BS;
						}
					}
					ranklt = m;
					break BS;
				}
			}
			// now, s indicate first element over i_1
			ranklt = s;
		}
		int rank0pos = ranklt - 1;
		int rank0 = (rank0pos << 8) - mRank1.access(rank0pos);
		int ofsPos = mOffsetPos.access(rank0pos);

		int foundClass = -1;
		int foundIndex = -1;
		int j;
		int js = rank0pos << 4;
		int je = js + 16;
		for (j = js; j < je; j++) {
			int nextRank0;
			int cls = mClassBits.get(j);
			if (cls == 0 && mOffsetBits.get(ofsPos) == 1) {
				// class 0/offset 1 is class 16/offset 0 (0xffff). so adjust rank.
				nextRank0 = rank0;// + 16 - 16;
			}
			else {
				nextRank0 = rank0 + 16 - cls;
			}
			if (nextRank0 > i) {
				foundClass = cls;
				foundIndex = j << 4;
				break;
			}
			rank0 = nextRank0;
			ofsPos += RRR16_OFFSET_WIDTH[cls];
		}
		assert foundClass >= 0 : i;
		int ofs = mOffsetBits.getnbits(ofsPos, RRR16_OFFSET_WIDTH[foundClass]);
		// convert class 0/offset 1 to class 16/offset 0 (0xffff)
		int val = foundClass == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[foundClass][ofs] & 0xffff;
		return foundIndex + S1_16[((~val & 0xffff) << 4) + (i - rank0)];
	}

	@Override
	public int select1(int i) {
		if (i < 0 || i >= rank1all) {
			throw new IllegalArgumentException("i=" + i);
		}

		// search from large block containing (i + 1)-th one-bit .
		int rank1pos = mRank1.ranklt(i + 1) - 1;
		int rank1 = mRank1.access(rank1pos);
		int ofsPos = mOffsetPos.access(rank1pos);

		int foundClass = -1;
		int foundIndex = -1;
		int j;
		int js = rank1pos << 4;
		int je = js + 16;
		for (j = js; j < je; j++) {
			int nextRank1;
			int cls = mClassBits.get(j);
			if (cls == 0 && mOffsetBits.get(ofsPos) == 1) {
				// class 0/offset 1 is class 16/offset 0 (0xffff). so adjust rank.
				nextRank1 = rank1 + 16;
			}
			else {
				nextRank1 = rank1 + cls;
			}
			if (nextRank1 > i) {
				foundClass = cls;
				foundIndex = j << 4;
				break;
			}
			rank1 = nextRank1;
			ofsPos += RRR16_OFFSET_WIDTH[cls];
		}
		assert foundClass >= 0 : i;
		int ofs = mOffsetBits.getnbits(ofsPos, RRR16_OFFSET_WIDTH[foundClass]);
		// convert class 0/offset 1 to class 16/offset 0 (0xffff)
		int val = foundClass == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[foundClass][ofs] & 0xffff;
		return foundIndex + S1_16[(val << 4) + (i - rank1)];
	}

	int next0(int i) {
		int s = i + 1;
		// to calculate offset start position, summing offset bit width
		// from first block of large block containing s to previous block containing s .
		int clsIdx = s >>> 4;
		int ofsPos = mOffsetPos.access(s >>> 8);
		for (int j = (s & 0xffffff00) >>> 4; j < clsIdx; j++) {
			ofsPos += RRR16_OFFSET_WIDTH[mClassBits.get(j)];
		}
		int cls = mClassBits.get(clsIdx);
		int ofsWidth = RRR16_OFFSET_WIDTH[cls];
		int ofs = mOffsetBits.getnbits(ofsPos, ofsWidth);
		// convert class 0/offset 1 to class 16/offset 0 (0xffff)
		int bits = cls == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[cls][ofs] & 0xffff;

		bits = ~bits & 0xffff >>> (s & 0xf);
		s &= 0xfffffff0;

		int loop = 0;
		for (;;) {
			if (bits != 0) {
				return s + S1_16[bits << 4];
			}
			s += 16;
			if (++loop >= 8) {
				return select0(rank0(i+1));
			}
			if (s > bitLength) {
				throw new IllegalArgumentException("not found. i=" + i);
			}
			clsIdx++;
			ofsPos += ofsWidth;
			cls = mClassBits.get(clsIdx);
			ofsWidth = RRR16_OFFSET_WIDTH[cls];
			ofs = mOffsetBits.getnbits(ofsPos, ofsWidth);
			// convert class 0/offset 1 to class 16/offset 0 (0xffff)
			bits = cls == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[cls][ofs] & 0xffff;
			bits = ~bits & 0xffff;
		}
	}

	int next1(int i) {
		int s = i + 1;
		// to calculate offset start position, summing offset bit width
		// from first block of large block containing s to previous block containing s .
		int clsIdx = s >>> 4;
		int ofsPos = mOffsetPos.access(s >>> 8);
		for (int j = (s & 0xffffff00) >>> 4; j < clsIdx; j++) {
			ofsPos += RRR16_OFFSET_WIDTH[mClassBits.get(j)];
		}
		int cls = mClassBits.get(clsIdx);
		int ofsWidth = RRR16_OFFSET_WIDTH[cls];
		int ofs = mOffsetBits.getnbits(ofsPos, ofsWidth);
		// convert class 0/offset 1 to class 16/offset 0 (0xffff)
		int bits = cls == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[cls][ofs] & 0xffff;

		bits = bits & 0xffff >>> (s & 0xf);
		s &= 0xfffffff0;

		int loop = 0;
		for (;;) {
			if (bits != 0) {
				return s + S1_16[bits << 4];
			}
			s += 16;
			if (++loop >= 8) {
				return select1(rank1(i+1));
			}
			if (s > bitLength) {
				throw new IllegalArgumentException("not found. i=" + i);
			}
			clsIdx++;
			ofsPos += ofsWidth;
			cls = mClassBits.get(clsIdx);
			ofsWidth = RRR16_OFFSET_WIDTH[cls];
			ofs = mOffsetBits.getnbits(ofsPos, ofsWidth);
			// convert class 0/offset 1 to class 16/offset 0 (0xffff)
			bits = cls == 0 && ofs == 1 ? 0xffff : RRR16_OFS2VAL[cls][ofs] & 0xffff;
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

}
