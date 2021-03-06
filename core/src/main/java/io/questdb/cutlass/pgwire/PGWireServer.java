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

package io.questdb.cutlass.pgwire;

import io.questdb.cairo.CairoEngine;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import io.questdb.mp.EagerThreadSetup;
import io.questdb.mp.Job;
import io.questdb.mp.WorkerPool;
import io.questdb.mp.WorkerPoolConfiguration;
import io.questdb.network.*;
import io.questdb.std.Misc;
import io.questdb.std.ThreadLocal;
import io.questdb.std.WeakObjectPool;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;

public class PGWireServer implements Closeable {
    private static final Log LOG = LogFactory.getLog(PGWireServer.class);
    private final IODispatcher<PGConnectionContext> dispatcher;
    private final PGConnectionContextFactory contextFactory;

    public PGWireServer(
            PGWireConfiguration configuration,
            CairoEngine engine,
            WorkerPool pool
    ) {
        this.contextFactory = new PGConnectionContextFactory(configuration);
        this.dispatcher = IODispatchers.create(
                configuration.getDispatcherConfiguration(),
                contextFactory
        );

        pool.assign(dispatcher);

        for (int i = 0, n = pool.getWorkerCount(); i < n; i++) {
            final PGJobContext jobContext = new PGJobContext(configuration, engine);
            pool.assign(i, new Job() {
                private final IORequestProcessor<PGConnectionContext> processor = (operation, context, dispatcher) -> {
                    try {
                        jobContext.handleClientOperation(context);
                        dispatcher.registerChannel(context, IOOperation.READ);
                    } catch (PeerIsSlowToWriteException e) {
                        dispatcher.registerChannel(context, IOOperation.READ);
                    } catch (PeerIsSlowToReadException e) {
                        dispatcher.registerChannel(context, IOOperation.WRITE);
                    } catch (PeerDisconnectedException | BadProtocolException e) {
                        dispatcher.disconnect(context);
                    }
                };

                @Override
                public boolean run() {
                    return dispatcher.processIOQueue(processor);
                }
            });

            // http context factory has thread local pools
            // therefore we need each thread to clean their thread locals individually
            pool.assign(i, () -> {
                Misc.free(jobContext);
                contextFactory.closeContextPool();
            });
        }
    }

    @Nullable
    public static PGWireServer create(PGWireConfiguration configuration, WorkerPool workerPool, Log log, CairoEngine cairoEngine) {
        PGWireServer pgWireServer;
        if (configuration.isEnabled()) {
            final WorkerPool localPool;
            if (configuration.getWorkerCount() > 0) {
                localPool = new WorkerPool(new WorkerPoolConfiguration() {
                    @Override
                    public int[] getWorkerAffinity() {
                        return configuration.getWorkerAffinity();
                    }

                    @Override
                    public int getWorkerCount() {
                        return configuration.getWorkerCount();
                    }

                    @Override
                    public boolean haltOnError() {
                        return configuration.workerHaltOnError();
                    }
                });
            } else {
                localPool = workerPool;
            }

            pgWireServer = new PGWireServer(configuration, cairoEngine, localPool);

            if (localPool != workerPool) {
                localPool.start(log);
            }
        } else {
            pgWireServer = null;
        }
        return pgWireServer;
    }

    @Override
    public void close() {
        Misc.free(contextFactory);
        Misc.free(dispatcher);
    }

    private static class PGConnectionContextFactory implements IOContextFactory<PGConnectionContext>, Closeable, EagerThreadSetup {
        private final ThreadLocal<WeakObjectPool<PGConnectionContext>> contextPool;
        private boolean closed = false;

        public PGConnectionContextFactory(PGWireConfiguration configuration) {
            this.contextPool = new ThreadLocal<>(() -> new WeakObjectPool<>(() ->
                    new PGConnectionContext(configuration), configuration.getConnectionPoolInitialCapacity()));
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public PGConnectionContext newInstance(long fd) {
            return contextPool.get().pop().of(fd);
        }

        @Override
        public void done(PGConnectionContext context) {
            if (closed) {
                Misc.free(context);
            } else {
                context.of(-1);
                contextPool.get().push(context);
                LOG.info().$("pushed").$();
            }
        }

        @Override
        public void setup() {
            contextPool.get();
        }

        private void closeContextPool() {
            Misc.free(this.contextPool.get());
            LOG.info().$("closed").$();
        }
    }
}
