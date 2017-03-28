/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * for internal use.
 * structure for storing intervals with variable length.
 * interval components are [start, end) pair, id and level of WaveletMatrix.
 * <br> 1-length root-interval are stored (start).
 * <br> root-interval are stored (~start, end).
 * <br> inner-interval are stored (~start, ~end, id, level).
 */
class Intervals {

	private IntBuffer internalBuffer;
	private int count;
	private int totalLength;
	private boolean rootOnly;

	Intervals(int initialCapacity) {
		internalBuffer = new IntBuffer(initialCapacity);
		count = 0;
		totalLength = 0;
		rootOnly = true;
	}

	/**
	 * Returns number of stored intervals.
	 * @return number of stored intervals
	 */
	int getCount() {
		return count;
	}

	/**
	 * Returns summation of every interval length.
	 * @return summation of every interval length
	 */
	int getTotalLength() {
		return totalLength;
	}

	/**
	 * Returns true if this instance is empty or contains root-intervals only.
	 * @return true if this instance is empty or contains root-intervals only.
	 */
	boolean isRootOnly() {
		return rootOnly;
	}

	/**
	 * Clear internal structure and reset state.
	 */
	void clear() {
		internalBuffer.clear();
		count = 0;
		totalLength = 0;
		rootOnly = true;
	}
	/**
	 * Append 1-length root-interval
	 * @param s	start of interval
	 */
	void addRoot(int s) {
		internalBuffer.add(s);
		count++;
		totalLength++;
	}
	/**
	 * Append root-interval.
	 * @param s	start of interval
	 * @param e	end of interval (exclusive)
	 */
	void addRoot(int s, int e) {
		if (e - s == 1) {
			internalBuffer.add(s);
			count++;
			totalLength++;
		}
		else {
			internalBuffer.add(~s, e);
			count++;
			totalLength += e - s;
		}
	}
	/**
	 * Append inner-interval.
	 * @param s	start of interval
	 * @param e	end of interval (exclusive)
	 * @param id	id of WaveletMatrix
	 * @param lv	level of WaveletMatrix
	 */
	void add(int s, int e, int id, int lv) {
		internalBuffer.add(~s, ~e, id, lv);
		count++;
		totalLength += e - s;
		rootOnly = false;
	}

	/**
	 * Returns cursor for iterate contents.
	 * @return cursor
	 */
	IntervalCursor getCursor() {
		return new IntervalCursor();
	}

	/**
	 * for internal use.
	 * cursor for iterate contents.
	 */
	class IntervalCursor {
		int s;
		int e;
		int treeId;
		int level;
		boolean root;
		private int ptr = 0;

		/**
		 * reset cursor
		 */
		void reset() {
			ptr = 0;
		}
		/**
		 * Forward cursor, and returns true if this iteration has more elements.
		 * @return true if the iteration has more elements
		 */
		boolean next() {
			if (ptr < internalBuffer.length()) {
				int a0, a1;
				if ((a0 = internalBuffer.get(ptr++)) >= 0) {
					// 1-length root-interval
					s = a0;
					e = a0 + 1;
					treeId = -1;
					level = -1;
					root = true;
				}
				else if ((a1 = internalBuffer.get(ptr++)) >= 0) {
					// root-interval
					s = ~a0;
					e = a1;
					treeId = -1;
					level = -1;
					root = true;
				}
				else {
					// inner-interval
					s = ~a0;
					e = ~a1;
					treeId = internalBuffer.get(ptr++);
					level = internalBuffer.get(ptr++);
					root = false;
				}
				return true;
			}
			else {
				return false;
			}
		}
	}

}

