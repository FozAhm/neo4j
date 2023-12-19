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
package org.neo4j.cluster.protocol.snapshot;

/**
 * API for getting a snapshot of current state from an instance in a cluster.
 * Mainly used when a new instance joins, and wants to catch up without having
 * to re-read all previous messages.
 */
public interface Snapshot
{
    void setSnapshotProvider( SnapshotProvider snapshotProvider );

    void refreshSnapshot();
}