/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

/**
 * class for generating random permutation.
 */
class RandomPermutation {

	final private Random mRnd;

	/**
	 * Constructor, using new java.util.Random() as a random number generator.
	 */
	RandomPermutation() {
		this(null);
	}

	/**
	 * Constructor, using a specified random number generator.
	 * @param rnd	random number generator. if null, using new java.util.Random() as a random number generator.
	 */
	RandomPermutation(Random rnd) {
		if (rnd == null) {
			mRnd = new Random();
		}
		else {
			mRnd = rnd;
		}
	}

	/**
	 * Returns random permutation of k elements from set {0,1, ... ,n-1}
	 * @param n	n of set {0,1, ... ,n-1}
	 * @param k	number of return elements
	 * @return random permutation
	 */
	int[] partialPermutation(int n, int k) {
		if (n < 0 || k < 0) {
			throw new IllegalArgumentException("k or n are negative number.");
		}
		if (k > n) {
			throw new IllegalArgumentException("k > n");
		}

		if (n <= 100000 || k > (n >> 4)) {
			// shuffle sequence {0,1, ... n-1} k times
			int[] sequence = new int[n];
			for (int i = 0; i < n; i++) {
				sequence[i] = i;
			}
			shuffleTailK(sequence, k);
			// shuffled numbers are stored at tail of sequence
			return Arrays.copyOfRange(sequence, n - k, n);
		}
		else {
			// generate random number k times. retry if collision occurred.
			HashSet<Integer> collision = new HashSet<>(k);
			int[] rands = new int[k];
			for (int i = 0; i < k; i++) {
				int r;
				do {
					r = mRnd.nextInt(n);
				}
				while (collision.contains(r));
				collision.add(r);
				rands[i] = r;
			}
			return rands;
		}
	}

	private void shuffleTailK(int[] sequence, int k) {
		// Fisher-Yates shuffle
		k = sequence.length - k; // stopper
		for (int j = sequence.length; j > k; j--) {
			int rndIndex = mRnd.nextInt(j); // [0, j)

			int swaptmp = sequence[j-1];
			sequence[j-1] = sequence[rndIndex];
			sequence[rndIndex] = swaptmp;
		}
	}

}
