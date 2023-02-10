/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.testing.fsm;

import org.neo4j.bolt.dbapi.BoltGraphDatabaseManagementServiceSPI;
import org.neo4j.bolt.negotiation.ProtocolVersion;
import org.neo4j.bolt.protocol.common.BoltProtocol;
import org.neo4j.bolt.protocol.v51.BoltProtocolV51;
import org.neo4j.bolt.testing.messages.BoltMessages;
import org.neo4j.bolt.testing.messages.BoltV51Messages;
import org.neo4j.logging.internal.LogService;
import org.neo4j.time.SystemNanoClock;

public class StateMachineV51Provider implements StateMachineProvider {
    private static final StateMachineProvider INSTANCE = new StateMachineV51Provider();

    private StateMachineV51Provider() {}

    public static StateMachineProvider getInstance() {
        return INSTANCE;
    }

    @Override
    public ProtocolVersion version() {
        return BoltProtocolV51.VERSION;
    }

    @Override
    public BoltMessages messages() {
        return BoltV51Messages.getInstance();
    }

    @Override
    public BoltProtocol protocol(
            LogService logging,
            BoltGraphDatabaseManagementServiceSPI graphDatabaseManagementServiceSPI,
            SystemNanoClock clock) {
        return new BoltProtocolV51(logging, graphDatabaseManagementServiceSPI, clock);
    }
}
