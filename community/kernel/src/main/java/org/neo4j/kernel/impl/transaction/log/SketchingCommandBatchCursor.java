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
package org.neo4j.kernel.impl.transaction.log;

import static org.neo4j.kernel.impl.transaction.log.entry.LogEntryTypeCodes.CHUNK_END;
import static org.neo4j.kernel.impl.transaction.log.entry.LogEntryTypeCodes.TX_COMMIT;

import java.io.IOException;
import org.neo4j.kernel.impl.transaction.CommittedCommandBatch;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntry;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryReader;
import org.neo4j.kernel.impl.transaction.log.entry.LogEntryStart;
import org.neo4j.kernel.impl.transaction.log.entry.v54.LogEntryChunkStart;

public class SketchingCommandBatchCursor implements CommandBatchCursor {
    private final ReadableClosablePositionAwareChecksumChannel channel;
    private final LogEntryCursor logEntryCursor;
    private final LogPositionMarker lastGoodPositionMarker = new LogPositionMarker();

    public SketchingCommandBatchCursor(ReadableClosablePositionAwareChecksumChannel channel, LogEntryReader entryReader)
            throws IOException {
        this.channel = channel;
        channel.getCurrentPosition(lastGoodPositionMarker);
        this.logEntryCursor = new LogEntryCursor(entryReader, channel);
    }

    @Override
    public CommittedCommandBatch get() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean next() throws IOException {
        while (hasEntries()) {
            LogEntry entry = logEntryCursor.get();
            assert entry instanceof LogEntryStart || entry instanceof LogEntryChunkStart
                    : "Expected Start entry, read " + entry + " instead";

            // Read till commit entry
            while (hasEntries()) {
                entry = logEntryCursor.get();

                if (isBatchEnd(entry)) {
                    channel.getCurrentPosition(lastGoodPositionMarker);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasEntries() throws IOException {
        return logEntryCursor.next();
    }

    private boolean isBatchEnd(LogEntry entry) {
        return isChunkEnd(entry) || isCommit(entry);
    }

    private static boolean isCommit(LogEntry entry) {
        return entry.getType() == TX_COMMIT;
    }

    private static boolean isChunkEnd(LogEntry entry) {
        return entry.getType() == CHUNK_END;
    }

    @Override
    public void close() throws IOException {
        logEntryCursor.close();
    }

    @Override
    public LogPosition position() {
        return lastGoodPositionMarker.newPosition();
    }
}