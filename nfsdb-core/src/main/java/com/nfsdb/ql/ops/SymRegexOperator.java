/*
 * Copyright (c) 2014. Vlad Ilyushchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nfsdb.ql.ops;

import com.nfsdb.collections.IntHashSet;
import com.nfsdb.ql.Record;
import com.nfsdb.ql.SymFacade;
import com.nfsdb.storage.ColumnType;
import com.nfsdb.storage.SymbolTable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SymRegexOperator extends AbstractBinaryOperator {

    private final IntHashSet set = new IntHashSet();
    private Matcher matcher;

    public SymRegexOperator() {
        super(ColumnType.BOOLEAN);
    }

    @Override
    public boolean getBool(Record rec) {
        return set.contains(lhs.getInt(rec));
    }

    @Override
    public void prepare(SymFacade facade) {
        super.prepare(facade);
        set.clear();
        SymbolTable tab = lhs.getSymbolTable();
        for (SymbolTable.Entry e : tab.values()) {
            if (matcher.reset(e.value).find()) {
                set.add(e.key);
            }
        }
    }

    @Override
    public void setRhs(VirtualColumn rhs) {
        super.setRhs(rhs);
        matcher = Pattern.compile(rhs.getStr(null).toString()).matcher("");
    }
}