/*******************************************************************************
 *  _  _ ___ ___     _ _
 * | \| | __/ __| __| | |__
 * | .` | _|\__ \/ _` | '_ \
 * |_|\_|_| |___/\__,_|_.__/
 *
 * Copyright (C) 2014-2016 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * As a special exception, the copyright holders give permission to link the
 * code of portions of this program with the OpenSSL library under certain
 * conditions as described in each individual source file and distribute
 * linked combinations including the program with the OpenSSL library. You
 * must comply with the GNU Affero General Public License in all respects for
 * all of the code used other than as permitted herein. If you modify file(s)
 * with this exception, you may extend this exception to your version of the
 * file(s), but you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version. If you delete this
 * exception statement from all source files in the program, then also delete
 * it in the license file.
 *
 ******************************************************************************/

package com.nfsdb.std;

import com.nfsdb.misc.Unsafe;

import java.util.Arrays;

public class IntList implements Mutable {
    private static final int DEFAULT_ARRAY_SIZE = 16;
    private static final int noEntryValue = -1;
    private int[] buffer;
    private int pos = 0;
    private StringBuilder toStringBuilder;

    @SuppressWarnings("unchecked")
    public IntList() {
        this(DEFAULT_ARRAY_SIZE);
    }

    @SuppressWarnings("unchecked")
    public IntList(int capacity) {
        this.buffer = new int[capacity < DEFAULT_ARRAY_SIZE ? DEFAULT_ARRAY_SIZE : capacity];
    }

    public void add(int value) {
        ensureCapacity0(pos + 1);
        Unsafe.arrayPut(buffer, pos++, value);
    }

    public void add(int index, int element) {
        ensureCapacity(++pos);
        System.arraycopy(buffer, index, buffer, index + 1, pos - index - 1);
        Unsafe.arrayPut(buffer, index, element);
    }

    public void addAll(IntList that) {
        int p = pos;
        int s = that.size();
        ensureCapacity(p + s);
        System.arraycopy(that.buffer, 0, this.buffer, p, s);
    }

    public int binarySearch(int v) {
        int low = 0;
        int high = pos;

        while (low < high) {

            if (high - low < 65) {
                return scanSearch(v);
            }

            int mid = (low + high - 1) >>> 1;
            long midVal = Unsafe.arrayGet(buffer, mid);

            if (midVal < v)
                low = mid + 1;
            else if (midVal > v)
                high = mid - 1;
            else
                return mid;
        }
        return -(low + 1);
    }

    public void clear() {
        pos = 0;
        Arrays.fill(buffer, noEntryValue);
    }

    public void clear(int capacity) {
        setAll(capacity, noEntryValue);
    }

    public void ensureCapacity(int capacity) {
        ensureCapacity0(capacity);
        pos = capacity;
    }

    public void extendAndSet(int index, int value) {
        ensureCapacity0(index + 1);
        if (index >= pos) {
            pos = index + 1;
        }
        Unsafe.arrayPut(buffer, index, value);
    }

    public int get(int index) {
        if (index < pos) {
            return Unsafe.arrayGet(buffer, index);
        }
        throw new ArrayIndexOutOfBoundsException(index);
    }

    public int getLast() {
        if (pos > 0) {
            return Unsafe.arrayGet(buffer, pos - 1);
        }
        return noEntryValue;
    }

    /**
     * Returns element at the specified position. This method does not do
     * bounds check and may cause memory corruption if index is out of bounds.
     * Instead the responsibility to check bounds is placed on application code,
     * which is often the case anyway, for example in indexed for() loop.
     *
     * @param index of the element
     * @return element at the specified position.
     */
    public int getQuick(int index) {
        return Unsafe.arrayGet(buffer, index);
    }

    /**
     * Returns element at the specified position or null, if element index is
     * out of bounds. This is an alternative to throwing runtime exception or
     * doing preemptive check.
     *
     * @param index position of element
     * @return element at the specified position.
     */
    public int getQuiet(int index) {
        if (index < pos) {
            return Unsafe.arrayGet(buffer, index);
        }
        return noEntryValue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int hashCode = 1;
        for (int i = 0, n = pos; i < n; i++) {
            int v = getQuick(i);
            hashCode = 31 * hashCode + (v == noEntryValue ? 0 : v);
        }
        return hashCode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object that) {
        return this == that || that instanceof IntList && equals((IntList) that);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        if (toStringBuilder == null) {
            toStringBuilder = new StringBuilder();
        }

        toStringBuilder.setLength(0);
        toStringBuilder.append('[');
        for (int i = 0, k = size(); i < k; i++) {
            if (i > 0) {
                toStringBuilder.append(',');
            }
            toStringBuilder.append(get(i));
        }
        toStringBuilder.append(']');
        return toStringBuilder.toString();
    }

    public void increment(int index) {
        Unsafe.arrayPut(buffer, index, Unsafe.arrayGet(buffer, index) + 1);
    }

    public boolean remove(int key) {
        for (int i = 0, n = size(); i < n; i++) {
            if (key == getQuick(i)) {
                removeIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void removeIndex(int index) {
        if (pos < 1 || index >= pos) {
            return;
        }
        int move = pos - index - 1;
        if (move > 0) {
            System.arraycopy(buffer, index + 1, buffer, index, move);
        }
        Unsafe.arrayPut(buffer, --pos, noEntryValue);
    }

    public void set(int index, int element) {
        if (index < pos) {
            Unsafe.arrayPut(buffer, index, element);
            return;
        }
        throw new ArrayIndexOutOfBoundsException(index);
    }

    public void setAll(int capacity, int value) {
        ensureCapacity0(capacity);
        pos = 0;
        Arrays.fill(buffer, value);
    }

    public void setQuick(int index, int value) {
        Unsafe.arrayPut(buffer, index, value);
    }

    public int size() {
        return pos;
    }

    public void zero(int value) {
        Arrays.fill(buffer, 0, pos, value);
    }

    @SuppressWarnings("unchecked")
    private void ensureCapacity0(int capacity) {
        int l = buffer.length;
        if (capacity > l) {
            int newCap = Math.max(l << 1, capacity);
            int[] buf = new int[newCap];
            System.arraycopy(buffer, 0, buf, 0, l);
            this.buffer = buf;
        }
    }

    private boolean equals(IntList that) {
        if (this.pos == that.pos) {
            for (int i = 0, n = pos; i < n; i++) {
                int lhs = this.getQuick(i);
                if (lhs == noEntryValue) {
                    return that.getQuick(i) == noEntryValue;
                } else if (lhs == that.getQuick(i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int scanSearch(int v) {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            long f = getQuick(i);
            if (f == v) {
                return i;
            }
            if (f > v) {
                return -(i + 1);
            }
        }
        return -(sz + 1);
    }

}