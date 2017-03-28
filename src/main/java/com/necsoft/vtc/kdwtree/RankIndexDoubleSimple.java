/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Simple implementation of RankIndex.
 */
class RankIndexDoubleSimple implements RankIndex, Serializable {

	private static final long serialVersionUID = 1L;

	private long[] reals;

	/**
	 * Constructor
	 * @param sortedValues	number sequence to store. must be sorted.
	 * @param cardinality	cardinality of number sequence.
	 */
	RankIndexDoubleSimple(long[] sortedValues, int cardinality) {
		int length = sortedValues.length;
		reals = new long[cardinality];

		reals[0] = sortedValues[0];
		int denserank = 1;
		long prev = sortedValues[0];
		for (int i = 1; i < length; i++) {
			long v = sortedValues[i];
			if (prev != v) {
				reals[denserank] = v;
				denserank++;
				prev = sortedValues[i];
			}
		}
	}

	@Override
	public int real2denserank(double real) {
		int found = Arrays.binarySearch(reals, Utils.encodeDL(real));
		if (found < 0) {
			return ~found;
		}
		else {
			return found;
		}
	}

	@Override
	public double denserank2double(int denserank) {
		return Utils.decodeLD(reals[denserank]);
	}

	@Override
	public int denserankMax() {
		return reals.length - 1;
	}

}
