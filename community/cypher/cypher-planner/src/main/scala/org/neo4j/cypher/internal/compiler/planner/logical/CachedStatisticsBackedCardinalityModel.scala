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
package org.neo4j.cypher.internal.compiler.planner.logical

import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.compiler.helpers.CachedFunction
import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.CardinalityModel
import org.neo4j.cypher.internal.compiler.planner.logical.Metrics.QueryGraphSolverInput
import org.neo4j.cypher.internal.ir.PlannerQueryPart
import org.neo4j.cypher.internal.ir.SinglePlannerQuery
import org.neo4j.cypher.internal.ir.UnionQuery
import org.neo4j.cypher.internal.util.Cardinality

class CachedStatisticsBackedCardinalityModel(wrapped: StatisticsBackedCardinalityModel) extends CardinalityModel {

  private val singleCached = CachedFunction[SinglePlannerQuery, Metrics.QueryGraphSolverInput, SemanticTable, Cardinality] { (a, b, c) => wrapped.singlePlannerQueryCardinality(a, b, c) }
  private val unionCached = CachedFunction[UnionQuery, Cardinality, Cardinality, Cardinality] { (a, b, c) => wrapped.combineUnion(a, b, c) }
  private val cached = CachedFunction[PlannerQueryPart, Metrics.QueryGraphSolverInput, SemanticTable, Cardinality] { (a, b, c) => cachedCardinality(a, b, c) }

  private def cachedCardinality(queryPart: PlannerQueryPart, input: QueryGraphSolverInput, semanticTable: SemanticTable): Cardinality = queryPart match {
    case singlePlannerQuery: SinglePlannerQuery => singleCached(singlePlannerQuery, input, semanticTable)
    case uq@UnionQuery(part, query, _, _) => unionCached(uq, apply(part, input, semanticTable), apply(query, input, semanticTable))
  }

  override def apply(queryPart: PlannerQueryPart, input: QueryGraphSolverInput, semanticTable: SemanticTable): Cardinality = cached(queryPart, input, semanticTable)
}


