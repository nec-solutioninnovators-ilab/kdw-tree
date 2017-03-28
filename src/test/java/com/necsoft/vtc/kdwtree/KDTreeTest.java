/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import static org.junit.Assert.*;

import org.junit.BeforeClass;
import org.junit.Test;

public class KDTreeTest {

	private static TestDataset data;
	private static KDTree tree;

	@BeforeClass
	public static void onlyOnce() {
		data = new TestDataset();
		tree = new KDTree(data.getPoints());
	}

	@Test
	public void testCount() {
		for (int testNumber = data.getTestNumberMin(); testNumber <= data.getTestNumberMax(); testNumber++) {
			int expected = data.countExpected(testNumber);
			int actual = tree.count(data.getQueryMin(testNumber), data.getQueryMax(testNumber));
			assertEquals(expected, actual);
		}
	}

}
