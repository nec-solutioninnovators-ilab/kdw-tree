/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Sample implementation of the traditional kd-tree.
<p>EXAMPLE</p>
<pre style="border:1px solid;">
// 2-dimensional data points (20-elements).
double[][] data = new double[][] {
	{0,3},{1,3},{2,3},{3,3},{4,3},
	{0,2},{1,2},{2,2},{3,2},{4,2},
	{0,1},{1,1},{2,1},{3,1},{4,1},
	{0,0},{1,0},{2,0},{3,0},{4,0},
};

// create KDTree
KDTree tree = new KDTree(data);

// counting query. returns number of points in query (hyper)rectangle.
int count = tree.count(new double[]{1,1}, new double[]{2,2});
// result is 4
System.out.println(count);
</pre>
 */
public class KDTree implements Serializable {

	private static final long serialVersionUID = 1L;

	/** number of dimensions */
	private int numDim;

	/** leaf size */
	private int LEAF_SIZE = 256;

	/** node of root */
	private TNode root;

	/**
	 * Create instance from passed data points.
	 * The array form of data points is double[number of points][number of dimensions].
	 * Number of dimensions must be between 2 and 31.
	 * Number of dimensions in all points must be identical.
	 * @param data	The data points to be stored. The array form is double[number of points][number of dimensions].
	 * @exception IllegalArgumentException	If data is null. If data is empty. If data contains null. If number of dimensions are not between 2 to 31. If number of dimensions in all points are not identical. If data points contain not a finite number.
	 */
	public KDTree(double[][] data) {
		if (data == null) {
			throw new IllegalArgumentException("data is null");
		}
		if (data.length <= 0) {
			throw new IllegalArgumentException("data is empty");
		}
		if (data[0] == null) {
			throw new IllegalArgumentException("data contains null");
		}
		if (data[0].length < 2 || data[0].length > 31) {
			throw new IllegalArgumentException("number of dimensions are not between 2 and 31");
		}

		numDim = data[0].length;
		int numPoints = data.length;
		for (int i = 0; i < numPoints; i++) {
			if (data[i] == null) {
				throw new IllegalArgumentException("data contains null");
			}
			if (data[i].length != numDim) {
				throw new IllegalArgumentException("number of dimensions in all points are not identical");
			}
			for (int d = 0; d < numDim; d++) {
				if (!Utils.isFinite(data[i][d])) {
					throw new IllegalArgumentException("data contains not a finite number");
				}
			}
		}

		// create pointers and set initial sequence. (0,1,...,numData-1)
		IntBuffer pointers = new IntBuffer(numPoints);
		for (int i = 0; i < numPoints; i++) {
			pointers.add(i);
		}
		// build tree recursively
		root = kdtree(data, 0, 0, pointers);
	}

	/** node of kd-tree */
	private class TNode implements Serializable {
		private static final long serialVersionUID = 1L;

		/** dividing dimension */
		private int divDimension;
		/** coordinates of dividing point */
		private double[] val;
		/** left child */
		private TNode leftChild;
		/** right child */
		private TNode rightChild;
		/** sub-tree size including this node */
		private int treeSize;

		/** true if node is leaf */
		private boolean isLeaf = false;
		/** points of leaf node */
		private double[][] leafVals;
	}

	/**
	 * build tree recursively.
	 * @param points	data points. The array form of data points is double[number of points][number of dimensions].
	 * @param divDim	dividing dimension
	 * @param indivisible	indivisible dimension, MSB is assigned first dimension, next bit is assigned second dimension, ...
	 * @param pointers
	 * @return	created node of kd-tree
	 */
	private TNode kdtree(double[][] points, int divDim, int indivisible, IntBuffer pointers) {
		int treeSize = pointers.length();
		if (treeSize <= LEAF_SIZE) {
			// build leaf
			return kdTreeLeaf(points, pointers);
		}

		if (Integer.bitCount(indivisible) == numDim) {
			// build leaf if indivisible in all dimensions
			return kdTreeLeaf(points, pointers);
		}

		if (divDim == numDim) {
			divDim = 0;
		}

		if (indivisible << divDim < 0) {
			// skip dividing if already indivisible in the dividing dimension
			return kdtree(points, divDim + 1, indivisible, pointers);
		}

		// skip dividing if all values of the dividing dimension are same.
		boolean divisible = false;
		double value0 = points[pointers.get(0)][divDim];
		for (int i = 1; i < treeSize; i++) {
			if (points[pointers.get(i)][divDim] != value0) {
				divisible = true;
				break;
			}
		}
		if (!divisible) {
			// skip dividing and update information of indivisible dimension
			return kdtree(points, divDim + 1, indivisible | (0x80000000 >>> divDim), pointers);
		}

		// compute median on dividing dimension
		double [] tmp_arr = new double[treeSize];
		for (int i = 0; i < tmp_arr.length; i++) {
			tmp_arr[i] = points[pointers.get(i)][divDim];
		}
		double median = selectMedian(tmp_arr);
		tmp_arr = null;

		// compute statistics for selecting suitable dividing value
		int lesserThanCount = 0;
		int equalsCount = 0;
		double greaterThanMinimum = Double.MAX_VALUE;
		for (int i = 0; i < treeSize; i++) {
			double v = points[pointers.get(i)][divDim];
			if (v < median) {
				lesserThanCount++;
			}
			else if (v == median) {
				equalsCount++;
			}
			else { // v > median
				if (v < greaterThanMinimum) {
					greaterThanMinimum = v;
				}
			}
		}

		// select suitable dividing value
		double dividingValue;
		int leftCount;
		int rightCount;
		if (lesserThanCount == 0) {
			dividingValue = greaterThanMinimum;
			leftCount = equalsCount;
		}
		else {
			dividingValue = median;
			leftCount = lesserThanCount;
		}
		rightCount = treeSize - leftCount - 1;

		// assign points to left if less than dividing value, otherwise, assign to right
		double[] divPoint = null;
		IntBuffer leftList = new IntBuffer(leftCount);
		IntBuffer rightList = new IntBuffer(rightCount);
		for (int i = 0; i < treeSize; i++) {
			int pt = pointers.get(i);
			if (divPoint == null && points[pt][divDim] == dividingValue) {
				// assign point of first dividing value to this node
				divPoint = points[pt].clone();
			}
			else if (points[pt][divDim] < dividingValue) {
				leftList.add(pt);
			}
			else {
				rightList.add(pt);
			}
		}

		// create current node
		TNode node = new TNode();
		node.divDimension = divDim;
		node.val = divPoint;
		node.treeSize = treeSize;

		// create left child recursively
		node.leftChild = kdtree(points, divDim + 1, indivisible, leftList);
		// create right child recursively
		node.rightChild = kdtree(points, divDim + 1, indivisible, rightList);
		return node;
	}

