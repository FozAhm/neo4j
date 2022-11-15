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
package org.neo4j.cypher.internal.compiler.ast.convert.plannerQuery

import org.neo4j.cypher.internal.ast.AstConstructionTestSupport
import org.neo4j.cypher.internal.ast.FullExistsExpression
import org.neo4j.cypher.internal.ast.Query
import org.neo4j.cypher.internal.ast.SimpleExistsExpression
import org.neo4j.cypher.internal.ast.Union.UnionMapping
import org.neo4j.cypher.internal.ast.semantics.SemanticTable
import org.neo4j.cypher.internal.expressions
import org.neo4j.cypher.internal.expressions.AssertIsNode
import org.neo4j.cypher.internal.expressions.CountExpression
import org.neo4j.cypher.internal.expressions.CountStar
import org.neo4j.cypher.internal.expressions.EveryPath
import org.neo4j.cypher.internal.expressions.Expression
import org.neo4j.cypher.internal.expressions.LabelExpression.Negation
import org.neo4j.cypher.internal.expressions.LabelExpression.Wildcard
import org.neo4j.cypher.internal.expressions.Pattern
import org.neo4j.cypher.internal.expressions.PatternComprehension
import org.neo4j.cypher.internal.expressions.PatternExpression
import org.neo4j.cypher.internal.expressions.RelationshipsPattern
import org.neo4j.cypher.internal.expressions.SemanticDirection.BOTH
import org.neo4j.cypher.internal.expressions.SemanticDirection.INCOMING
import org.neo4j.cypher.internal.expressions.SemanticDirection.OUTGOING
import org.neo4j.cypher.internal.expressions.Unique
import org.neo4j.cypher.internal.expressions.Variable
import org.neo4j.cypher.internal.expressions.functions.Exists
import org.neo4j.cypher.internal.frontend.phases.Namespacer
import org.neo4j.cypher.internal.frontend.phases.rewriting.cnf.flattenBooleanOperators
import org.neo4j.cypher.internal.ir.AggregatingQueryProjection
import org.neo4j.cypher.internal.ir.PatternRelationship
import org.neo4j.cypher.internal.ir.PlannerQuery
import org.neo4j.cypher.internal.ir.QueryGraph
import org.neo4j.cypher.internal.ir.QueryHorizon
import org.neo4j.cypher.internal.ir.RegularQueryProjection
import org.neo4j.cypher.internal.ir.RegularSinglePlannerQuery
import org.neo4j.cypher.internal.ir.Selections
import org.neo4j.cypher.internal.ir.SimplePatternLength
import org.neo4j.cypher.internal.ir.UnionQuery
import org.neo4j.cypher.internal.ir.VarPatternLength
import org.neo4j.cypher.internal.ir.ast.CountIRExpression
import org.neo4j.cypher.internal.ir.ast.ExistsIRExpression
import org.neo4j.cypher.internal.ir.ast.ListIRExpression
import org.neo4j.cypher.internal.rewriting.rewriters.AddUniquenessPredicates
import org.neo4j.cypher.internal.rewriting.rewriters.inlineNamedPathsInPatternComprehensions
import org.neo4j.cypher.internal.util.AnonymousVariableNameGenerator
import org.neo4j.cypher.internal.util.InputPosition
import org.neo4j.cypher.internal.util.inSequence
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class CreateIrExpressionsTest extends CypherFunSuite with AstConstructionTestSupport {

  private val n = varFor("n")
  private val m = varFor("m")
  private val o = varFor("o")
  private val q = varFor("q")
  private val r = varFor("r")
  private val r2 = varFor("r2")
  private val r3 = varFor("r3")
  private val path = varFor("p")

  private val rPred = greaterThan(prop(r.name, "foo"), literalInt(5))
  private val rLessPred = lessThan(prop(r.name, "foo"), literalInt(10))
  private val oPred = greaterThan(prop(o.name, "foo"), literalInt(5))

  private val n_r_m_chain = relationshipChain(
    nodePat(Some("n")),
    relPat(Some("r"), direction = BOTH),
    nodePat(Some("m"))
  )

  private val n_r_m = RelationshipsPattern(n_r_m_chain)(pos)

  private val n_r_m_withLabelDisjunction = RelationshipsPattern(relationshipChain(
    nodePat(Some("n")),
    relPat(Some("r"), direction = BOTH),
    nodePat(Some("m"), Some(labelDisjunction(labelLeaf("M"), labelLeaf("MM"))))
  ))(pos)

  private val n_r25_m = RelationshipsPattern(relationshipChain(
    nodePat(Some("n")),
    relPat(
      name = Some("r"),
      length = Some(Some(expressions.Range(Some(literalUnsignedInt(2)), Some(literalUnsignedInt(5)))(pos))),
      direction = BOTH
    ),
    nodePat(Some("m"))
  ))(pos)

  private val n_r_m_withPreds = RelationshipsPattern(relationshipChain(
    nodePat(Some("n")),
    relPat(
      name = Some("r"),
      labelExpression = Some(labelDisjunction(labelRelTypeLeaf("R"), labelRelTypeLeaf("P"))),
      properties = Some(mapOfInt("prop" -> 5)),
      predicates = Some(rPred)
    ),
    nodePat(Some("m")),
    relPat(Some("r2"), direction = INCOMING),
    nodePat(
      Some("o"),
      Some(Negation(Wildcard()(pos))(pos)),
      Some(mapOfInt("prop" -> 5)),
      Some(oPred)
    )
  ))(pos)

  private val o_r2_m_r3_q_chain = relationshipChain(
    nodePat(Some("o")),
    relPat(Some("r2")),
    nodePat(Some("m")),
    relPat(Some("r3")),
    nodePat(Some("q"))
  )

  // { (n)-[r]->(m), (o)-[r2]->(m)-[r3]->(q) }
  private val n_r_m_r2_o_r3_q = Pattern(Seq(
    EveryPath(n_r_m_chain),
    EveryPath(o_r2_m_r3_q_chain)
  ))(pos)

  private def makeAnonymousVariableNameGenerator(): AnonymousVariableNameGenerator = new AnonymousVariableNameGenerator

  private def rewrite(e: Expression, semanticTable: SemanticTable = new SemanticTable()): Expression = {
    val anonymousVariableNameGenerator = makeAnonymousVariableNameGenerator()
    val rewriter = inSequence(
      AddUniquenessPredicates.rewriter,
      flattenBooleanOperators,
      inlineNamedPathsInPatternComprehensions.instance,
      CreateIrExpressions(anonymousVariableNameGenerator, semanticTable)
    )
    e.endoRewrite(rewriter)
  }

  private def queryWith(qg: QueryGraph, horizon: Option[QueryHorizon]): PlannerQuery = {
    PlannerQuery(
      RegularSinglePlannerQuery(
        queryGraph = qg,
        horizon = horizon.getOrElse(RegularQueryProjection()),
        tail = None
      )
    )
  }

  test("Rewrites PatternExpression") {
    val pe = PatternExpression(n_r_m)(Set(m, r), Set(n))
    val pathExpression = PathExpressionBuilder.node(n.name).bothTo(r.name, m.name).build()

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val rewritten = rewrite(pe)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> pathExpression)))
        ),
        variableToCollectName,
        collectionName,
        s"(${n.name})-[${r.name}]-(${m.name})"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites PatternExpression with varlength relationship") {
    val pe = PatternExpression(n_r25_m)(Set(m, r), Set(n))
    val pathExpression = PathExpressionBuilder.node(n.name).bothToVarLength(r.name, m.name).build()

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val rewritten = rewrite(pe)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, VarPatternLength(2, Some(5)))),
            selections = Selections.from(Unique(varFor("r"))(pos))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> pathExpression)))
        ),
        variableToCollectName,
        collectionName,
        s"(${n.name})-[${r.name}*2..5]-(${m.name})"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites PatternExpression with longer pattern and inlined predicates") {
    val pe = PatternExpression(n_r_m_withPreds)(Set(m, r), Set(n))
    val pathExpression = PathExpressionBuilder.node(n.name).outTo(r.name, m.name).inTo(r2.name, o.name).build()

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val rewritten = rewrite(pe)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name, o.name),
            argumentIds = Set(n.name),
            patternRelationships = Set(
              PatternRelationship(
                r.name,
                (n.name, m.name),
                OUTGOING,
                Seq(relTypeName("R"), relTypeName("P")),
                SimplePatternLength
              ),
              PatternRelationship(r2.name, (m.name, o.name), INCOMING, Seq.empty, SimplePatternLength)
            ),
            selections = Selections.from(Seq(
              not(equals(r2, r)),
              andedPropertyInequalities(rPred),
              andedPropertyInequalities(oPred),
              equals(prop(r.name, "prop"), literalInt(5)),
              equals(prop(o.name, "prop"), literalInt(5)),
              not(hasALabel(o.name))
            ))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> pathExpression)))
        ),
        variableToCollectName,
        collectionName,
        s"(${n.name})-[${r.name}:R|P {prop: 5} WHERE ${r.name}.foo > 5]->(${m.name})<-[${r2.name}]-(${o.name}:!% {prop: 5} WHERE ${o.name}.foo > 5)"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites exists(PatternExpression) with Node label disjunction") {
    val pe = PatternExpression(n_r_m_withLabelDisjunction)(Set(m, r), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val existsVariableName = nameGenerator.nextName

    val rewritten = rewrite(Exists(pe)(pos))

    rewritten should equal(
      ExistsIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)),
            selections = Selections.from(ors(hasLabels(m, "M"), hasLabels(m, "MM")))
          ),
          None
        ),
        existsVariableName,
        s"exists((${n.name})-[${r.name}]-(${m.name}:M|MM))"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites PatternComprehension") {
    val pc = PatternComprehension(
      None,
      n_r_m,
      None,
      literalInt(5)
    )(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val rewritten = rewrite(pc)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> literalInt(5))))
        ),
        variableToCollectName,
        collectionName,
        s"[(${n.name})-[${r.name}]-(${m.name}) | 5]"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites PatternComprehension with named path") {
    val pc = PatternComprehension(
      Some(path),
      n_r_m,
      None,
      path
    )(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val pathExpression = PathExpressionBuilder.node(n.name).bothTo(r.name, m.name).build()

    val rewritten = rewrite(pc)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> pathExpression)))
        ),
        variableToCollectName,
        collectionName,
        s"[(${n.name})-[${r.name}]-(${m.name}) | (${n.name})-[${r.name}]-(${m.name})]"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites PatternComprehension with WHERE clause") {
    val rPred = greaterThan(prop(r.name, "foo"), literalInt(5))

    val pc = PatternComprehension(
      None,
      n_r_m,
      Some(rPred),
      prop(m, "foo")
    )(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val rewritten = rewrite(pc)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)),
            selections = Selections.from(andedPropertyInequalities(rPred))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> prop(m, "foo"))))
        ),
        variableToCollectName,
        collectionName,
        s"[(${n.name})-[${r.name}]-(${m.name}) WHERE ${r.name}.foo > 5 | ${m.name}.foo]"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites PatternComprehension with longer pattern and inlined predicates") {
    val rPred = greaterThan(prop(r.name, "foo"), literalInt(5))
    val oPred = greaterThan(prop(o.name, "foo"), literalInt(5))

    val pc = PatternComprehension(
      None,
      n_r_m_withPreds,
      None,
      literalInt(5)
    )(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val variableToCollectName = nameGenerator.nextName
    val collectionName = nameGenerator.nextName

    val rewritten = rewrite(pc)

    rewritten should equal(
      ListIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name, o.name),
            argumentIds = Set(n.name),
            patternRelationships = Set(
              PatternRelationship(
                r.name,
                (n.name, m.name),
                OUTGOING,
                Seq(relTypeName("R"), relTypeName("P")),
                SimplePatternLength
              ),
              PatternRelationship(r2.name, (m.name, o.name), INCOMING, Seq.empty, SimplePatternLength)
            ),
            selections = Selections.from(Seq(
              not(equals(r2, r)),
              andedPropertyInequalities(rPred),
              andedPropertyInequalities(oPred),
              equals(prop(r.name, "prop"), literalInt(5)),
              equals(prop(o.name, "prop"), literalInt(5)),
              not(hasALabel(o.name))
            ))
          ),
          Some(RegularQueryProjection(Map(variableToCollectName -> literalInt(5))))
        ),
        variableToCollectName,
        collectionName,
        s"[(${n.name})-[${r.name}:R|P {prop: 5} WHERE ${r.name}.foo > 5]->(${m.name})<-[${r2.name}]-(${o.name}:!% {prop: 5} WHERE ${o.name}.foo > 5) | 5]"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites SimpleExistsExpression") {
    val esc = SimpleExistsExpression(n_r_m_r2_o_r3_q, None)(pos, Set(n, m, o, q, r, r2, r3), Set.empty)

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val existsVariableName = nameGenerator.nextName

    val rewritten = rewrite(esc)

    rewritten should equal(
      ExistsIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name, o.name, q.name),
            patternRelationships =
              Set(
                PatternRelationship(varFor("r").name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength),
                PatternRelationship(varFor("r2").name, (o.name, m.name), OUTGOING, Seq.empty, SimplePatternLength),
                PatternRelationship(varFor("r3").name, (m.name, q.name), OUTGOING, Seq.empty, SimplePatternLength)
              ),
            selections = Selections.from(Seq(
              not(equals(r, r3)),
              not(equals(r, r2)),
              not(equals(r3, r2))
            ))
          ),
          None
        ),
        existsVariableName,
        "EXISTS { MATCH (n)-[r]-(m), (o)-[r2]->(m)-[r3]->(q) }"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites SimpleExistsExpression with where clause") {
    val esc = SimpleExistsExpression(n_r_m_r2_o_r3_q, Some(where(rPred)))(pos, Set(n, r, m, r2, o, r3, q), Set.empty)

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val existsVariableName = nameGenerator.nextName

    val rewritten = rewrite(esc)

    rewritten should equal(
      ExistsIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name, o.name, q.name),
            patternRelationships =
              Set(
                PatternRelationship(varFor("r").name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength),
                PatternRelationship(varFor("r2").name, (o.name, m.name), OUTGOING, Seq.empty, SimplePatternLength),
                PatternRelationship(varFor("r3").name, (m.name, q.name), OUTGOING, Seq.empty, SimplePatternLength)
              ),
            selections = Selections.from(Seq(
              not(equals(r, r3)),
              not(equals(r, r2)),
              not(equals(r3, r2)),
              andedPropertyInequalities(rPred)
            ))
          ),
          None
        ),
        existsVariableName,
        "EXISTS { MATCH (n)-[r]-(m), (o)-[r2]->(m)-[r3]->(q) WHERE r.foo > 5 }"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites FullExistsExpression with where clause") {
    val simpleMatchQuery = query(
      singleQuery(
        match_(Seq(n_r_m_chain, o_r2_m_r3_q_chain), None),
        return_(
          aliasedReturnItem(n)
        )
      )
    )

    val esc = FullExistsExpression(simpleMatchQuery)(pos, Set(r, r2, r3, m, o, q), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val existsVariableName = nameGenerator.nextName

    val semanticTable = new SemanticTable().addNode(varFor("n"))

    val rewritten = rewrite(esc, semanticTable)

    rewritten should equal(
      ExistsIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name, o.name, q.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(
                PatternRelationship(varFor("r").name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength),
                PatternRelationship(varFor("r2").name, (o.name, m.name), OUTGOING, Seq.empty, SimplePatternLength),
                PatternRelationship(varFor("r3").name, (m.name, q.name), OUTGOING, Seq.empty, SimplePatternLength)
              ),
            selections = Selections.from(Seq(
              not(equals(r, r3)),
              not(equals(r, r2)),
              not(equals(r3, r2))
            ))
          ),
          horizon = Some(RegularQueryProjection(Map("n" -> n)))
        ),
        existsVariableName,
        "EXISTS { MATCH (n)-[r]-(m), (o)-[r2]->(m)-[r3]->(q)\n  WHERE not r = r3 AND not r = r2 AND not r3 = r2\nRETURN n AS n }"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites FullExistsExpression") {
    val simpleMatchQuery = query(
      singleQuery(
        match_(n_r_m_chain, None),
        return_(
          aliasedReturnItem(n)
        )
      )
    )

    val esc = FullExistsExpression(simpleMatchQuery)(pos, Set.empty, Set.empty)

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val existsVariableName = nameGenerator.nextName

    val semanticTable = new SemanticTable().addNode(varFor("n"))

    val rewritten = rewrite(esc, semanticTable)

    rewritten should equal(
      ExistsIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name),
            patternRelationships =
              Set(
                PatternRelationship(varFor("r").name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)
              )
          ),
          horizon = Some(RegularQueryProjection(Map("n" -> n)))
        ),
        existsVariableName,
        "EXISTS { MATCH (n)-[r]-(m)\nRETURN n AS n }"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("Rewrites FullExistsExpression with Union") {
    val unionQuery = query(
      union(
        singleQuery(
          match_(n_r_m_chain, None),
          return_(
            aliasedReturnItem(n)
          )
        ),
        singleQuery(
          match_(n_r_m_chain, None),
          return_(
            aliasedReturnItem(n)
          )
        )
      )
    )

    val rewrittenQuery: Query = unionQuery.endoRewrite(Namespacer.projectUnions)

    val esc = FullExistsExpression(rewrittenQuery)(pos, Set.empty, Set.empty)

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val existsVariableName = nameGenerator.nextName

    val semanticTable = new SemanticTable().addNode(Variable("n")(InputPosition(16, 1, 17)))

    val rewritten = rewrite(esc, semanticTable)

    rewritten should equal(
      ExistsIRExpression(
        PlannerQuery(
          UnionQuery(
            RegularSinglePlannerQuery(
              QueryGraph(
                patternNodes = Set(n.name, m.name),
                patternRelationships =
                  Set(
                    PatternRelationship(varFor("r").name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)
                  )
              ),
              horizon = RegularQueryProjection(Map("n" -> n))
            ),
            RegularSinglePlannerQuery(
              QueryGraph(
                patternNodes = Set(n.name, m.name),
                patternRelationships =
                  Set(
                    PatternRelationship(varFor("r").name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)
                  )
              ),
              horizon = RegularQueryProjection(Map("n" -> n))
            ),
            distinct = true,
            List(UnionMapping(varFor(n.name), varFor(n.name), varFor(n.name)))
          )
        ),
        existsVariableName,
        "EXISTS { MATCH (n)-[r]-(m)\nRETURN n AS n\nUNION\nMATCH (n)-[r]-(m)\nRETURN n AS n }"
      )(pos, Set.empty, Set.empty)
    )
  }

  test("should rewrite COUNT { (n)-[r]-(m) }") {
    val p = Pattern(Seq(EveryPath(n_r_m.element)))(pos)
    val countExpr = CountExpression(p, None)(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val countVariableName = nameGenerator.nextName

    rewrite(countExpr) shouldBe
      CountIRExpression(
        queryWith(
          qg = QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength))
          ),
          horizon =
            Some(AggregatingQueryProjection(aggregationExpressions = Map(countVariableName -> CountStar()(pos))))
        ),
        countVariableName,
        s"COUNT { (n)-[r]-(m) }"
      )(pos, Set.empty, Set.empty)
  }

  test("should rewrite COUNT { (n)-[r]-(m) WHERE r.foo > 5}") {
    val p = Pattern(Seq(EveryPath(n_r_m.element)))(pos)
    val countExpr = CountExpression(p, Some(rPred))(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val countVariableName = nameGenerator.nextName

    rewrite(countExpr) shouldBe
      CountIRExpression(
        queryWith(
          qg = QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)),
            selections = Selections.from(andedPropertyInequalities(rPred))
          ),
          horizon =
            Some(AggregatingQueryProjection(aggregationExpressions = Map(countVariableName -> CountStar()(pos))))
        ),
        countVariableName,
        s"COUNT { (n)-[r]-(m) WHERE r.foo > 5 }"
      )(pos, Set.empty, Set.empty)
  }

  test("should rewrite count expression with longer pattern and inlined predicates") {
    val p = Pattern(Seq(EveryPath(n_r_m_withPreds.element)))(pos)
    val countExpr = CountExpression(p, None)(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val countVariableName = nameGenerator.nextName

    rewrite(countExpr) shouldBe
      CountIRExpression(
        queryWith(
          QueryGraph(
            patternNodes = Set(n.name, m.name, o.name),
            argumentIds = Set(n.name),
            patternRelationships = Set(
              PatternRelationship(
                r.name,
                (n.name, m.name),
                OUTGOING,
                Seq(relTypeName("R"), relTypeName("P")),
                SimplePatternLength
              ),
              PatternRelationship(r2.name, (m.name, o.name), INCOMING, Seq.empty, SimplePatternLength)
            ),
            selections = Selections.from(Seq(
              not(equals(r2, r)),
              andedPropertyInequalities(rPred),
              andedPropertyInequalities(oPred),
              equals(prop(r.name, "prop"), literalInt(5)),
              equals(prop(o.name, "prop"), literalInt(5)),
              not(hasALabel(o.name))
            ))
          ),
          horizon =
            Some(AggregatingQueryProjection(aggregationExpressions = Map(countVariableName -> CountStar()(pos))))
        ),
        countVariableName,
        s"COUNT { (n)-[r:R|P {prop: 5} WHERE r.foo > 5]->(m)<-[r2]-(o:!% {prop: 5} WHERE o.foo > 5) }"
      )(pos, Set.empty, Set.empty)
  }

  test("should rewrite COUNT { (m) } and add a type check") {
    val p = Pattern(Seq(EveryPath(n_r_m.element.rightNode)))(pos)
    val countExpr = CountExpression(p, None)(pos, Set(r, n), Set(m))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val countVariableName = nameGenerator.nextName

    rewrite(countExpr) shouldBe
      CountIRExpression(
        queryWith(
          qg = QueryGraph(
            patternNodes = Set(m.name),
            argumentIds = Set(m.name),
            selections = Selections.from(AssertIsNode(m)(pos))
          ),
          horizon =
            Some(AggregatingQueryProjection(aggregationExpressions = Map(countVariableName -> CountStar()(pos))))
        ),
        countVariableName,
        s"COUNT { (m) }"
      )(pos, Set.empty, Set.empty)
  }

  test("should rewrite COUNT { (n)-[r]-(m) WHERE r.foo > 5 AND r.foo < 10 } and group predicates") {
    val p = Pattern(Seq(EveryPath(n_r_m.element)))(pos)
    val countExpr = CountExpression(p, Some(and(rPred, rLessPred)))(pos, Set(r, m), Set(n))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val countVariableName = nameGenerator.nextName

    rewrite(countExpr) shouldBe
      CountIRExpression(
        queryWith(
          qg = QueryGraph(
            patternNodes = Set(n.name, m.name),
            argumentIds = Set(n.name),
            patternRelationships =
              Set(PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength)),
            selections = Selections.from(andedPropertyInequalities(rPred, rLessPred))
          ),
          horizon =
            Some(AggregatingQueryProjection(aggregationExpressions = Map(countVariableName -> CountStar()(pos))))
        ),
        countVariableName,
        s"COUNT { (n)-[r]-(m) WHERE r.foo > 5 AND r.foo < 10 }"
      )(pos, Set.empty, Set.empty)
  }

  test("Should rewrite COUNT { (n)-[r]->(m), (o)-[r2]->(m)-[r3]->(q) }") {
    val countExpr = CountExpression(n_r_m_r2_o_r3_q, None)(pos, Set(r, m, r2, r3, q), Set(n, o))

    val nameGenerator = makeAnonymousVariableNameGenerator()
    val countVariableName = nameGenerator.nextName

    rewrite(countExpr) shouldBe
      CountIRExpression(
        queryWith(
          qg = QueryGraph(
            patternNodes = Set(n.name, m.name, o.name, q.name),
            argumentIds = Set(n.name, o.name),
            patternRelationships = Set(
              PatternRelationship(r.name, (n.name, m.name), BOTH, Seq.empty, SimplePatternLength),
              PatternRelationship(r2.name, (o.name, m.name), OUTGOING, Seq.empty, SimplePatternLength),
              PatternRelationship(r3.name, (m.name, q.name), OUTGOING, Seq.empty, SimplePatternLength)
            ),
            selections = Selections.from(Seq(
              not(equals(r, r2)),
              not(equals(r, r3)),
              not(equals(r3, r2))
            ))
          ),
          horizon =
            Some(AggregatingQueryProjection(aggregationExpressions = Map(countVariableName -> CountStar()(pos))))
        ),
        countVariableName,
        s"COUNT { (n)-[r]-(m), (o)-[r2]->(m)-[r3]->(q) }"
      )(pos, Set.empty, Set.empty)
  }

}