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

package com.nfsdb.net.ha;

import com.nfsdb.net.ha.bridge.JournalEventBridge;
import com.nfsdb.net.ha.bridge.JournalEventHandler;
import com.nfsdb.net.ha.bridge.JournalEventProcessor;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

public class JournalEventBridgeTest {
    @Test
    public void testStartStop() throws Exception {
        JournalEventBridge bridge = new JournalEventBridge(2, TimeUnit.SECONDS);
        for (int i = 0; i < 10000; i++) {
            bridge.publish(10, System.currentTimeMillis());
        }
    }

    @Test
    public void testTwoPublishersThreeConsumers() throws Exception {
        ExecutorService service = Executors.newCachedThreadPool();
        final JournalEventBridge bridge = new JournalEventBridge(50, TimeUnit.MILLISECONDS);
        final Future[] publishers = new Future[2];
        final Handler[] consumers = new Handler[3];
        final int batchSize = 1000;

        final CyclicBarrier barrier = new CyclicBarrier(publishers.length + consumers.length);
        final CountDownLatch latch = new CountDownLatch(publishers.length + consumers.length);

        for (int i = 0; i < publishers.length; i++) {
            final int index = i;
            publishers[i] = service.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    int count = 0;
                    try {
                        barrier.await();
                        for (int k = 0; k < batchSize; k++) {
                            long ts = System.nanoTime();
                            bridge.publish(index, ts);
                            count++;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }

                    return count;
                }
            });
        }


        for (int i = 0; i < consumers.length; i++) {
            final JournalEventProcessor processor = new JournalEventProcessor(bridge);
            final Handler handler = new Handler(i);
            consumers[i] = handler;
            service.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        barrier.await();
                        while (true) {
                            if (!processor.process(handler, true)) {
                                break;
                            }
                        }
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

//        service.submit(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    barrier.await();
//                    for (int i = 0; i < 1000; i++) {
//                        Sequence sequence = bridge.createAgentSequence();
//                        LockSupport.parkNanos(TimeUnit.MICROSECONDS.toNanos(10));
//                        bridge.removeAgentSequence(sequence);
//                    }
//                } catch (InterruptedException | BrokenBarrierException e) {
//                    e.printStackTrace();
//                } finally {
//                    latch.countDown();
//                }
//            }
//        });

        latch.await();

        for (Future f : publishers) {
            Assert.assertEquals(batchSize, f.get());
        }

        Assert.assertEquals(batchSize, consumers[0].getCounter());
        Assert.assertEquals(batchSize, consumers[1].getCounter());
        Assert.assertEquals(0, consumers[2].getCounter());
    }

    private class Handler implements JournalEventHandler {
        private final int index;
        private int counter;

        private Handler(int index) {
            this.index = index;
        }

        public int getCounter() {
            return counter;
        }

        @Override
        public void handle(int journalIndex) {
            if (journalIndex == index) {
                counter++;
            }
        }
    }
}