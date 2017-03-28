/*
KDW-tree

Copyright (c) 2014-2017 NEC Solution Innovators, Ltd.

This software is released under the MIT License, See the LICENSE file
in the project root for more information.
*/
package com.necsoft.vtc.kdwtree;

/**
 * variable size array-like structure which stores int.
 */
class IntBuffer {
	private int[] elements;
	private int length = 0;

	/**
	 * Constructor. create instance with initial capacity 8192. 
	 */
	IntBuffer() {
		this(8192);
	}
	/**
	 * Constructor. create instance with specified initial capacity.
	 * @param initialCapacity	initial capacity.
	 */
	IntBuffer(int initialCapacity) {
		elements = new int[initialCapacity];
	}
	private void moreElements() {
		if (length > elements.length) {
			int[] newElements = new int[length + (length >> 2)]; // * 1.25
			System.arraycopy(elements, 0, newElements, 0, elements.length);
			elements = newElements;
		}
	}
	/**
	 * Returns the number of elements in this instance.
	 * @return the number of elements
	 */
	int length() {
		return length;
	}
	/**
	 * Appends the specified value to the end of this.
	 * @param v	value to be appended
	 */
	void add(int v) {
		length++;
		moreElements();
		elements[length-1] = v;
	}
	/**
	 * Appends the specified values to the end of this.
	 * @param v1	first value to be appended
	 * @param v2	second value to be appended
	 */
	void add(int v1, int v2) {
		length += 2;
		moreElements();
		elements[length-2] = v1;
		elements[length-1] = v2;
	}
	/**
	 * Appends the specified values to the end of this.
	 * @param v1	first value to be appended
	 * @param v2	second value to be appended
	 * @param v3	third value to be appended
	 */
	void add(int v1, int v2, int v3) {
		length += 3;
		moreElements();
		elements[length-3] = v1;
		elements[length-2] = v2;
		elements[length-1] = v3;
	}
	/**
	 * Appends the specified values to the end of this.
	 * @param v1	first value to be appended
	 * @param v2	second value to be appended
	 * @param v3	third value to be appended
	 * @param v4	fourth value to be appended
	 */
	void add(int v1, int v2, int v3, int v4) {
		length += 4;
		moreElements();
		elements[length-4] = v1;
		elements[length-3] = v2;
		elements[length-2] = v3;
		elements[length-1] = v4;
	}
	/**
	 * Appends the other IntBuffer contents to the end of this.
	 * @param adder	IntBuffer to be appended
	 */
	void addAll(IntBuffer adder) {
		int oldLength = length;
		length += adder.length;
		moreElements();
		System.arraycopy(adder.elements, 0, elements, oldLength, adder.length);
	}
	/**
	 * Replaces the element at the specified position in this with the specified value.
	 * @param index	index of the element to replace
	 * @param v	value to be stored at the specified position
	 */
	void set(int index, int v) {
		if (index >= length) {
			length = index + 1;
		}
		moreElements();
		elements[index] = v;
	}
	/**
	 * Replaces the elements at the specified position in this with the specified values.
	 * @param index	first index of the element to replace
	 * @param v1	first value to be stored at the specified position
	 * @param v2	second value to be stored at the specified position
	 */
	void set(int index, int v1, int v2) {
		if (index + 1 >= length) {
			length = index + 2;
		}
		moreElements();
		elements[index] = v1;
		elements[index+1] = v2;
	}
	/**
	 * Replaces the elements at the specified position in this with the specified values.
	 * @param index	first index of the element to replace
	 * @param v1	first value to be stored at the specified position
	 * @param v2	second value to be stored at the specified position
	 * @param v3	third value to be stored at the specified position
	 * @param v4	fourth value to be stored at the specified position
	 */
	void set(int index, int v1, int v2, int v3, int v4) {
		if (index + 3 >= length) {
			length = index + 4;
		}
		moreElements();
		elements[index] = v1;
		elements[index+1] = v2;
		elements[index+2] = v3;
		elements[index+3] = v4;
	}
	/**
	 * Returns the element at the specified position in this.
	 * @param index	index of the element to return
	 * @return the value at the specified position.
	 */
	int get(int index) {
		if (index >= length) {
			return 0;
		}
		return elements[index];
	}
	/**
	 * Returns an array containing all of the elements in this in proper sequence (from first to last element).
	 * @return an array containing all of the elements in this in proper sequence
	 */
	int[] toArray() {
		int[] array = new int[length];
		System.arraycopy(elements, 0, array, 0, length);
		return array;
	}
	/**
	 * Sets the length to 0.
	 */
	void clear() {
		length = 0;
	}

	/**
	 * for internal use
	 */
	int[] internal_baseArray() {
		return elements;
	}
	/**
	 * for internal use
	 */
	void internal_expand(int size) {
		if (size > elements.length) {
			int[] newElements = new int[size + (size >> 2)]; // * 1.25
			System.arraycopy(elements, 0, newElements, 0, elements.length);
			elements = newElements;
		}
	}
	/**
	 * for internal use
	 */
	void internal_setLength(int length) {
		this.length = length;
	}

}
