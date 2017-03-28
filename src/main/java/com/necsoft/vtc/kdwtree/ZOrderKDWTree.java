/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

import java.util.Arrays;
import java.util.Random;

/**
 * An implementation of the KDW-tree (short for k-dimensional wavelet tree) that is based on z-order.
 * <p>EXAMPLE</p>
<pre style="border:1px solid;">
// 2-dimensional data points (20-elements).
double[][] data = new double[][] {
	{0,3},{1,3},{2,3},{3,3},{4,3},
	{0,2},{1,2},{2,2},{3,2},{4,2},
	{0,1},{1,1},{2,1},{3,1},{4,1},
	{0,0},{1,0},{2,0},{3,0},{4,0},
};

// creates KDWTree
KDWTree tree = new ZOrderKDWTree(data);

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
public class ZOrderKDWTree implements KDWTree {

	private static final long serialVersionUID = 1L;

	/** number of points */
	private int numData;

	/** number of dimensions */
	private int numDim;

	/** rank-space dictionary */
	private RankIndex[] mdRankIndex;

	/** number of left shift to align rank-space value on leftmost one-bit */
	private int[] rankShift;

	/** index of points before z-order sorting */
	private int[] pointers;

	/** array of rank-space points which are sorted according to z-order. */
	private int[][] zoPoints;

	/** WaveletMatrix created from rank-space points which are sorted according to z-order */
	private WaveletMatrix[] zoWM;

	/** threshold to sequential-scan */
	private final int stopWidth = 256;

	/** initial capacity of work area used by report query */
	private final int reportBuffersInitialCapacity = 8192;

	/**
	 * Create instance from passed data points.
	 * The array form of data points is double[number of points][number of dimensions].
	 * Number of dimensions must be between 2 and 31.
	 * Number of dimensions in all points must be identical.
	 * @param data	The data points to be stored. The array form is double[number of points][number of dimensions].
	 * @exception IllegalArgumentException	If data is null. If data is empty. If data contains null. If number of dimensions are not between 2 to 31. If number of dimensions in all points are not identical. If data points contain not a finite number.
	 */
	public ZOrderKDWTree(double[][] data) {
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

		// create rank-space dictionaries every dimensions
		mdRankIndex = new RankIndex[numDim];
		for (int d = 0; d < numDim; d++) {
			// create rank-space dictionary
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
		}

		// compute number of left shift to align rank-space value on leftmost one-bit
		int maxRank = 0;
		for (int d = 0; d < numDim; d++) {
			if (maxRank < mdRankIndex[d].denserankMax()) {
				maxRank = mdRankIndex[d].denserankMax();
			}
		}
		int maxRankBits = maxRank == 0 ? 1 : 32 - Integer.numberOfLeadingZeros(maxRank);
		rankShift = new int[numDim];
		for (int d = 0; d < numDim; d++) {
			int rankBits = 32 - Integer.numberOfLeadingZeros(mdRankIndex[d].denserankMax());
			if (rankBits == 0) {
				rankBits = 1;
			}
			rankShift[d] = maxRankBits - rankBits;
		}

		// convert data points from real-number to rank-space value
		zoPoints = new int[numDim][];
		for (int d = 0; d < numDim; d++) {
			zoPoints[d] = new int[numData];
			int idx = 0;
			for (double[] real : data) {
				zoPoints[d][idx] = mdRankIndex[d].real2denserank(real[d]) << rankShift[d];
				idx++;
			}
		}

		// create pointers and set initial sequence. (0,1,...,numData-1)
		pointers = new int[numData];
		for (int i = 0; i < numData; i++) {
			pointers[i] = i;
		}

		// sort pointers according to z-order
		ZOrderSort.sortIndirect(zoPoints, pointers);

		// create WaveletMatrix from rank-space points which are sorted according to z-order
		zoWM = new WaveletMatrix[numDim];
		int[] workArray = new int[numData];
		for (int d = 0; d < numDim; d++) {
			// sort rank-space points according to z-order
			indirectArrayCopy(zoPoints[d], pointers, workArray);
			System.arraycopy(workArray, 0, zoPoints[d], 0, numData);

			// create WaveletMatrix
			zoWM[d] = new WaveletMatrix(workArray, maxRankBits);
		}

	}

	// copy src[ptr[x]] to dst[x]
	private void indirectArrayCopy(int[] src, int[] ptr, int[] dst) {
		for (int i = 0, len = ptr.length; i < len; i++) {
			dst[i] = src[ptr[i]];
		}
	}

	// inner classes for searching
	private class WMNode {
		/** level of search node in WaveletMatrix */
		int level;
		/** start position of search node in WaveletMatrix */
		int start;
		/** prefix of search node in WaveletMatrix */
		int path;
	}
	private class SearchNode {
		/**
		 * contained state are bit assigned in the direction of MSB to LSB.
		 * MSB is contained state of first dimension, second bit is contained state of second dimension, and so on.
		 */
		int contained;
		/** dimension of current processing */
		int dim;
		/** start position of root node */
		int rootStart;
		/** width of node */
		int width;
		/** search node in WaveletMatrix of every dimensions */
		WMNode[] wmNodes;
		SearchNode() {
			wmNodes = new WMNode[numDim];
			for (int d = 0; d < numDim; d++) {
				wmNodes[d] = new WMNode();
			}
		}
	}
	private class SearchContext {
		/** work area */
		int[] work1;
		/** minimum point of query range */
		int[] qmins;
		/** maximum point of query range */
		int[] qmaxs;
		/** current search state */
		SearchNode[] snodes;
		/** current pointer of search stack structure */
		int sp;
		SearchContext() {
			work1 = new int[numDim];
			qmins = new int[numDim];
			qmaxs = new int[numDim];
			int stackDepth = 0;
			for (int d = 0; d < numDim; d++) {
				stackDepth += zoWM[d].depth();
			}
			stackDepth *= 2;
			snodes = new SearchNode[stackDepth];
			for (int i = 0; i < stackDepth; i++) {
				snodes[i] = new SearchNode();
			}
			sp = -1;
		}
		SearchNode current() {
			return snodes[sp];
		}
		SearchNode reserve() {
			sp++;
			return snodes[sp];
		}
		void release() {
			sp--;
		}
	}

	@Override
	public int count(double[] min, double[] max) {
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return 0;
		}

		SearchContext ctx = new SearchContext();

		// convert query range from real-number to rank-space
		for (int d = 0; d < numDim; d++) {
			int shift = rankShift[d];
			ctx.qmins[d] = (mdRankIndex[d].real2denserank(min[d])) << shift;
			ctx.qmaxs[d] = ((mdRankIndex[d].real2denserank(Math.nextUp(max[d])) - 1) << shift) | ((1 << shift) - 1) ;
		}

		SearchNode qnode = ctx.reserve();
		try {
			// initialize virtual node for search processing
			qnode.contained = 0;
			for (int d = 0; d < numDim; d++) {
				int qmin = ctx.qmins[d];
				int qmax = ctx.qmaxs[d];
				int vmin = zoWM[d].minValue();
				int vmax = zoWM[d].maxValue();
				if (vmin > qmax || vmax < qmin) {
					// stop if all point ranges are out of query range
					return 0;
				}
				else if (qmin <= vmin && vmax <= qmax) {
					// update contained state if all point ranges are contained in query range
					qnode.contained |= 0x80000000 >>> d;
				}
			}

			qnode.dim = numDim - 1;
			qnode.rootStart = 0;
			qnode.width = numData;
			for (int d = 0; d < numDim; d++) {
				qnode.wmNodes[d].level = zoWM[d].depth() - 1;
				qnode.wmNodes[d].start = 0;
				qnode.wmNodes[d].path = 0;
			}

			// search from root
			return countRecursive(ctx);
		}
		finally {
			ctx.release();
		}
	}

	private int countRecursive(SearchContext ctx) {
		final SearchNode curNode = ctx.current();

		final int contained = curNode.contained;
		final int notContained = numDim - Integer.bitCount(contained);
		final int width = curNode.width;

		if (notContained == 0) {
			// now, points are contained in query range of every dimensions.
			return width;
		}
		else if (width < stopWidth) {
			// now, number of points are below threshold.
			// count with sequential-scan
			return countRootScan(ctx);
		}
		else if (notContained == 1) {
			// now, points are contained in query range of (d - 1) dimensions.
			// count with WaveletMatrix on final dimension
			return count1D(ctx);
		}

		final int dim = curNode.dim < 0 ? numDim - 1 : curNode.dim;
		final int dimBit = 0x80000000 >>> dim;
		final int dimContained = contained & dimBit;
		final int rootStart = curNode.rootStart;
		final int qmin = ctx.qmins[dim];
		final int qmax = ctx.qmaxs[dim];

		final WMNode[] wmNodes = curNode.wmNodes;
		final WMNode wmNode = wmNodes[dim];
		final int level = wmNode.level;
		final int start = wmNode.start;
		final int end = start + width;
		final int path = wmNode.path;
		final int levelBit = 1 << level;

		final SuccinctBitVector sbv = zoWM[dim].mWM[level];
		final int s1 = sbv.rank1(start);
		final int e1 = sbv.rank1(end);
		final int s0 = start - s1;
		final int e0 = end - e1;
		final int width0 = e0 - s0;
		final int width1 = e1 - s1;

		int freqResult = 0;

		// search zero-bits child
		CHILD_0: if (s0 < e0) {
			// [pmin, pmax] : possible range of child
			final int pmin = path;
			final int pmax = pmin | (levelBit - 1);
			int childContained = contained;
			if (dimContained == 0) {
				if (pmin > qmax || pmax < qmin) {
					// out of query range
					break CHILD_0;
				}
				else if (pmin >= qmin && pmax <= qmax) {
					// in range. update child contained state.
					childContained |= dimBit;
				}
			}

			final SearchNode childNode = ctx.reserve();
			try {
				// initialize virtual node for searching child
				childNode.contained = childContained;
				childNode.dim = dim - 1;
				childNode.rootStart = rootStart;
				childNode.width = width0;
				for (int d = 0; d < numDim; d++) {
					if (d == dim) {
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level = level - 1;
						childWMNode.start = s0;
						childWMNode.path = pmin;
					}
					else {
						WMNode parentWMNode = wmNodes[d];
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level = parentWMNode.level;
						childWMNode.start = parentWMNode.start;
						childWMNode.path = parentWMNode.path;
					}
				}
				// search child
				freqResult += countRecursive(ctx);
			}
			finally {
				ctx.release();
			}
		}
		// search one-bits child
		CHILD_1: if (s1 < e1) {
			// [pmin, pmax] : possible range of child
			final int pmin = path | levelBit;
			final int pmax = pmin | (levelBit - 1);
			int childContained = contained;
			if (dimContained == 0) {
				if (pmin > qmax || pmax < qmin) {
					// out of query range
					break CHILD_1;
				}
				else if (pmin >= qmin && pmax <= qmax) {
					// in range. update child contained state.
					childContained |= dimBit;
				}
			}

			final SearchNode childNode = ctx.reserve();
			try {
				// initialize virtual node for searching child
				childNode.contained = childContained;
				childNode.dim = dim - 1;
				childNode.rootStart = rootStart + width0;
				childNode.width = width1;
				for (int d = 0; d < numDim; d++) {
					if (d == dim) {
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level = level - 1;
						childWMNode.start = s1 + zoWM[dim].mZ[level];
						childWMNode.path = pmin;
					}
					else {
						WMNode parentWMNode = wmNodes[d];
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level = parentWMNode.level;
						childWMNode.start = parentWMNode.start + width0;
						childWMNode.path = parentWMNode.path;
					}
				}
				// search child
				freqResult += countRecursive(ctx);
			}
			finally {
				ctx.release();
			}
		}

		return freqResult;
	}

	// Returns count using sequential-scan
	private int countRootScan(SearchContext ctx) {
		final int dimension = numDim;
		final int[] mins = ctx.qmins;
		final int[] maxs = ctx.qmaxs;
		final SearchNode curNode = ctx.current();
		final int contained = curNode.contained;
		final int rootStart = curNode.rootStart;
		final int rootEnd = rootStart + curNode.width;
		final int notContained = dimension - Integer.bitCount(contained);
		int freqResult = 0;
		if (notContained == 1) {
			// sequential-scan on final dimension
			final int last1d = Integer.numberOfLeadingZeros(~contained);
			final int[] basearray = zoPoints[last1d];
			final int min = mins[last1d];
			final int max = maxs[last1d];
			for (int j = rootStart; j < rootEnd; j++) {
				final int val = basearray[j];
				if (val >= min && val <= max) {
					freqResult++;
				}
			}
		}
		else {
			// sequential-scan on not contained dimensions
			int[] dims = ctx.work1;
			for (int ptr = 0, d = 0; d < dimension; d++) {
				if (contained << d < 0) {
					continue;
				}
				dims[ptr++] = d;
			}
			JLOOP: for (int j = rootStart; j < rootEnd; j++) {
				for (int ptr = 0; ptr < notContained; ptr++) {
					final int d = dims[ptr];
					final int val = zoPoints[d][j];
					if (val < mins[d] || val > maxs[d]) {
						continue JLOOP;
					}
				}
				freqResult++;
			}
		}
		return freqResult;
	}

	// returns count using WaveletMatrix
	private int count1D(SearchContext ctx) {
		final SearchNode curNode = ctx.current();

		final int last1d = Integer.numberOfLeadingZeros(~curNode.contained);
		WaveletMatrix wm = zoWM[last1d];
		final WMNode wmNode = curNode.wmNodes[last1d];
		final int lv = wmNode.level;
		final int start = wmNode.start;
		final int end = start + curNode.width;

		// query range
		final int qmin = ctx.qmins[last1d];
		final int qmax = ctx.qmaxs[last1d];

		// path range (possible range of WaveletMatrix node)
		final int pmin = wmNode.path;
		final int pmax = pmin | ((1 << (lv+1)) - 1);

		// relation of query range and path range intersection
		//      [qmin    ,    qmax]      query range
		// [pmin,pmax]                   path range contain minimum of query range
		//                   [pmin,pmax] path range contain maximum of query range
		// [pmin         ,         pmax] path range fully contain query range

		if (pmax <= qmax) {
			return end - start - little1D(lv, start, end, qmin, wm);
		}
		else if (qmin <= pmin) {
			return little1D(lv, start, end, qmax + 1, wm);
		}
		else {
			return little1D(lv, start, end, qmax + 1, wm) - little1D(lv, start, end, qmin, wm);
		}
	}

	// Returns occurrence which is less than specified value c from sub-tree of WaveletMatrix wm.
	// The sub-tree of WaveletMatrix is specified by level lv and position range [s, e)
	private int little1D(int lv, int s, int e, int c, final WaveletMatrix wm) {
		int result = 0;
		c <<= ~lv;
		for (; lv >= 0; c <<= 1, lv--) {
			SuccinctBitVector sbv = wm.mWM[lv];
			int s1 = sbv.rank1(s);
			int e1 = sbv.rank1(e);
			if (c < 0) {
				// If bit of c is 1, the 0's children are always less than c. So add the number of 0's children to result.
				if (s1 < e1) {
					result += (e - e1) - (s - s1); // equivalent to e0 - s0
					s = s1 + wm.mZ[lv];
					e = e1 + wm.mZ[lv];
				}
				else {
					result += e - s;
					break;
				}
			}
			else {
				s -= s1;
				e -= e1;
				if (s >= e) {
					break;
				}
			}
		}
		return result;
	}

	@Override
	public int[] report(double[] min, double[] max) {
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return new int[0];
		}

		Intervals intervals = new Intervals(reportBuffersInitialCapacity);
		Intervals.IntervalCursor cursor = intervals.getCursor();

		// search intervals
		int freqResult = intervals(min, max, intervals);
		if (freqResult <= 0) {
			return new int[0];
		}

		// retrieved intervals are root-intervals or inner-intervals.
		// so, convert inner-intervals to root-intervals.

		int[] indexArray = new int[freqResult];
		int indexPtr = 0;

		Intervals workIntervals = new Intervals(reportBuffersInitialCapacity);
		Intervals.IntervalCursor workCursor = workIntervals.getCursor();
		IntBuffer work1 = new IntBuffer(reportBuffersInitialCapacity);
		IntBuffer work2 = new IntBuffer(reportBuffersInitialCapacity);

		cursor.reset();
		while (cursor.next()) {
			if (cursor.root) {
				// root-intervals
				for (int i = cursor.s; i < cursor.e; i++) {
					indexArray[indexPtr++] = pointers[i];
				}
			}
			else {
				// convert inner-intervals to root-intervals
				workIntervals.clear();
				zoWM[cursor.treeId].innerInterval2rootIntervals(cursor.level, cursor.s, cursor.e, workIntervals, work1, work2);
				workCursor.reset();
				while (workCursor.next()) {
					for (int i = workCursor.s; i < workCursor.e; i++) {
						indexArray[indexPtr++] = pointers[i];
					}
				}
			}
		}

		return indexArray;
	}

	@Override
	public int[] sample(double[] min, double[] max, int sampleCount, Random rnd) {
		if (sampleCount <= 0) {
			throw new IllegalArgumentException("sampleCount is not positive number");
		}
		if (rnd == null) {
			throw new IllegalArgumentException("rnd is null.");
		}
		if (!Utils.validateOrthogonalRange(min, max, numDim)) {
			return new int[0];
		}

		Intervals intervals = new Intervals(reportBuffersInitialCapacity);
		Intervals.IntervalCursor cursor = intervals.getCursor();
		Intervals workIntervals = new Intervals(reportBuffersInitialCapacity);
		Intervals.IntervalCursor workCursor = workIntervals.getCursor();

		// search intervals
		int freqResult = intervals(min, max, intervals);
		if (freqResult <= 0) {
			return new int[0];
		}

		if (freqResult <= sampleCount) {
			sampleCount = freqResult;
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
			cursor.reset();
			INTERVAL_LOOP: while (cursor.next()) {
				int w = cursor.e - cursor.s;
				while (processed <= sample && sample < processed + w) {
					int ss = cursor.s + sample - processed;
					if (cursor.root) {
						workIntervals.addRoot(ss, ss + 1);
					}
					else {
						workIntervals.add(ss, ss + 1, cursor.treeId, cursor.level);
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
			Intervals swapIntervals = intervals;
			Intervals.IntervalCursor swapCursor = cursor;
			intervals = workIntervals;
			cursor = workCursor;
			workIntervals = swapIntervals;
			workCursor = swapCursor;
		}

		// sampled intervals are root-intervals or inner-intervals.
		// so, convert inner-intervals to root-intervals.

		int[] indexArray = new int[sampleCount];
		int indexPtr = 0;
		IntBuffer work1 = new IntBuffer(reportBuffersInitialCapacity);
		IntBuffer work2 = new IntBuffer(reportBuffersInitialCapacity);

		cursor.reset();
		while (cursor.next()) {
			if (cursor.root) {
				// root-intervals
				for (int i = cursor.s; i < cursor.e; i++) {
					indexArray[indexPtr++] = pointers[i];
				}
			}
			else {
				// convert inner-intervals to root-intervals
				workIntervals.clear();
				zoWM[cursor.treeId].innerInterval2rootIntervals(cursor.level, cursor.s, cursor.e, workIntervals, work1, work2);
				workCursor.reset();
				while (workCursor.next()) {
					for (int i = workCursor.s; i < workCursor.e; i++) {
						indexArray[indexPtr++] = pointers[i];
					}
				}
			}
		}

		return indexArray;
	}

	private int intervals(double[] min, double[] max, Intervals result) {
		result.clear();

		SearchContext ctx = new SearchContext();

		// convert query range from real-number to rank-space
		for (int d = 0; d < numDim; d++) {
			int shift = rankShift[d];
			ctx.qmins[d] = (mdRankIndex[d].real2denserank(min[d])) << shift;
			ctx.qmaxs[d] = ((mdRankIndex[d].real2denserank(Math.nextUp(max[d])) - 1) << shift) | ((1 << shift) - 1) ;
		}

		SearchNode qnode = ctx.reserve();
		try {
			// initialize virtual node for search processing
			qnode.contained = 0;
			for (int d = 0; d < numDim; d++) {
				int qmin = ctx.qmins[d];
				int qmax = ctx.qmaxs[d];
				int vmin = zoWM[d].minValue();
				int vmax = zoWM[d].maxValue();
				if (vmin > qmax || vmax < qmin) {
					// stop if all point ranges are out of query range
					return 0;
				}
				else if (qmin <= vmin && vmax <= qmax) {
					// update contained state if all point ranges are contained in query range
					qnode.contained |= 0x80000000 >>> d;
				}
			}

			qnode.dim = numDim - 1;
			qnode.rootStart = 0;
			qnode.width = numData;
			for (int d = 0; d < numDim; d++) {
				qnode.wmNodes[d].level = zoWM[d].depth() - 1;
				qnode.wmNodes[d].start = 0;
				qnode.wmNodes[d].path = 0;
			}

			// search from root
			intervalsRecursive(ctx, result);
		}
		finally {
			ctx.release();
		}
		return result.getTotalLength();
	}

	private void intervalsRecursive(SearchContext ctx, Intervals result) {
		final SearchNode curNode = ctx.current();

		final int contained = curNode.contained;
		final int notContained = numDim - Integer.bitCount(contained);
		final int width = curNode.width;

		if (notContained == 0) {
			// now, points are contained in query range of every dimensions.
			result.addRoot(curNode.rootStart, curNode.rootStart + width);
			return;
		}
		else if (width < stopWidth) {
			// now, number of points are below threshold.
			// search intervals with sequential-scan
			intervalsRootScan(ctx, result);
			return;
		}
		else if (notContained == 1) {
			// now, points are contained in query range of (d - 1) dimensions.
			// search intervals with WaveletMatrix on final dimension
			final int last1d = Integer.numberOfLeadingZeros(~contained);
			final WMNode wmNode = curNode.wmNodes[last1d];
			intervals1D(last1d, wmNode.level, wmNode.start, wmNode.start + width, wmNode.path, ctx.qmins[last1d], ctx.qmaxs[last1d], result);
			return;
		}

		final int dim = curNode.dim < 0 ? numDim - 1 : curNode.dim;
		final int dimBit = 0x80000000 >>> dim;
		final int dimContained = contained & dimBit;
		final int rootStart = curNode.rootStart;
		final int qmin = ctx.qmins[dim];
		final int qmax = ctx.qmaxs[dim];

		final WMNode[] wmNodes = curNode.wmNodes;
		final WMNode wmNode = wmNodes[dim];
		final int level = wmNode.level;
		final int start = wmNode.start;
		final int end = start + width;
		final int path = wmNode.path;
		final int levelBit = 1 << level;

		final SuccinctBitVector sbv = zoWM[dim].mWM[level];
		final int s1 = sbv.rank1(start);
		final int e1 = sbv.rank1(end);
		final int s0 = start - s1;
		final int e0 = end - e1;
		final int width0 = e0 - s0;
		final int width1 = e1 - s1;

		// search zero-bits child
		CHILD_0: if (s0 < e0) {
			// [pmin, pmax] : possible range of child
			final int pmin = path;
			final int pmax = pmin | (levelBit - 1);
			int childContained = contained;
			if (dimContained == 0) {
				if (pmin > qmax || pmax < qmin) {
					// out of query range
					break CHILD_0;
				}
				else if (pmin >= qmin && pmax <= qmax) {
					// in range. update child contained state.
					childContained |= dimBit;
				}
			}

			final SearchNode childNode = ctx.reserve();
			try {
				// initialize virtual node for searching child
				childNode.contained = childContained;
				childNode.dim = dim - 1;
				childNode.rootStart = rootStart;
				childNode.width = width0;
				for (int d = 0; d < numDim; d++) {
					if (d == dim) {
						WMNode childAxisNode = childNode.wmNodes[d];
						childAxisNode.level = level - 1;
						childAxisNode.start = s0;
						childAxisNode.path = pmin;
					}
					else {
						WMNode parentWMNode = wmNodes[d];
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level= parentWMNode.level;
						childWMNode.start = parentWMNode.start;
						childWMNode.path = parentWMNode.path;
					}
				}
				// search child
				intervalsRecursive(ctx, result);
			}
			finally {
				ctx.release();
			}
		}
		// search one-bits child
		CHILD_1: if (s1 < e1) {
			// [pmin, pmax] : possible range of child
			final int pmin = path | levelBit;
			final int pmax = pmin | (levelBit - 1);
			int childContained = contained;
			if (dimContained == 0) {
				if (pmin > qmax || pmax < qmin) {
					// out of query range
					break CHILD_1;
				}
				else if (pmin >= qmin && pmax <= qmax) {
					// in range. update child contained state.
					childContained |= dimBit;
				}
			}

			final SearchNode childNode = ctx.reserve();
			try {
				// initialize virtual node for searching child
				childNode.contained = childContained;
				childNode.dim = dim - 1;
				childNode.rootStart = rootStart + width0;
				childNode.width = width1;
				for (int d = 0; d < numDim; d++) {
					if (d == dim) {
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level = level - 1;
						childWMNode.start = s1 + zoWM[dim].mZ[level];
						childWMNode.path = pmin;
					}
					else {
						WMNode parentWMNode = wmNodes[d];
						WMNode childWMNode = childNode.wmNodes[d];
						childWMNode.level = parentWMNode.level;
						childWMNode.start = parentWMNode.start + width0;
						childWMNode.path = parentWMNode.path;
					}
				}
				// search child
				intervalsRecursive(ctx, result);
			}
			finally {
				ctx.release();
			}
		}
	}

	// Search intervals contained in query range using sequential-scan
	private void intervalsRootScan(SearchContext ctx, Intervals result) {
		final int dimension = numDim;
		final int[] mins = ctx.qmins;
		final int[] maxs = ctx.qmaxs;
		final SearchNode curNode = ctx.current();
		final int contained = curNode.contained;
		final int rootStart = curNode.rootStart;
		final int rootEnd = rootStart + curNode.width;
		final int notContained = dimension - Integer.bitCount(contained);;
		int intervalStart = -1;
		if (notContained == 1) {
			// sequential-scan on final dimension
			final int last1d = Integer.numberOfLeadingZeros(~contained);
			final int[] basearray = zoPoints[last1d];
			final int min = mins[last1d];
			final int max = maxs[last1d];
			for (int j = rootStart; j < rootEnd; j++) {
				int val = basearray[j];
				if (val >= min && val <= max) {
					if (intervalStart < 0) {
						intervalStart = j;
					}
				}
				else {
					if (intervalStart >= 0) {
						result.addRoot(intervalStart, j);
						intervalStart = -1;
					}
				}
			}
		}
		else {
			// sequential-scan on not contained dimensions
			final int[] dims = ctx.work1;
			for (int ptr = 0, d = 0; d < dimension; d++) {
				if (contained << d < 0) {
					continue;
				}
				dims[ptr++] = d;
			}
			JLOOP: for (int j = rootStart; j < rootEnd; j++) {
				for (int ptr = 0; ptr < notContained; ptr++) {
					final int d = dims[ptr];
					final int val = zoPoints[d][j];
					if (val < mins[d] || val > maxs[d]) {
						if (intervalStart >= 0) {
							result.addRoot(intervalStart, j);
							intervalStart = -1;
						}
						continue JLOOP;
					}
				}
				if (intervalStart < 0) {
					intervalStart = j;
				}
			}
		}
		if (intervalStart >= 0) {
			result.addRoot(intervalStart, rootEnd);
		}
	}

	// Search intervals which are contained in query range [qmin, qmax] from sub-tree of WaveletMatrix corresponding to specified dimension axis.
	// The sub-tree of WaveletMatrix is specified by level lv and position range [s, e)
	private void intervals1D(int dim, int lv, int s, int e, int path, int qmin, int qmax, Intervals result) {
		final WaveletMatrix wm = zoWM[dim];
		final SuccinctBitVector sbv = wm.mWM[lv];
		final int s1 = sbv.rank1(s);
		final int e1 = sbv.rank1(e);
		final int s0 = s - s1;
		final int e0 = e - e1;
		final int levelBit = 1 << lv;

		// search zero-bits child
		CHILD_0: if (s0 < e0) {
			// [pmin, pmax] : possible range of child
			final int pmin = path;
			final int pmax = pmin | (levelBit - 1);
			if (pmin > qmax || pmax < qmin) {
				// out of query range
				break CHILD_0;
			}
			else if (pmin >= qmin && pmax <= qmax) {
				// in range
				result.add(s0, e0, dim, lv - 1);
				break CHILD_0;
			}
			intervals1D(dim, lv - 1, s0, e0, pmin, qmin, qmax, result);
		}
		// search one-bits child
		CHILD_1: if (s1 < e1) {
			// [pmin, pmax] : possible range of child
			final int pmin = path | levelBit;
			final int pmax = pmin | (levelBit - 1);
			if (pmin > qmax || pmax < qmin) {
				// out of query range
				break CHILD_1;
			}
			else if (pmin >= qmin && pmax <= qmax) {
				// in range
				result.add(s1 + wm.mZ[lv], e1 + wm.mZ[lv], dim, lv - 1);
				break CHILD_1;
			}
			intervals1D(dim, lv - 1, s1 + wm.mZ[lv], e1 + wm.mZ[lv], pmin, qmin, qmax, result);
		}
	}

}
