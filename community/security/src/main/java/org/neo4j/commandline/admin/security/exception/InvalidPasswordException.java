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
package org.neo4j.commandline.admin.security.exception;

import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorMessageHolder;
import org.neo4j.gqlstatus.HasGqlStatusInfo;
import org.neo4j.kernel.api.exceptions.Status;

public class InvalidPasswordException extends RuntimeException implements Status.HasStatus, HasGqlStatusInfo {
    private final Status status;
    private final ErrorGqlStatusObject gqlStatusObject;
    private final String oldMessage;

    public InvalidPasswordException(String message) {
        super(message, null, false, false);
        this.status = Status.Statement.ArgumentError;

        this.gqlStatusObject = null;
        this.oldMessage = message;
    }

    public InvalidPasswordException(ErrorGqlStatusObject gqlStatusObject, String message) {
        super(ErrorMessageHolder.getMessage(gqlStatusObject, message), null, false, false);
        this.gqlStatusObject = gqlStatusObject;

        this.status = Status.Statement.ArgumentError;
        this.oldMessage = message;
    }

    @Override
    public String getOldMessage() {
        return oldMessage;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public ErrorGqlStatusObject gqlStatusObject() {
        return gqlStatusObject;
    }
}
