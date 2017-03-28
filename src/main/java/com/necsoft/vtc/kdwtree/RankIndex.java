/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * An interface of rank-space dictionary.
 * Rank is assigned in dense-ranking ("1223" ranking).
 * Rank of leader is 0.
 * Rank of absent numbers is rank of insertion-point.
 * <pre style="font-family:'MS Mincho','Terminal','monospace';">Example
 * number	2 3 5 6 6 6 8 8 9 9
 * rank	0 1 2 3 3 3 4 4 5 5
 * 
 * Rank of absent numbers.
 * 	rank of 1 is 0.
 * 	rank of 7 is 4.
 * 	rank of 10 is 6.
 * </pre>
 * 
 */
interface RankIndex {

	/**
	 * Returns dense-rank of specified number.
	 * @param real	number
	 * @return dense-rank
	 */
	public int real2denserank(double real);

	/**
	 * Returns number of specified dense-rank
	 * @param rank	dense-rank
	 * @return number
	 */
	public double denserank2double(int denserank);

	/**
	 * Returns maximum of dense-rank within this.
	 * @return maximum of dense-rank
	 */
	public int denserankMax();

}
