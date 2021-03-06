/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2020 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.questdb.std;

import java.util.Arrays;


public class CharSequenceIntHashMap extends AbstractCharSequenceHashSet {
    private static final int NO_ENTRY_VALUE = -1;
    private final int noEntryValue;
    private final ObjList<CharSequence> list;
    private int[] values;

    public CharSequenceIntHashMap() {
        this(8);
    }

    public CharSequenceIntHashMap(int initialCapacity) {
        this(initialCapacity, 0.5, NO_ENTRY_VALUE);
    }

    public CharSequenceIntHashMap(int initialCapacity, double loadFactor, int noEntryValue) {
        super(initialCapacity, loadFactor);
        this.noEntryValue = noEntryValue;
        this.list = new ObjList<>(capacity);
        values = new int[capacity];
        clear();
    }

    public final void clear() {
        super.clear();
        list.clear();
        Arrays.fill(values, noEntryValue);
    }

    public void increment(CharSequence key) {
        int index = keyIndex(key);
        if (index < 0) {
            int index1 = -index - 1;
            Unsafe.arrayPut(values, -index - 1, values[index1] + 1);
        } else {
            putAt0(index, key.toString(), 0);
        }
    }

    public ObjList<CharSequence> keys() {
        return list;
    }

    public boolean contains(CharSequence key) {
        return keyIndex(key) < 0;
    }

    public int get(CharSequence key) {
        return valueAt(keyIndex(key));
    }

    public boolean put(CharSequence key, int value) {
        return putAt(keyIndex(key), key, value);
    }

    public void putAll(CharSequenceIntHashMap other) {
        CharSequence[] otherKeys = other.keys;
        int[] otherValues = other.values;
        for (int i = 0, n = otherKeys.length; i < n; i++) {
            if (otherKeys[i] != noEntryKey) {
                put(otherKeys[i], otherValues[i]);
            }
        }
    }

    public void removeAt(int index) {
        if (index < 0) {
            int index1 = -index - 1;
            CharSequence key = keys[index1];
            super.removeAt(index);
            list.remove(key);
        }
    }

    @Override
    protected void erase(int index) {
        Unsafe.arrayPut(keys, index, noEntryKey);
        Unsafe.arrayPut(values, index, noEntryValue);
    }

    @Override
    protected void move(int from, int to) {
        Unsafe.arrayPut(keys, to, keys[from]);
        Unsafe.arrayPut(values, to, values[from]);
        erase(from);
    }

    public boolean putAt(int index, CharSequence key, int value) {
        if (index < 0) {
            Unsafe.arrayPut(values, -index - 1, value);
            return false;
        }
        String keyString = key.toString();
        putAt0(index, keyString, value);
        list.add(keyString);
        return true;
    }

    public int valueQuick(int index) {
        return get(list.getQuick(index));
    }

    public void putIfAbsent(CharSequence key, int value) {
        int index = keyIndex(key);
        if (index > -1) {
            putAt0(index, key.toString(), value);
        }
    }

    public int valueAt(int index) {
        int index1 = -index - 1;
        return index < 0 ? values[index1] : noEntryValue;
    }

    private void putAt0(int index, CharSequence key, int value) {
        Unsafe.arrayPut(keys, index, key);
        Unsafe.arrayPut(values, index, value);
        if (--free == 0) {
            rehash();
        }
    }

    private void rehash() {
        int size = size();
        int newCapacity = capacity * 2;
        mask = newCapacity - 1;
        free = capacity = newCapacity;
        int arrayCapacity = (int) (newCapacity / loadFactor);

        int[] oldValues = values;
        CharSequence[] oldKeys = keys;
        this.keys = new CharSequence[arrayCapacity];
        this.values = new int[arrayCapacity];
        Arrays.fill(keys, null);

        free -= size;
        for (int i = oldKeys.length; i-- > 0; ) {
            CharSequence key = oldKeys[i];
            if (key != null) {
                final int index = keyIndex(key);
                Unsafe.arrayPut(keys, index, key);
                Unsafe.arrayPut(values, index, oldValues[i]);
            }
        }
    }
}