/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.transaction.log.files;

import java.io.IOException;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.impl.transaction.log.LogTailMetadata;
import org.neo4j.kernel.impl.transaction.log.LogTailMetadataFactory;
import org.neo4j.storageengine.api.StorageEngineFactory;

public class LogTailMetadataFactoryImpl implements LogTailMetadataFactory {
    private final FileSystemAbstraction fileSystem;

    public LogTailMetadataFactoryImpl(FileSystemAbstraction fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public LogTailMetadata getLogTailMetadata(DatabaseLayout databaseLayout, StorageEngineFactory storageEngineFactory)
            throws IOException {
        return LogFilesBuilder.logFilesBasedOnlyBuilder(databaseLayout.getTransactionLogsDirectory(), fileSystem)
                .withStorageEngineFactory(storageEngineFactory)
                .build()
                .getTailMetadata();
    }
}