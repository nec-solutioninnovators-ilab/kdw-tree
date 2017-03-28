/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * Utility class for z-order sorting multi-dimensional point sequence.
 */
class ZOrderSort {
	/**
	 * Sort pointer array which stores index of point sequence. (point sequence are not sorted)
	 * @param points	point sequence array of int[dimension][sequence].
	 * @param pointers	pointer array which stores index of point sequence.
	 */
	static void sortIndirect(int[][] points, int[] pointers) {
		if (points == null) {
			throw new IllegalArgumentException("points argument is null");
		}
		if (pointers == null) {
			throw new IllegalArgumentException("pointers argument is null");
		}
		// at least 1-dimension
		if (points.length < 1) {
			throw new IllegalArgumentException("points dimension must be at least 1.");
		}
		// length of point sequence in all dimensions and pointers must be identical.
		int length = pointers.length;
		for (int d = 0; d < points.length; d++) {
			if (points[d].length != length) {
				throw new IllegalArgumentException("length of point in all dimensions and pointers must be identical.");
			}
		}

		// quick sort
		qsort(points, pointers, 0, points[0].length - 1);
	}

	private static void qsort(int[][] points, int[] pointers, int left, int right) {
		if (right <= left) {
			return;
		}
		int i = partition(points, pointers, left, right);
		qsort(points, pointers, left, i - 1);
		qsort(points, pointers, i + 1, right);
	}

	private static int partition(int[][] points, int[] pointers, int left, int right) {
		int i = left - 1;
		int j = right;
		int swap;
		while (true) {
			while (less(points, pointers[++i], pointers[right])) {
			}
			while (less(points, pointers[right], pointers[--j])) {
				if (j == left) {
					break;
				}
			}
			if (i >= j) {
				break;
			}
			// swap i j
			swap = pointers[i];
			pointers[i] = pointers[j];
			pointers[j] = swap;
		}
		// swap i right
		swap = pointers[i];
		pointers[i] = pointers[right];
		pointers[right] = swap;
		return i;
	}

	private static boolean less(int[][] points, int a, int b) {
		// compare order is dimension 2,1,0 (z,y,x)
		int j = 0;
		int x = 0;
		int dim = points.length;
		for (int k = dim - 1; k >= 0; k--) {
			int y = points[k][a] ^ points[k][b];
			if (less_msb(x, y)) {
				j = k;
				x = y;
			}
		}
		return (points[j][a] < points[j][b]);
	}

	private static boolean less_msb(int x, int y) {
		return x < y && x < (x ^ y);
	}

}
