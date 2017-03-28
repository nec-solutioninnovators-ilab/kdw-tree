/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Random;

/**
 * An implementation of the KDW-tree (short for k-dimensional wavelet tree) that uses an external tree structure instead of z-order.
<p>EXAMPLE</p>
<pre style="border:1px solid;">
// 2-dimensional data points (20-elements).
double[][] data = new double[][] {
	{0,3},{1,3},{2,3},{3,3},{4,3},
	{0,2},{1,2},{2,2},{3,2},{4,2},
	{0,1},{1,1},{2,1},{3,1},{4,1},
	{0,0},{1,0},{2,0},{3,0},{4,0},
};

// creates KDWTree
KDWTree tree = new ExternalizedKDWTree(data);

// counting query. returns number of points in query (hyper)rectangle.
int count = tree.count(new double[]{1,1}, new double[]{2,2});
// result is 4
System.out.println(count);

// reporting query. finds all points in query (hyper)rectangle
// and returns their indexes in the original data array.
// This query finds 4 points and returns their indexes: (6, 7, 11, 12).
int[] reportIndexes = tree.report(new double[]{1,1}, new double[]{2,2});
for (int index : reportIndexes) {
	System.out.println(index);
}

// sampling query. finds all points in query (hyper)rectangle, selects points
// from them at random and returns their indexes in the original data array.
// This query samples three points from the points in the query.
java.util.Random rnd = new java.util.Random();
int[] sampleIndexes = tree.sample(new double[]{1,1}, new double[]{2,2}, 3, rnd);
for (int index : sampleIndexes) {
	System.out.println(index);
}
</pre>
 */
public class ExternalizedKDWTree implements KDWTree {

	private static final long serialVersionUID = 1L;

	/** number of points */
	private int numData;

	/** number of dimensions */
	private int numDim;

	/** rank-space dictionary */
	private RankIndex[] mdRankIndex;

	/** index of points of original data points */
	private int[] pointers;

	/** leaf size of kd-tree */
	private final int LEAF_SIZE = 256;

	/** minimum coordinates of kd-tree root */
	private int[] rootMins;

	/** maximum coordinates of kd-tree root */
	private int[] rootMaxs;

	/** root node of kd-tree */
	private KdNode root;

	/** WaveletMatrix created from kd-tree leaf points in rank-space */
	private WaveletMatrix[] kdWM;

	/** current offset of sub-tree in building kd-tree */
	transient private int nodeOffset;

	/**
	 * Create instance from passed data points.
	 * The array form of data points is double[number of points][number of dimensions].
	 * Number of dimensions must be between 2 and 31.
	 * Number of dimensions in all points must be identical.
	 * @param data	The data points to be stored. The array form is double[number of points][number of dimensions].
	 * @exception IllegalArgumentException	If data is null. If data is empty. If data contains null. If number of dimensions are not between 2 to 31. If number of dimensions in all points are not identical. If data points contain not a finite number.
	 */
	public ExternalizedKDWTree(double[][] data) {
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

		numData = data.length;
		numDim = data[0].length;

		// array of rank-space points
		int[][] points = new int[numDim][];

		// create rank-space dictionaries every dimensions
		mdRankIndex = new RankIndex[numDim];
		for (int d = 0; d < numDim; d++) {
			RankIndexBuilder rankIndexBuilder = new RankIndexBuilder(numData);
			for (double[] real : data) {
				if (d == 0) {
					if (real == null) {
						throw new IllegalArgumentException("data contains null");
					}
					if (real.length != numDim) {
						throw new IllegalArgumentException("number of dimensions in all points are not identical");
					}
				}
				if (!Utils.isFinite(real[d])) {
					throw new IllegalArgumentException("data contains not a finite number");
				}
				rankIndexBuilder.append(real[d]);
			}
			mdRankIndex[d] = rankIndexBuilder.build();

			// convert data points from real-number to rank-space value
			points[d] = new int[numData];
			int idx = 0;
			for (double[] real : data) {
				points[d][idx] = mdRankIndex[d].real2denserank(real[d]);
				idx++;
			}
		}

		// compute minimum and maximum coordinates of rank-space points, corresponding to kd-tree root
		rootMins = new int[numDim];
		rootMaxs = new int[numDim];
		for (int d = 0; d < numDim; d++) {
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (int i = 0; i < numData; i++) {
				int v = points[d][i];
				if (v < min) {
					min = v;
				}
				if (v > max) {
					max = v;
				}
			}
			rootMins[d] = min;
			rootMaxs[d] = max;
		}

		// create pointers and set initial sequence. (0,1,...,numData-1)
		pointers = new int[numData];
		for (int i = 0; i < numData; i++) {
			pointers[i] = i;
		}

		// build kd-tree node recursively
		nodeOffset = 0;
		int[] workarray = new int[numData];
		root = buildNode(points, 0, new boolean[numDim], pointers, 0, numData, workarray);
		// now, pointers are sorted according to kd-tree leaf order

		// create WaveletMatrix from rank-space points which are sorted according to kd-tree leaf order
		kdWM = new WaveletMatrix[numDim];
		for (int d = 0; d < numDim; d++) {
			// sort rank-space points according to kd-tree leaf order
			indirectArrayCopy(points[d], pointers, workarray);

			// create WaveletMatrix
			kdWM[d] = new WaveletMatrix(workarray);
		}

		// destroy array of rank-space points
		for (int d = 0; d < numDim; d++) {
			points[d] = null;
		}
	}

