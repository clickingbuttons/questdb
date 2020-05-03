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

package io.questdb.griffin.engine.functions.rnd;

import io.questdb.cairo.CairoConfiguration;
import io.questdb.cairo.sql.Function;
import io.questdb.cairo.sql.Record;
import io.questdb.cairo.sql.SymbolTableSource;
import io.questdb.griffin.FunctionFactory;
import io.questdb.griffin.SqlException;
import io.questdb.griffin.SqlExecutionContext;
import io.questdb.griffin.engine.functions.ByteFunction;
import io.questdb.griffin.engine.functions.StatelessFunction;
import io.questdb.std.ObjList;
import io.questdb.std.Rnd;

public class RndByteCCFunctionFactory extends FunctionFactory {

    @Override
    public String getSignature() {
        return "rnd_byte(ii)";
    }

    @Override
    public Function newInstance(ObjList<Function> args, int position, CairoConfiguration configuration) throws SqlException {

        byte lo = (byte) args.getQuick(0).getInt(null);
        byte hi = (byte) args.getQuick(1).getInt(null);

        if (lo < hi) {
            return new RndFunction(position, lo, hi);
        }

        throw SqlException.position(position).put("invalid range");
    }

    private static class RndFunction extends ByteFunction implements StatelessFunction {
        private final byte lo;
        private final byte range;
        private Rnd rnd;

        public RndFunction(int position, byte lo, byte hi) {
            super(position);
            this.lo = lo;
            this.range = (byte) (hi - lo + 1);
        }

        @Override
        public byte getByte(Record rec) {
            short s = rnd.nextShort();
            if (s < 0) {
                return (byte) (lo - s % range);
            }
            return (byte) (lo + s % range);
        }

        @Override
        public void init(SymbolTableSource symbolTableSource, SqlExecutionContext executionContext) {
            this.rnd = executionContext.getRandom();
        }
    }
}
