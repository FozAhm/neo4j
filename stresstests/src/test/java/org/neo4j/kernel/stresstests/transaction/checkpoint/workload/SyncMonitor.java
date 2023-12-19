/*
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * Neo4j Sweden Software License, as found in the associated LICENSE.txt
 * file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Neo4j Sweden Software License for more details.
 */
package org.neo4j.kernel.stresstests.transaction.checkpoint.workload;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class SyncMonitor implements Worker.Monitor
{
    private final AtomicBoolean stopSignal = new AtomicBoolean();
    private final AtomicLong transactionCounter = new AtomicLong();
    private final CountDownLatch stopLatch;

    SyncMonitor( int threads )
    {
        this.stopLatch = new CountDownLatch( threads );
    }

    @Override
    public void transactionCompleted()
    {
        transactionCounter.incrementAndGet();
    }

    @Override
    public boolean stop()
    {
        return stopSignal.get();
    }

    @Override
    public void done()
    {
        stopLatch.countDown();
    }

    public long transactions()
    {
        return transactionCounter.get();
    }

    public void stopAndWaitWorkers() throws InterruptedException
    {
        stopSignal.set( true );
        stopLatch.await();
    }
}