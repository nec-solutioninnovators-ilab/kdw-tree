/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;
import java.util.Random;

/**
 * An interface of the KDW-tree queries.
 */
public interface KDWTree extends Serializable {

	/**
	 * Counting query. Returns the number of points in query (hyper)rectangle.
	 * @param min	minimum coordinates of (hyper)rectangle; in other words, left-bottom coordinates of (hyper)rectangle, inclusive.
	 * @param max	maximum coordinates of (hyper)rectangle; in other words, right-top coordinates of (hyper)rectangle, inclusive.
	 * @return	number of points in the specified (hyper)rectangle, or 0 if any point does not exist in the specified (hyper)rectangle.
	 * @throws IllegalArgumentException If min or max is null. If dimensions of min or max are not identical to the dimensions of tree. If min or max contains not a finite number.
	 */
	public int count(double[] min, double[] max);

	/**
	 * Reporting query. Finds all points in query (hyper)rectangle and returns their indexes in the original data array.
	 * (The original data array means the data argument passed to the constructor.)
	 * <p>The returned indexes are not sorted.
	 * @param min	minimum coordinates of (hyper)rectangle; in other words, left-bottom coordinates of (hyper)rectangle, inclusive.
	 * @param max	maximum coordinates of (hyper)rectangle; in other words, right-top coordinates of (hyper)rectangle, inclusive.
	 * @return	indexes in the original data array, or empty array if any point does not exist in the specified (hyper)rectangle.
	 * @throws IllegalArgumentException If min or max is null. If dimensions of min or max are not identical to the dimensions of tree. If min or max contains not a finite number.
	 */
	public int[] report(double[] min, double[] max);

	/**
	 * Sampling query. Finds all points in query (hyper)rectangle, selects points from them at random
	 * and returns their indexes in the original data array.
	 * (The original data array means the data argument passed to the constructor.)
	 * <p>The returned indexes are not sorted.
	 * @param min	minimum coordinates of (hyper)rectangle; in other words, left-bottom coordinates of (hyper)rectangle, inclusive.
	 * @param max	maximum coordinates of (hyper)rectangle; in other words, right-top coordinates of (hyper)rectangle, inclusive.
	 * @param sampleCount	number of samples. must be positive number.
	 * @param rnd	instance of java.util.Random which are used selecting points randomly.
	 * @return	indexes in the original data array, or empty array if any point does not exist in the specified (hyper)rectangle.
	 * @throws IllegalArgumentException If min or max is null. If dimensions of min or max are not identical to the dimensions of tree. If min or max contains not a finite number.
	 * If sampleCount is not positive number. If rnd is null.
	 */
	public int[] sample(double[] min, double[] max, int sampleCount, Random rnd);

}
