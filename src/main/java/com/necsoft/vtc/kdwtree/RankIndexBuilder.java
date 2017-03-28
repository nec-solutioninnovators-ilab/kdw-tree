/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.util.Arrays;

class RankIndexBuilder {

	transient private long[] longArray;
	transient private int appendPos = 0;

	RankIndexBuilder(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("length <= 0");
		}
		longArray = new long[length];
	}

	private void moreElements() {
		if (appendPos >= longArray.length) {
			int needSize = appendPos + 1 + 4;
			long[] newElements = new long[needSize + (needSize >> 2)]; // * 1.25
			System.arraycopy(longArray, 0, newElements, 0, longArray.length);
			longArray = newElements;
		}
	}

	void append(double value) {
		moreElements();
		longArray[appendPos++] = Utils.encodeDL(value);
	}

	RankIndex build() {
		if (appendPos < 1) {
			throw new IllegalStateException("empty.");
		}

		int count = appendPos;
		Arrays.sort(longArray, 0, count);

		long first = longArray[0];
		long last = longArray[count - 1];

		// cardinality
		int cardinality = 1;
		long prev = longArray[0];
		for (int i = 1; i < count; i++) {
			if (prev != longArray[i]) {
				cardinality++;
				prev = longArray[i];
			}
		}

		// estimate overall number of bits when store numbers and ranks to simple array.
		long totalBitsSimple = 96L * cardinality;

		// estimate overall number of bits when store to dictionary.
		long totalBits = RankIndexDouble.estimateBits(cardinality, first, last);

		RankIndex indexObj;
		if (totalBitsSimple <= totalBits) {
			indexObj = new RankIndexDoubleSimple(longArray, cardinality);
		}
		else {
			indexObj = new RankIndexDouble(longArray, cardinality);
		}

		longArray = null;
		return indexObj;
	}

}
