/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;

/**
 * An interface providing common operations of bit-vector.
 */
interface SuccinctBitVector extends Serializable {

	/**
	 * Append bit 0 or 1.
	 * @param b	bit to append. 0 or 1
	 */
	public void append(int b);

	/**
	 * Build internal structures for query operations (access, rank, select, and so on).
	 */
	public void build();

	/**
	 * Returns the overall number of bits allocated by this bit-vector.
	 * @return	the overall number of bits allocated by this bit-vector.
	 */
	public long usedBits();

	/**
	 * Returns the length of this bit-vector (number of bits).
	 * @return	length of this bit-vector (number of bits)
	 */
	public int length();

	/**
	 * Returns the bit value at the specified position. An position ranges from 0 to length() - 1.
	 * @param i	position to access.
	 * @return	the bit value at the specified index. 0 or 1.
	 */
	public int access(int i);

	/**
	 * Returns number of the specified bit value in the specified position range [0, i). The position range [0, i) begins at 0 and ends at i - 1.
	 * @param b	bit value. 0 or 1.
	 * @param i	i of position range [0, i)
	 * @return	number of the specified bit value in the specified position range
	 */
	public int rank(int b, int i);

	/**
	 * Returns number of zero-bits in the specified position range [0, i). The position range [0, i) begins at 0 and ends at i - 1.
	 * @param i	i of the position range [0, i)
	 * @return	number of zero-bits in the specified position range
	 */
	public int rank0(int i);

	/**
	 * Returns number of one-bits in the specified position range [0, i). The position range [0, i) begins at 0 and ends at i - 1.
	 * @param i	i of the position range [0, i)
	 * @return	number of one-bits in the specified position range
	 */
	public int rank1(int i);

	/**
	 * Returns number of zero-bits in this bit-vector.
	 * @return	number of zero-bits
	 */
	public int rank0();

	/**
	 * Returns number of one-bits in this bit-vector.
	 * @return	number of one-bits
	 */
	public int rank1();

	/**
	 * Returns the position of the (i + 1)-th occurrence of the specified bit value.
	 * @param b	bit value. 0 or 1.
	 * @param i	
	 * @return	the position of (i + 1)-th occurrence of the specified bit value
	 */
	public int select(int b, int i);

	/**
	 * Returns the position of the (i + 1)-th occurrence of zero-bit.
	 * @param i	
	 * @return	the position of the (i + 1)-th occurrence of zero-bit.
	 */
	public int select0(int i);

	/**
	 * Returns the position of the (i + 1)-th occurrence of one-bit.
	 * @param i	
	 * @return	the position of the (i + 1)-th occurrence of one-bit.
	 */
	public int select1(int i);

	/**
	 * Multiple operations of select0().
	 * Search for positions of multiple zero-bits occurrences.
	 * The occurrences(argument se) and positions(argument result) are range [start, end) form. (start inclusive, end exclusive)
	 * NOTICE the occurrences must be sorted, otherwise result are undefined.
	 * <pre style="font-family:'MS Mincho','Terminal','monospace';">EXAMPLE
	 * occurrences	01     23 4  56789  012 3 4 5678901    2
	 * bit-sequence	0011111001011000001100010101000000011110
	 * positions	0123456789012345678901234567890123456789
	 * specified occurrence ranges [3,7) [8,10)
	 * Then, results are [8,9) [10,11) [13,15) [16,18) .</pre>
	 * @param se	IntBuffer which stores multiple occurrences in range [start, end) form. Range start must be stored even position. Range end must be stored odd position. Range must be sorted.
	 * @param begin	the index of the first element (inclusive) to be processed in argument se
	 * @param end	the index of the last element (exclusive) to be processed in argument se
	 * @param bias	bias value for occurrence. The occurrences(argument se) - bias are used in search process.
	 * @param result	IntBuffer to store results. Range start are stored even position. Range end are stored odd position.
	 */
	public void selectRanges0(IntBuffer se, int begin, int end, int bias, IntBuffer result);

	/**
	 * Multiple operations of select1().
	 * Search for positions of multiple one-bits occurrences.
	 * The occurrences(argument se) and positions(argument result) are range [start, end) form. (start inclusive, end exclusive)
	 * NOTICE the occurrences must be sorted, otherwise result are undefined.
	 * <pre style="font-family:'MS Mincho','Terminal','monospace';">EXAMPLE
	 * occurrences	  01234  5 67     89   0 1 2       3456 
	 * bit-sequence	0011111001011000001100010101000000011110
	 * positions	0123456789012345678901234567890123456789
	 * specified occurrence ranges	[3,7) [8,10)
	 * Then, results are [5,7) [9,10) [11,12) [18,20) .</pre>
	 * @param se	IntBuffer which stores multiple occurrences in range [start, end) form. Range start must be stored even position. Range end must be stored odd position. Range must be sorted.
	 * @param begin	the index of the first element (inclusive) to be processed in argument se
	 * @param end	the index of the last element (exclusive) to be processed in argument se
	 * @param bias	bias value for occurrence. The occurrences(argument se) - bias are used in search process.
	 * @param result	IntBuffer to store results. Range start are stored even position. Range end are stored odd position.
	 */
	public void selectRanges1(IntBuffer se, int begin, int end, int bias, IntBuffer result);
}
