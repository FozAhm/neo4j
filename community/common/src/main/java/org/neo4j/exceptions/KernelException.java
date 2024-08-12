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
package org.neo4j.exceptions;

import org.neo4j.common.TokenNameLookup;
import org.neo4j.gqlstatus.ErrorGqlStatusObject;
import org.neo4j.gqlstatus.ErrorMessageHolder;
import org.neo4j.gqlstatus.HasGqlStatusInfo;
import org.neo4j.kernel.api.exceptions.Status;

public abstract class KernelException extends Exception implements Status.HasStatus, HasGqlStatusInfo {
    private final Status statusCode;
    private final ErrorGqlStatusObject gqlStatusObject;
    private final String oldMessage;

    protected KernelException(Status statusCode, Throwable cause, String message, Object... parameters) {
        super(toMessage(message, parameters), cause);
        this.statusCode = statusCode;

        this.gqlStatusObject = null;
        this.oldMessage = toMessage(message, parameters);
    }

    protected KernelException(
            ErrorGqlStatusObject gqlStatusObject,
            Status statusCode,
            Throwable cause,
            String message,
            Object... parameters) {
        super(ErrorMessageHolder.getMessage(gqlStatusObject, toMessage(message, parameters)), cause);
        this.gqlStatusObject = gqlStatusObject;

        this.statusCode = statusCode;
        this.oldMessage = toMessage(message, parameters);
    }

    protected KernelException(Status statusCode, Throwable cause) {
        super(cause);
        this.statusCode = statusCode;

        this.gqlStatusObject = null;
        this.oldMessage = HasGqlStatusInfo.getOldCauseMessage(cause);
    }

    protected KernelException(ErrorGqlStatusObject gqlStatusObject, Status statusCode, Throwable cause) {
        super(ErrorMessageHolder.getMessage(gqlStatusObject, HasGqlStatusInfo.getOldCauseMessage(cause)), cause);
        this.gqlStatusObject = gqlStatusObject;

        this.statusCode = statusCode;
        this.oldMessage = HasGqlStatusInfo.getOldCauseMessage(cause);
    }

    protected KernelException(Status statusCode, String message, Object... parameters) {
        super(toMessage(message, parameters));
        this.statusCode = statusCode;

        this.gqlStatusObject = null;
        this.oldMessage = toMessage(message, parameters);
    }

    protected KernelException(
            ErrorGqlStatusObject gqlStatusObject, Status statusCode, String message, Object... parameters) {
        super(ErrorMessageHolder.getMessage(gqlStatusObject, toMessage(message, parameters)));
        this.gqlStatusObject = gqlStatusObject;

        this.statusCode = statusCode;
        this.oldMessage = toMessage(message, parameters);
    }

    @Override
    public String getOldMessage() {
        return oldMessage;
    }

    /** The Neo4j status code associated with this exception type. */
    @Override
    public Status status() {
        return statusCode;
    }

    public String getUserMessage(TokenNameLookup tokenNameLookup) {
        return getMessage();
    }

    private static String toMessage(String message, Object... parameters) {
        // need to check for params as some messages (when no params are provided) could have a '%' within
        // and that makes String.format most unhappy and we get exceptions thrown in exception code
        return (parameters.length > 0) ? String.format(message, parameters) : message;
    }

    @Override
    public ErrorGqlStatusObject gqlStatusObject() {
        return gqlStatusObject;
    }
}