	// build leaf
	private TNode kdTreeLeaf(double[][] points, IntBuffer pointers) {
		// store all of points to leaf
		TNode node = new TNode();
		node.treeSize = pointers.length();
		node.isLeaf = true;
		node.leafVals = new double[numDim][];
		for (int d = 0; d < numDim; d++) {
			node.leafVals[d] = new double[pointers.length()];
			for (int i = 0; i < pointers.length(); i++) {
				int pt = pointers.get(i);
				node.leafVals[d][i] = points[pt][d];
			}
		}
		return node;
	}

	// compute median in array element
	// if array.length is even, select median from array[length/2]
	private double selectMedian(double[] array) {
		final int mid = array.length >> 1;
		int start = 0;
		int end = array.length - 1;
		while (start < end) {
			int r = start;
			int w = end;
			double pivot = array[(r + w) >> 1];
			while (r < w) {
				if (array[r] >= pivot) {
					double tmp = array[w];
					array[w] = array[r];
					array[r] = tmp;
					w--;
				}
				else {
					r++;
				}
			}
			if (array[r] > pivot) {
				r--;
			}
			if (mid <= r) {
				end = r;
			}
			else {
				start = r + 1;
			}
		}
		return array[mid];
	}

	private class QNode {
		private TNode node;
		private double[] min;
		private double[] max;

		private QNode(TNode node, double[] min, double[] max) {
			this.node = node;
			this.min = min.clone();
			this.max = max.clone();
		}
	}

	/**
	 * Counting query. Returns the number of points in query (hyper)rectangle.
	 * @param min	minimum coordinates of (hyper)rectangle; in other words, left-bottom coordinates of (hyper)rectangle, inclusive.
	 * @param max	maximum coordinates of (hyper)rectangle; in other words, right-top coordinates of (hyper)rectangle, inclusive.
	 * @return	number of points in the specified (hyper)rectangle, or 0 if any point does not exist in the specified (hyper)rectangle.
	 * @throws IllegalArgumentException If min or max is null. If dimensions of min or max are not identical to the dimensions of tree. If min or max contains not a finite number.
	 */
	public int count(double[] min, double[] max) {
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return 0;
		}

		int count = 0;

		// initialize search stack
		List<QNode> que = new ArrayList<QNode>(64);
		// push root
		double[] smin = new double[numDim];
		double[] smax = new double[numDim];
		for (int d = 0; d < numDim; d++) {
			smin[d] = - Double.MAX_VALUE;
			smax[d] = Double.MAX_VALUE;
		}
		QNode firstQNode = new QNode(root, smin, smax);
		que.add(firstQNode);

		while (!que.isEmpty()) {
			// pop
			QNode qnode = que.remove(que.size() - 1);
			TNode tnode = qnode.node;

			if (tnode.isLeaf) {
				// search leaf
				ILOOP: for (int i = 0; i < tnode.treeSize; i++) {
					for (int d = 0; d < numDim; d++) {
						if (tnode.leafVals[d][i] < min[d] || max[d] < tnode.leafVals[d][i]) {
							continue ILOOP;
						}
					}
					count++;
				}
				continue;
			}

			// check if sub-trees are contained in query range.
			boolean subtreeContained = true;
			for (int d = 0; d < numDim; d++) {
				if (qnode.min[d] < min[d] || max[d] < qnode.max[d]) {
					subtreeContained = false;
					break;
				}
			}
			if (subtreeContained) {
				count += tnode.treeSize;
				continue;
			}

			// check if coordinates of this node are contained in query range.
			boolean thisNodeContained = true;
			for (int d = 0; d < numDim; d++) {
				if (tnode.val[d] < min[d] || max[d] < tnode.val[d]) {
					thisNodeContained = false;
					break;
				}
			}
			if (thisNodeContained) {
				count++;
			}

			// search children
			int d = tnode.divDimension;
			if (min[d] < tnode.val[d]) {
				// push left child
				QNode q = new QNode(tnode.leftChild, qnode.min, qnode.max);
				q.max[d] = tnode.val[d];
				que.add(q);
			}
			if (tnode.val[d] <= max[d]) {
				// push right child
				QNode q = new QNode(tnode.rightChild, qnode.min, qnode.max);
				q.min[d] = tnode.val[d];
				que.add(q);
			}
		}

		return count;
	}

}
