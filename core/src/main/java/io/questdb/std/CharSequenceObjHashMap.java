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
import java.util.Comparator;


public class CharSequenceObjHashMap<V> extends AbstractCharSequenceHashSet {
    private final ObjList<CharSequence> list;
    private V[] values;

    public CharSequenceObjHashMap() {
        this(8);
    }

    private CharSequenceObjHashMap(int initialCapacity) {
        this(initialCapacity, 0.5);
    }

    @SuppressWarnings("unchecked")
    private CharSequenceObjHashMap(int initialCapacity, double loadFactor) {
        super(initialCapacity, loadFactor);
        this.list = new ObjList<>(capacity);
        keys = new CharSequence[capacity];
        values = (V[]) new Object[capacity];
        clear();
    }

    public final void clear() {
        super.clear();
        list.clear();
    }

    public void putAll(CharSequenceObjHashMap<V> other) {
        CharSequence[] otherKeys = other.keys;
        V[] otherValues = other.values;
        for (int i = 0, n = otherKeys.length; i < n; i++) {
            if (otherKeys[i] != noEntryKey) {
                put(otherKeys[i], otherValues[i]);
            }
        }
    }

    @Override
    protected void erase(int index) {
        Unsafe.arrayPut(keys, index, noEntryKey);
        Unsafe.arrayPut(values, index, null);
    }

    public void removeAt(int index) {
        if (index < 0) {
            int index1 = -index - 1;
            CharSequence key = keys[index1];
            super.removeAt(index);
            list.remove(key);
        }
    }

    public V get(CharSequence key) {
        return valueAt(keyIndex(key));
    }

    public ObjList<CharSequence> keys() {
        return list;
    }

    public boolean put(CharSequence key, V value) {
        return putAt(keyIndex(key), key, value);
    }

    @Override
    protected void move(int from, int to) {
        Unsafe.arrayPut(keys, to, keys[from]);
        Unsafe.arrayPut(values, to, values[from]);
        erase(from);
    }

    public boolean putAt(int index, CharSequence key, V value) {
        if (putAt0(index, key, value)) {
            list.add(key);
            return true;
        }
        return false;
    }

    public void sortKeys(Comparator<CharSequence> comparator) {
        list.sort(comparator);
    }

    public V valueAt(int index) {
        int index1 = -index - 1;
        return index < 0 ? values[index1] : null;
    }

    public V valueQuick(int index) {
        return get(list.getQuick(index));
    }

    private boolean putAt0(int index, CharSequence key, V value) {
        if (index < 0) {
            Unsafe.arrayPut(values, -index - 1, value);
            return false;
        } else {
            Unsafe.arrayPut(keys, index, key);
            Unsafe.arrayPut(values, index, value);
            if (--free == 0) {
                rehash();
            }
            return true;
        }
    }

    @SuppressWarnings({"unchecked"})
    private void rehash() {
        int size = size();
        int newCapacity = capacity * 2;
        mask = newCapacity - 1;
        free = capacity = newCapacity;
        int arrayCapacity = (int) (newCapacity / loadFactor);

        V[] oldValues = values;
        CharSequence[] oldKeys = keys;
        this.keys = new CharSequence[arrayCapacity];
        this.values = (V[]) new Object[arrayCapacity];
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