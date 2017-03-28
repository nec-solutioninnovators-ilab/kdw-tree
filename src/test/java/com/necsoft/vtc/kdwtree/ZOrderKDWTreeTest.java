/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Random;

import org.junit.BeforeClass;
import org.junit.Test;

public class ZOrderKDWTreeTest {

	private static TestDataset data;
	private static KDWTree tree;

	@BeforeClass
	public static void onlyOnce() {
		data = new TestDataset();
		tree = new ZOrderKDWTree(data.getPoints());
	}

	@Test
	public void testCount() {
		for (int testNumber = data.getTestNumberMin(); testNumber <= data.getTestNumberMax(); testNumber++) {
			int expected = data.countExpected(testNumber);
			int actual = tree.count(data.getQueryMin(testNumber), data.getQueryMax(testNumber));
			assertEquals(expected, actual);
		}
	}

	@Test
	public void testReport() {
		for (int testNumber = data.getTestNumberMin(); testNumber <= data.getTestNumberMax(); testNumber++) {
			int[] expected = data.reportExpected(testNumber);
			int[] actual = tree.report(data.getQueryMin(testNumber), data.getQueryMax(testNumber));
			Arrays.sort(actual);
			assertArrayEquals(expected, actual);
		}
	}

	@Test
	public void testSample() {
		Random rnd = new Random();
		for (int testNumber = data.getTestNumberMin(); testNumber <= data.getTestNumberMax(); testNumber++) {
			int[] actual = tree.sample(data.getQueryMin(testNumber), data.getQueryMax(testNumber), 1000, rnd);
			for (int location : actual) {
				assertTrue(data.isLocationExpected(testNumber, location));
			}
		}
	}

}