	// copy src[ptr[x]] to dst[x]
	private void indirectArrayCopy(int[] src, int[] ptr, int[] dst) {
		for (int i = 0, len = ptr.length; i < len; i++) {
			dst[i] = src[ptr[i]];
		}
	}

	/** build kd-tree node recursively */
	private KdNode buildNode(int[][] points, int dim, boolean[] ignoreDim, int[] pointers, final int start, final int end, int[] workarray) {
		int treeSize = end - start;

		if (treeSize <= LEAF_SIZE) {
			// build kd-tree leaf
			return buildLeaf(points, dim, pointers, start, end);
		}

		// compute median of current dimension, and divide node using the median.
		// however, choose next dimension if divide unavailable (e.g. single value).
		// if divide unavailable every dimensions, build leaf
		for (int retryCount = 0; retryCount < numDim; retryCount++) {

			// skip dimension if divide unavailable
			if (ignoreDim[dim]) {
				if (++dim >= numDim) {
					dim = 0;
				}
				continue;
			}

			// points of current dimension
			int[] dimPoints = points[dim];

			// compute median
			for (int i = 0; i < treeSize; i++) {
				int pt = pointers[start + i];
				workarray[i] = dimPoints[pt];
			}
			int median = selectMedian(workarray, treeSize);

			// compute value of node members

			// compute minimum value
			int min = Integer.MAX_VALUE;
			// compute previous value of median
			int maxLeft = Integer.MIN_VALUE;
			// compute next value of median
			int nextMedian = Integer.MAX_VALUE;
			// compute maximum value
			int max = Integer.MIN_VALUE;
			// compute number of elements which are less than median
			int predecessorCount = 0;
			// compute number of elements which are greater than median
			int successorCount = 0;
			for (int i = 0; i < treeSize; i++) {
				int v = workarray[i];
				if (v < min) {
					min = v;
				}
				if (v > max) {
					max = v;
				}
				if (v < median) {
					if (v > maxLeft) {
						maxLeft = v;
					}
					predecessorCount++;
				}
				else if (v > median) {
					if (v < nextMedian) {
						nextMedian = v;
					}
					successorCount++;
				}
			}
			// assign median value to right child.
			// adjust median to balancing children.
			if (predecessorCount > 0) {
				if (predecessorCount >= successorCount) {
					// don't adjust
					// e.g. (1,1,1,2,3,3,3) => median is 2
					// e.g. (1,1,1,1,2,3,3,3) => median is 2
				}
				else {
					// adjust median to next value.
					// e.g. (1,2,2,2,5,5,5) => adjust median from 2 to 5
					// e.g. (1,2,2,2,2,5,5,5) => adjust median from 2 to 5
					maxLeft = median;
					median = nextMedian;
				}
			}
			else if (successorCount > 0) {
				// adjust median to next value.
				// e.g. (1,1,1,3,3) => adjust median from 1 to 3
				// e.g. (1,1,1,1,3,3) => adjust median from 1 to 3
				maxLeft = median;
				median = nextMedian;
			}
			else {
				// elements of current dimension are single value.
				// divide unavailable in current dimension
				ignoreDim[dim] = true;
				// retry in next dimension
				if (++dim >= numDim) {
					dim = 0;
				}
				continue;
			}

			// divide points according to median value
			int leftEnd = start;
			int rightPtr = 0;
			for (int i = start; i < end; i++) {
				int pt = pointers[i];
				int v = dimPoints[pt];
				if (v < median) {
					// assign left if values are less than median
					pointers[leftEnd++] = pt;
				}
				else {
					// assign right if values are greater than or equals to median
					workarray[rightPtr++] = pt;
				}
			}
			System.arraycopy(workarray, 0, pointers, leftEnd, rightPtr);

			// create node, then build sub-tree
			KdNode node = new KdNode();
			node.divDimension = dim;
			node.offset = nodeOffset;
			node.treeSize = treeSize;
			node.minValue = min;
			node.maxValueLeft = maxLeft;
			node.minValueRight = median;
			node.maxValue = max;

			if (++dim >= numDim) {
				dim = 0;
			}
			node.leftChild = buildNode(points, dim, ignoreDim.clone(), pointers, start, leftEnd, workarray);
			node.rightChild = buildNode(points, dim, ignoreDim.clone(), pointers, leftEnd, end, workarray);

			return node;

		}

		// now, divide unavailable every dimensions, so build leaf.
		return buildLeaf(points, dim, pointers, start, end);
	}

