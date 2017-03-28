/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.util.Arrays;
import java.util.Random;

class TestDataset {
	private static final int DIMENSIONS = 3;
	private static final int POINTS = 100000;
	private static final int QUERIES = 1000;
	private final Random rnd;
	private final double[][] points;
	private final double[][][] queries;
	TestDataset() {
		rnd = new Random();
		points = new double[POINTS][DIMENSIONS];
		queries = new double[QUERIES][2][DIMENSIONS];
		for (int i = 0; i < POINTS; i++) {
			for (int d = 0; d < DIMENSIONS; d++) {
				points[i][d] = (rnd.nextDouble() * 2.0 - 1.0) * Double.MAX_VALUE;
			}
		}
		for (int i = 0; i < QUERIES; i++) {
			for (int d = 0; d < DIMENSIONS; d++) {
				double min = (rnd.nextDouble() * 2.0 - 1.0) * Double.MAX_VALUE;
				double max = (rnd.nextDouble() * 2.0 - 1.0) * Double.MAX_VALUE;
				if (min <= max) {
					queries[i][0][d] = min;
					queries[i][1][d] = max;
				}
				else {
					queries[i][0][d] = max;
					queries[i][1][d] = min;
				}
			}
		}
	}
	double[][] getPoints() {
		return points;
	}
	int getTestNumberMin() {
		return 0;
	}
	int getTestNumberMax() {
		return QUERIES - 1;
	}
	double[] getQueryMin(int testNumber) {
		return queries[testNumber][0];
	}
	double[] getQueryMax(int testNumber) {
		return queries[testNumber][1];
	}
	int countExpected(int testNumber) {
		int count = 0;
		LOOP: for (int i = 0; i < POINTS; i++) {
			for (int d = 0; d < DIMENSIONS; d++) {
				double v = points[i][d];
				if (v < queries[testNumber][0][d] || v > queries[testNumber][1][d]) {
					continue LOOP;
				}
			}
			count++;
		}
		return count;
	}
	int[] reportExpected(int testNumber) {
		int[] reports = new int[POINTS];
		int w = 0;
		LOOP: for (int i = 0; i < POINTS; i++) {
			for (int d = 0; d < DIMENSIONS; d++) {
				double v = points[i][d];
				if (v < queries[testNumber][0][d] || v > queries[testNumber][1][d]) {
					continue LOOP;
				}
			}
			reports[w++] = i;
		}
		return Arrays.copyOf(reports, w);
	}
	boolean isLocationExpected(int testNumber, int location) {
		if (location < 0 || location >= POINTS) {
			return false;
		}
		for (int d = 0; d < DIMENSIONS; d++) {
			double v = points[location][d];
			if (v < queries[testNumber][0][d] || v > queries[testNumber][1][d]) {
				return false;
			}
		}
		return true;
	}

}
