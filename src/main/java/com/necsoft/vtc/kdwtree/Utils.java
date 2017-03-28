/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * Utility class for miscellaneous operations.
 */
class Utils {

	/**
	 * Encode a double value into long value. The encoded long value keeps numerical order of the double value.
	 * @param d	the value to be encoded
	 * @return	encoded value
	 * @see #decodeLD
	 */
	static final long encodeDL(final double d) {
		long l = Double.doubleToRawLongBits(d);
		if (l < 0) {
			return l ^ 0x7fffffffffffffffL;
		}
		return l;
	}

	/**
	 * Decode a encoded long value to double value of the origin.
	 * @param l	the value to decode
	 * @return	double value of the origin
	 * @see #encodeDL
	 */
	static final double decodeLD(long l) {
		if (l < 0) {
			l ^= 0x7fffffffffffffffL;
		}
		return Double.longBitsToDouble(l);
	}

	/**
	 * Returns true if the argument is a finite Double-precision floating-point value, false otherwise (e.g. NaN and infinity).
	 * @param d	the value to be tested
	 * @return	true if the argument is a finite Double-precision floating-point value, false otherwise.
	 */
	static final boolean isFinite(double d) {
		return -Double.MAX_VALUE <= d && d <= Double.MAX_VALUE;
	}

	/**
	 * Validate specified query (hyper)rectangle. Returns true if min <= max, false otherwise. 
	 * @param min	minimum coordinates of (hyper)rectangle; in other words, left-bottom coordinates of (hyper)rectangle, inclusive.
	 * @param max	maximum coordinates of (hyper)rectangle; in other words, right-top coordinates of (hyper)rectangle, inclusive.
	 * @param dim	expected dimensions of min and max
	 * @return	true if min <= max, false otherwise.
	 * @throws IllegalArgumentException If min or max is null. If dimensions of min or max are not equal to dim. If min or max contains not a finite number.
	 */
	static boolean validateOrthogonalRange(double[] min, double[] max, int dim) {
		if (min == null || max == null) {
			throw new IllegalArgumentException("min or max is null.");
		}
		if (min.length != dim || max.length != dim) {
			throw new IllegalArgumentException("dimensions of min or max are not identical to the dimensions of tree.");
		}
		for (int d = 0; d < dim; d++) {
			if (!(isFinite(min[d]) && isFinite(max[d]))) {
				throw new IllegalArgumentException("min or max contains not a finite number");
			}
			if (min[d] > max[d]) {
				return false;
			}
		}
		return true;
	}

}