	private KdNode buildLeaf(int[][] points, final int dim, int[] pointers, final int start, final int end) {
		int treeSize = end - start;

		KdNode node = new KdNode();
		node.divDimension = dim;
		node.offset = nodeOffset;
		node.treeSize = treeSize;
		node.isLeaf = true;

		// compute minimum value and maximum value of current dimension.
		// and, store points to leaf array interleaved.
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		node.lvFlat = new int[numDim * treeSize];
		for (int d = 0; d < numDim; d++) {
			if (d == dim) {
				for (int i = 0; i < treeSize; i++) {
					int v = points[d][pointers[start+i]];
					node.lvFlat[d + numDim * i] = v;
					if (v < min) {
						min = v;
					}
					if (v > max) {
						max = v;
					}
				}
			}
			else {
				for (int i = 0; i < treeSize; i++) {
					node.lvFlat[d + numDim * i] = points[d][pointers[start+i]];
				}
			}
		}
		node.minValue = min;
		node.maxValue = max;

		// update offset by size of leaf
		nodeOffset += node.treeSize;

		return node;
	}

	// compute median in array element at index range [0, length).
	// if length is even, select median from array[length/2]
	private int selectMedian(int[] array, final int length) {
		final int mid = length >> 1; // length / 2;
		int start = 0;
		int end = length - 1;
		while (start < end) {
			int r = start;
			int w = end;
			int pivot = array[(r + w) >> 1];
			while (r < w) {
				if (array[r] >= pivot) {
					int tmp = array[w];
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

	/** node of kd-tree */
	private class KdNode implements Serializable {
		private static final long serialVersionUID = 1L;

		/** left child */
		private KdNode leftChild;
		/** right child */
		private KdNode rightChild;
		/** sub-tree size including this node */
		private int treeSize;

		/** dimension of node (dividing dimension) */
		private int divDimension;

		/** minimum value of left child */
		private int minValue;
		/** maximum value of left child */
		private int maxValueLeft;
		/** minimum value of right child */
		private int minValueRight;
		/** maximum value of right child */
		private int maxValue;

		/** true if node is leaf */
		private boolean isLeaf = false;
		/** points of leaf node, store interleaved (x0,y0,z0,x1,y1,z1,...) */
		private int[] lvFlat;

		/** sub-tree offset (used by WaveletMatrix searching) */
		private int offset;
	}

	@Override
	public int count(double[] min, double[] max) {
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return 0;
		}

		// convert query range from real-number to rank-space
		int[] qMin = new int[numDim];
		int[] qMax = new int[numDim];
		for (int d = 0; d < numDim; d++) {
			qMin[d] = mdRankIndex[d].real2denserank(min[d]);
			qMax[d] = mdRankIndex[d].real2denserank(Math.nextUp(max[d])) - 1;
			if (qMin[d] > qMax[d]) {
				return 0;
			}
		}

		int[] vmin = rootMins.clone();
		int[] vmax = rootMaxs.clone();
		return rangeCountRecursive(vmin, vmax, qMin, qMax, root, 0, kdWM);
	}

	private int rangeCountRecursive(int[] vmin, int[] vmax, final int[] queryMin, final int[] queryMax, KdNode node, int containedBit, WaveletMatrix[] wms) {
		// dimension of current node
		final int dim = node.divDimension;

		final int nodeMin = node.minValue;
		final int nodeMax = node.maxValue;
		final int qmin = queryMin[dim];
		final int qmax = queryMax[dim];
		if (nodeMax < qmin || qmax < nodeMin) {
			// out of range
			return 0;
		}
		vmin[dim] = nodeMin;
		vmax[dim] = nodeMax;

		// number of dimensions contained
		final int dimContainedBit = 0x80000000 >>> dim;
		boolean dimContained = false;
		if (qmin <= nodeMin && nodeMax <= qmax) {
			// now, points are contained in query range of current dimension.
			containedBit |= dimContainedBit;
			dimContained = true;
		}

		int numContained = Integer.bitCount(containedBit);
		if (numContained == numDim) {
			// now, points are contained in query range of every dimensions.
			return node.treeSize;
		}

		if (numContained == numDim - 1) {
			// now, points are contained in query range of (d - 1) dimensions.
			// search final dimension
			int last1d = Integer.numberOfLeadingZeros(~containedBit);
			final int vmin1d = vmin[last1d];
			final int vmax1d = vmax[last1d];
			final int qmin1d = queryMin[last1d];
			final int qmax1d = queryMax[last1d];
			if (qmin1d <= vmin1d && vmax1d <= qmax1d) {
				return node.treeSize;
			}
			if (node.isLeaf) {
				// search leaf on final dimension
				return leafCount1D(last1d, node, qmin1d, qmax1d);
			}
			else {
				// search with WaveletMatrix on final dimension
				WaveletMatrix wm = wms[last1d];
				int s = node.offset;
				int e = s + node.treeSize;
				if (vmax1d <= qmax1d) {
					return node.treeSize - wm.ranklt(qmin1d, s, e);
				}
				else if (qmin1d <= vmin1d) {
					return wm.rankle(qmax1d, s, e);
				}
				else {
					return wm.rankle(qmax1d, s, e) - wm.ranklt(qmin1d, s, e);
				}
			}
		}
		else if (node.isLeaf) {
			// search leaf
			return leafCount(node, queryMin, queryMax);
		}

		int count = 0;
		if (dimContained) {
			// search left child and right child
			int[] lmax = vmax.clone();
			int[] rmin = vmin.clone();
			lmax[dim] = node.maxValueLeft;
			rmin[dim] = node.minValueRight;
			count += rangeCountRecursive(vmin/*.clone()*/, lmax, queryMin, queryMax, node.leftChild, containedBit, wms);
			lmax = null;
			count += rangeCountRecursive(rmin, vmax/*.clone()*/, queryMin, queryMax, node.rightChild, containedBit, wms);
			return count;
		}
		else {
			// search left child
			final int lmax = node.maxValueLeft;
			if (qmin <= lmax) {
				int[] newmax = vmax.clone();
				newmax[dim] = lmax;
				count += rangeCountRecursive(vmin.clone(), newmax, queryMin, queryMax, node.leftChild,
						(qmin <= nodeMin && lmax <= qmax)
						? containedBit | dimContainedBit
						: containedBit,
						wms);
			}

			// search right child
			final int rmin = node.minValueRight;
			if (rmin <= qmax) {
				int[] newmin = vmin/*.clone()*/;
				newmin[dim] = rmin;
				count += rangeCountRecursive(newmin, vmax/*clone()*/, queryMin, queryMax, node.rightChild,
						(qmin <= rmin && nodeMax <= qmax)
						? containedBit | dimContainedBit
						: containedBit,
						wms);
			}

			return count;
		}
	}

	// Returns count in leaf on final dimension
	private int leafCount1D(int axis, KdNode leaf, int queryMin, int queryMax) {
		int dim = numDim;
		int len = leaf.lvFlat.length;
		int count = 0;
		for (; axis < len; axis += dim) {
			int v = leaf.lvFlat[axis];
			if (v < queryMin || queryMax < v) {
				continue;
			}
			count++;
		}
		return count;
	}

	// Returns count in leaf
	private int leafCount(KdNode leaf, int[] queryMin, int[] queryMax) {
		int dim = numDim;
		int len = leaf.lvFlat.length;
		int count = 0;
		for (int i = 0, d = 0; i < len;) {
			int v = leaf.lvFlat[i];
			if (v < queryMin[d] || queryMax[d] < v) {
				i += dim - d;
				d = 0;
				continue;
			}
			i++;
			d++;
			if (d == dim) {
				count++;
				d = 0;
			}
		}
		return count;
	}

	@Override
	public int[] report(double[] min, double[] max) {
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return new int[0];
		}

		// initialize work area
		Intervals currentIntervals = new Intervals(10000);
		Intervals workIntervals = new Intervals(10000);
		IntBuffer work1 = new IntBuffer(10000);
		IntBuffer work2 = new IntBuffer(10000);
		workIntervals.clear();
		work1.clear();
		work2.clear();

		// convert query range from real-number to rank-space
		int[] qMin = new int[numDim];
		int[] qMax = new int[numDim];
		for (int d = 0; d < numDim; d++) {
			qMin[d] = mdRankIndex[d].real2denserank(min[d]);
			qMax[d] = mdRankIndex[d].real2denserank(Math.nextUp(max[d])) - 1;
			if (qMin[d] > qMax[d]) {
				return new int[0];
			}
		}

		// search intervals
		rangeIntervals(qMin, qMax, true, workIntervals);

		// search intervals which contained in query range on (d - 1) dimensions.
		currentIntervals.clear();
		Intervals.IntervalCursor workCursor = workIntervals.getCursor();
		while (workCursor.next()) {
			if (workCursor.root) {
				currentIntervals.addRoot(workCursor.s, workCursor.e);
			}
			else {
				// search with WaveletMatrix on final dimension
				int treeId = workCursor.treeId;
				WaveletMatrix wm = kdWM[treeId];
				wm.rangeIntervals(
						workCursor.s, workCursor.e,
						qMin[treeId], qMax[treeId],
						treeId, currentIntervals, work1, work2);
			}
		}

		// convert intervals from inner to root
		if (! currentIntervals.isRootOnly()) {
			workIntervals.clear();
			inner2global(currentIntervals, workIntervals, work1, work2);
			Intervals swap = currentIntervals;
			currentIntervals = workIntervals;
			workIntervals = swap;
		}

		int[] indexArray = new int[currentIntervals.getTotalLength()];
		int indexPtr = 0;
		Intervals.IntervalCursor currentCursor = currentIntervals.getCursor();
		while (currentCursor.next()) {
			for (int i = currentCursor.s; i < currentCursor.e; i++) {
				indexArray[indexPtr++] = pointers[i];
			}
		}
		return indexArray;
	}

	@Override
	public int[] sample(double[] min, double[] max, int sampleCount, Random rnd) {
		if (rnd == null) {
			throw new IllegalArgumentException("rnd is null.");
		}
		if (sampleCount <= 0) {
			throw new IllegalArgumentException("sampleCount is not positive number");
		}
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return new int[0];
		}

		// initialize work area
		Intervals currentIntervals = new Intervals(10000);
		Intervals workIntervals = new Intervals(10000);
		IntBuffer work1 = new IntBuffer(10000);
		IntBuffer work2 = new IntBuffer(10000);
		workIntervals.clear();
		work1.clear();
		work2.clear();

		// convert query range from real-number to rank-space
		int[] qMin = new int[numDim];
		int[] qMax = new int[numDim];
		for (int d = 0; d < numDim; d++) {
			qMin[d] = mdRankIndex[d].real2denserank(min[d]);
			qMax[d] = mdRankIndex[d].real2denserank(Math.nextUp(max[d])) - 1;
			if (qMin[d] > qMax[d]) {
				return new int[0];
			}
		}

		// search intervals
		rangeIntervals(qMin, qMax, true, workIntervals);

		// search intervals which contained in query range on (d - 1) dimensions.
		currentIntervals.clear();
		Intervals.IntervalCursor workCursor = workIntervals.getCursor();
		while (workCursor.next()) {
			if (workCursor.root) {
				currentIntervals.addRoot(workCursor.s, workCursor.e);
			}
			else {
				// search with WaveletMatrix on final dimension
				int treeId = workCursor.treeId;
				WaveletMatrix wm = kdWM[treeId];
				wm.rangeIntervals(
						workCursor.s, workCursor.e,
						qMin[treeId], qMax[treeId],
						treeId, currentIntervals, work1, work2);
			}
		}

		int freqResult = currentIntervals.getTotalLength();

		if (freqResult <= sampleCount) {
			Intervals swap = currentIntervals;
			currentIntervals = workIntervals;
			workIntervals = swap;
		}
		else {
			// generate partial permutations of sampleCount elements from set {0,1,...,freqResult-1}
			RandomPermutation rg = new RandomPermutation(rnd);
			int[] sampleIndex = rg.partialPermutation(freqResult, sampleCount);
			Arrays.sort(sampleIndex);

			// choose sub-set of intervals using partial permutations
			int processed = 0;
			int sptr = 0;
			int sample = sampleIndex[sptr];
			workIntervals.clear();
			Intervals.IntervalCursor currentCursor = currentIntervals.getCursor();
			INTERVAL_LOOP: while (currentCursor.next()) {
				int w = currentCursor.e - currentCursor.s;
				while (processed <= sample && sample < processed + w) {
					int ss = currentCursor.s + sample - processed;
					if (currentCursor.root) {
						workIntervals.addRoot(ss, ss + 1);
					}
					else {
						workIntervals.add(ss, ss + 1, currentCursor.treeId, currentCursor.level);
					}

					sptr++;
					if (sptr >= sampleCount) {
						// now, satisfied number of samples
						break INTERVAL_LOOP;
					}
					sample = sampleIndex[sptr];
				}
				processed += w;
			}
		}

		currentIntervals.clear();
		if (workIntervals.isRootOnly()) {
			// only root-intervals
			Intervals swap = currentIntervals;
			currentIntervals = workIntervals;
			workIntervals = swap;
		}
		else {
			// convert intervals form inner to root
			inner2global(workIntervals, currentIntervals, work1, work2);
		}

		int[] indexArray = new int[currentIntervals.getTotalLength()];
		int indexPtr = 0;
		Intervals.IntervalCursor currentCursor = currentIntervals.getCursor();
		while (currentCursor.next()) {
			for (int i = currentCursor.s; i < currentCursor.e; i++) {
				indexArray[indexPtr++] = pointers[i];
			}
		}
		return indexArray;
	}

	private void rangeIntervals(int[] queryMin, int[] queryMax, boolean d_1_stop, Intervals intervalsOut) {
		int[] vmin = rootMins.clone();
		int[] vmax = rootMaxs.clone();
		rangeIntervalsRecursive(vmin, vmax, queryMin, queryMax, root, 0, intervalsOut, d_1_stop);
		return;
	}

	private void rangeIntervalsRecursive(int[] vmin, int[] vmax, final int[] queryMin, final int[] queryMax, KdNode node, int containedBit, Intervals intervals, boolean d_1_stop) {
		// dimension of current node
		final int dim = node.divDimension;

		final int nodeMin = node.minValue;
		final int nodeMax = node.maxValue;
		final int qmin = queryMin[dim];
		final int qmax = queryMax[dim];
		if (nodeMax < qmin || qmax < nodeMin) {
			// out of range
			return;
		}
		vmin[dim] = nodeMin;
		vmax[dim] = nodeMax;

		// number of dimensions contained
		final int dimContainedBit = 0x80000000 >>> dim;
		boolean dimContained = false;
		if (qmin <= nodeMin && nodeMax <= qmax) {
			// now, points are contained in query range of current dimension.
			containedBit |= dimContainedBit;
			dimContained = true;
		}

		int numContained = Integer.bitCount(containedBit);
		if (numContained == numDim) {
			// now, points are contained in query range of every dimensions.
			intervals.addRoot(node.offset, node.offset + node.treeSize);
			return;
		}

		if (numContained == numDim - 1) {
			// now, points are contained in query range of (d - 1) dimensions.
			// search final dimension
			int last1d = Integer.numberOfLeadingZeros(~containedBit);
			if (node.isLeaf) {
				// search leaf on final dimension
				leafIntervals1D(last1d, node, queryMin[last1d], queryMax[last1d], intervals);
				return;
			}
			else if (d_1_stop) {
				// append inner-intervals on final dimension
				intervals.add(node.offset, node.offset + node.treeSize, last1d, 0);
				return;
			}
		}
		else if (node.isLeaf) {
			// search leaf
			leafIntervals(node, queryMin, queryMax, intervals);
			return;
		}

		if (dimContained) {
			// search left child and right child
			int[] lmax = vmax.clone();
			int[] rmin = vmin.clone();
			lmax[dim] = node.maxValueLeft;
			rmin[dim] = node.minValueRight;
			rangeIntervalsRecursive(vmin.clone(), lmax, queryMin, queryMax, node.leftChild, containedBit, intervals, d_1_stop);
			lmax = null;
			rangeIntervalsRecursive(rmin, vmax.clone(), queryMin, queryMax, node.rightChild, containedBit, intervals, d_1_stop);
			return;
		}
		else {
			// search left child
			final int lmax = node.maxValueLeft;
			if (qmin <= lmax) {
				int[] newmax = vmax.clone();
				newmax[dim] = lmax;
				rangeIntervalsRecursive(vmin.clone(), newmax, queryMin, queryMax, node.leftChild,
						(qmin <= nodeMin && lmax <= qmax)
						? containedBit | dimContainedBit
						: containedBit,
						intervals, d_1_stop);
			}

			// search right child
			final int rmin = node.minValueRight;
			if (rmin <= qmax) {
				int[] newmin = vmin.clone();
				newmin[dim] = rmin;
				rangeIntervalsRecursive(newmin, vmax.clone(), queryMin, queryMax, node.rightChild,
						(qmin <= rmin && nodeMax <= qmax)
						? containedBit | dimContainedBit
						: containedBit,
						intervals, d_1_stop);
			}

			return;
		}
	}

	// search intervals in leaf on final dimension
	private void leafIntervals1D(int axis, KdNode leaf, int queryMin, int queryMax, Intervals intervals) {
		int len = leaf.lvFlat.length;
		int intervalStart = -1;
		int ptr = leaf.offset;
		for (int i = axis; i < len; ptr++, i += numDim) {
			int v = leaf.lvFlat[i];
			if (v < queryMin || queryMax < v) {
				if (intervalStart >= 0) {
					intervals.addRoot(intervalStart, ptr);
					intervalStart = -1;
				}
				continue;
			}
			if (intervalStart < 0) {
				intervalStart = ptr;
			}
		}
		if (intervalStart >= 0) {
			intervals.addRoot(intervalStart, ptr);
		}
		return;
	}

	// search intervals in leaf
	private void leafIntervals(KdNode leaf, int[] queryMin, int[] queryMax, Intervals intervals) {
		int len = leaf.lvFlat.length;
		int intervalStart = -1;
		int ptr = leaf.offset;
		LOOP: for (int i = 0; i < len; ptr++, i += numDim) {
			for (int d = 0; d < numDim; d++) {
				int v = leaf.lvFlat[d+i];
				if (v < queryMin[d] || queryMax[d] < v) {
					if (intervalStart >= 0) {
						intervals.addRoot(intervalStart, ptr);
						intervalStart = -1;
					}
					continue LOOP;
				}
			}
			if (intervalStart < 0) {
				intervalStart = ptr;
			}
		}
		if (intervalStart >= 0) {
			intervals.addRoot(intervalStart, ptr);
		}
		return;
	}

	// convert inner-intervals to root-intervals
	private void inner2global(Intervals inner, Intervals globalOut, IntBuffer work1, IntBuffer work2) {
		globalOut.clear();
		Intervals.IntervalCursor cursor = inner.getCursor();
		while (cursor.next()) {
			if (cursor.root) {
				globalOut.addRoot(cursor.s, cursor.e);
			}
			else {
				kdWM[cursor.treeId].innerInterval2rootIntervals(cursor.level, cursor.s, cursor.e, globalOut, work1, work2);
			}
		}
	}

}
