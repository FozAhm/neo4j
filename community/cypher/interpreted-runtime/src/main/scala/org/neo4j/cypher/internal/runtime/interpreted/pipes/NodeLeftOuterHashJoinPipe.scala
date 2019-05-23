/*
 * Copyright (c) 2002-2019 "Neo4j,"
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
package org.neo4j.cypher.internal.runtime.interpreted.pipes

import org.neo4j.cypher.internal.runtime.ExecutionContext
import org.neo4j.cypher.internal.logical.plans.CachedProperty
import org.neo4j.cypher.internal.v4_0.util.attribution.Id

import scala.collection.mutable

case class NodeLeftOuterHashJoinPipe(nodeVariables: Set[String],
                                     lhs: Pipe,
                                     rhs: Pipe,
                                     nullableVariables: Set[String],
                                     nullableCachedProperties: Set[CachedProperty])
                                    (val id: Id = Id.INVALID_ID)
  extends NodeOuterHashJoinPipe(nodeVariables, lhs, rhs, nullableVariables, nullableCachedProperties) {

  protected def internalCreateResults(input: Iterator[ExecutionContext], state: QueryState): Iterator[ExecutionContext] = {

    if (input.isEmpty)
      return Iterator.empty

    val probeTable = buildProbeTableAndFindNullRows(input, withNulls = true)

    val rhsKeys = mutable.Set[IndexedSeq[Long]]()
    val lhsKeys = probeTable.keySet
    val joinedRows = (
      for {rhsRow <- rhs.createResults(state)
           joinKey <- computeKey(rhsRow)}
        yield {
          val lhsRows = probeTable(joinKey)
          rhsKeys.add(joinKey)
          lhsRows.map { lhsRow =>
            val outputRow = executionContextFactory.copyWith(lhsRow)
            outputRow.mergeWith(rhsRow, state.query)
            outputRow
          }
        }).flatten

    def rowsWithoutRhsMatch: Iterator[ExecutionContext] = {
      (lhsKeys -- rhsKeys).iterator.flatMap {
        x => probeTable(x).map(addNulls)
      }
    }

    val rowsWithNullAsJoinKey = probeTable.nullRows.map(addNulls)

    joinedRows ++ rowsWithNullAsJoinKey ++ rowsWithoutRhsMatch
  }
}
