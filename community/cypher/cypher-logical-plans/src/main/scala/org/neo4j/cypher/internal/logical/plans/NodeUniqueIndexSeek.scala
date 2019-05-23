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
package org.neo4j.cypher.internal.logical.plans

import org.neo4j.cypher.internal.v4_0.expressions.{Expression, LabelToken}
import org.neo4j.cypher.internal.v4_0.util.attribution.{IdGen, SameId}

/**
  * Produces one or zero rows containing the node with the given label and property values.
  *
  * This operator is used on label/property combinations under uniqueness constraint, meaning that a single matching
  * node is guaranteed.
  */
case class NodeUniqueIndexSeek(idName: String,
                               label: LabelToken,
                               properties: Seq[IndexedProperty],
                               valueExpr: QueryExpression[Expression],
                               argumentIds: Set[String],
                               indexOrder: IndexOrder)
                              (implicit idGen: IdGen) extends IndexSeekLeafPlan(idGen) {

  override val availableSymbols: Set[String] = argumentIds + idName

  override def copyWithoutGettingValues: NodeUniqueIndexSeek =
    NodeUniqueIndexSeek(idName, label, properties.map{ p => IndexedProperty(p.propertyKeyToken, DoNotGetValue) }, valueExpr, argumentIds, indexOrder)(SameId(this.id))

  override def withProperties(properties: Seq[IndexedProperty]): IndexLeafPlan =
    NodeUniqueIndexSeek(idName, label, properties, valueExpr, argumentIds, indexOrder)(SameId(this.id))
}
