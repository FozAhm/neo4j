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
package org.neo4j.ha;

import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.ha.HighlyAvailableGraphDatabase;
import org.neo4j.test.OtherThreadExecutor.WorkerCommand;

public class FinishTx implements WorkerCommand<HighlyAvailableGraphDatabase, Void>
{
    private final Transaction tx;
    private final boolean successful;

    public FinishTx( Transaction tx, boolean successful )
    {
        this.tx = tx;
        this.successful = successful;
    }

    @Override
    public Void doWork( HighlyAvailableGraphDatabase state )
    {
        if ( successful )
        {
            tx.success();
        }
        tx.close();
        return null;
    }
}