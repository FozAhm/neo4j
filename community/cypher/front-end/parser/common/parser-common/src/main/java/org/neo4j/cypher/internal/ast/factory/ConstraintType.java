/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.ast.factory;

public enum ConstraintType {
    NODE_UNIQUE("IS NODE UNIQUE"),
    REL_UNIQUE("IS RELATIONSHIP UNIQUE"),
    NODE_KEY("IS NODE KEY"),
    REL_KEY("IS RELATIONSHIP KEY"),
    NODE_EXISTS("EXISTS"),
    NODE_IS_NOT_NULL("IS NOT NULL"),
    REL_EXISTS("EXISTS"),
    REL_IS_NOT_NULL("IS NOT NULL"),
    NODE_IS_TYPED("IS TYPED"),
    REL_IS_TYPED("IS TYPED");

    private final String description;

    ConstraintType(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}