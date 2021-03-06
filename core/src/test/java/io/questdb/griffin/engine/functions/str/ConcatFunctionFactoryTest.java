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

package io.questdb.griffin.engine.functions.str;

import io.questdb.cairo.CairoTestUtils;
import io.questdb.cairo.PartitionBy;
import io.questdb.cairo.sql.RecordCursor;
import io.questdb.cairo.sql.RecordCursorFactory;
import io.questdb.griffin.AbstractGriffinTest;
import io.questdb.griffin.CompiledQuery;
import io.questdb.griffin.engine.functions.rnd.SharedRandom;
import io.questdb.std.Rnd;
import io.questdb.test.tools.TestUtils;
import org.junit.Before;
import org.junit.Test;

public class ConcatFunctionFactoryTest extends AbstractGriffinTest {

    @Before
    public void setUp3() {
        SharedRandom.RANDOM.set(new Rnd());
    }

    @Test
    public void testAll() throws Exception {
        TestUtils.assertMemoryLeak(() -> {
            CairoTestUtils.createAllTableWithNewTypes(configuration, PartitionBy.NONE);

            compiler.compile("insert into all2 select * from (" +
                    "select" +
                    " rnd_int()," +
                    " rnd_short()," +
                    " rnd_byte()," +
                    " rnd_double()," +
                    " rnd_float()," +
                    " rnd_long()," +
                    " rnd_str(2,3,0)," +
                    " rnd_symbol('A','D')," +
                    " rnd_boolean()," +
                    " rnd_bin()," +
                    " rnd_date()," +
                    " rnd_long256()," +
                    " rnd_char()," +
                    " timestamp_sequence(to_timestamp(0L), 10L) ts from long_sequence(10)) timestamp(ts)");
            CompiledQuery cq = compiler.compile("select concat(int, '/', short, '/', byte, '/', double, '/', float, '/', long, '/', str, '/', sym, '/', bool, '/', bin, '/', date, '/', long256, '/', chr, '/', timestamp) from all2 order by 1");

            try (RecordCursorFactory factory = cq.getRecordCursorFactory()) {
                sink.clear();
                try (RecordCursor cursor = factory.getCursor()) {
                    printer.print(cursor, factory.getMetadata(), true);
                }
            }

            TestUtils.assertEquals("concat\n" +
                            "-10505757/-15119/119/0.22822/0.174/3518554007419864093/WE/D/true/[]/4676168/0x1872e79ea10322460cb5f439cbc22e9d1f0481ab7acd1f4a77827c4f6b03027b/L/20\n" +
                            "-1148479920/24814/27/0.12967/0.285/-7611843578141082998/YR/A/false/[]/2827518/0x63eb3740c80f661e9c8afa23e6ca6ca17c1b058af93c08086bafc47f4abcd93b/D/0\n" +
                            "-1182156192/-20816/116/0.50259/0.995/2151565237758036093/NZ/A/false/[]/3201942/0x6c3493fcb2d0272d6046e5d137dd8f0f2e8575ff5c2587f584a7624f383eb28b/T/50\n" +
                            "-1251437443/-27994/33/0.81080/0.509/7036584259400395476/IUG/A/false/[]/8374432/0x1bd29676f6902e64355587e777dbb1f2413521d14f331c3713e2c5f1f106cfe2/Q/80\n" +
                            "-1271909747/4635/34/0.63816/0.402/8573481508564499209/SH/A/false/[]/3531603/0xbedf29efb28cdcb1b75dccbdf1f8b84b9b27eba5e9cfa1e29660300cea7db540/Y/10\n" +
                            "-1311366306/-32151/98/0.53570/0.654/-8757007522346766135/RIP/D/false/[]/7837161/0x3eef3f158e0843624d0fa2564c3517679a2dfd07dad695f78d5c4bed8432de98/Y/40\n" +
                            "-86791548/-32683/36/0.03951/0.720/6260580881559018466/PI/A/false/[]/132849/0xbabcd0482f05618f926cdd99e63abb35650d1fb462d014df59070392ef6aa389/W/90\n" +
                            "1570930196/-3021/88/0.76947/0.114/-4284648096271470489/RO/D/true/[]/8955510/0x7a43ccd77f510b47a21b64e62e9e70cd840f0e1e4477981455b0586d1c02dfb3/T/60\n" +
                            "1864113037/-1315/111/0.89409/0.198/-8082754367165748693/OV/A/false/[]/8611401/0x3d9491e7e14eba8e1de93a9cf1483e290ec6c3651b1c029f825c96def9f2fcc2/L/30\n" +
                            "2067844108/-6087/114/0.10228/0.089/-7724577649125721868/GMX/D/false/[]/4170699/0x6fff79101ec5c1cf61ca7a1ff52a4ccf7ab72c8ee7c4dea1c54dc9aa8e01394b/G/70\n",
                    sink);

            engine.releaseAllWriters();
            engine.releaseAllReaders();
        });
    }
}
