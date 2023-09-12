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
package org.neo4j.cypher.internal.physicalplanning

import org.neo4j.cypher.internal.compiler.helpers.LogicalPlanResolver
import org.neo4j.cypher.internal.logical.builder.AbstractLogicalPlanBuilder
import org.neo4j.cypher.internal.logical.builder.Resolver
import org.neo4j.cypher.internal.logical.plans.FieldSignature
import org.neo4j.cypher.internal.logical.plans.LogicalPlan
import org.neo4j.cypher.internal.logical.plans.ProcedureReadOnlyAccess
import org.neo4j.cypher.internal.logical.plans.ProcedureSignature
import org.neo4j.cypher.internal.logical.plans.QualifiedName
import org.neo4j.cypher.internal.physicalplanning.LivenessAnalysisTest.IncorrectLivenessException
import org.neo4j.cypher.internal.physicalplanning.LivenessAnalysisTest.PlanWithLiveAsserts
import org.neo4j.cypher.internal.physicalplanning.PhysicalPlanningAttributes.LiveVariables
import org.neo4j.cypher.internal.util.Foldable.SkipChildren
import org.neo4j.cypher.internal.util.Foldable.TraverseChildren
import org.neo4j.cypher.internal.util.attribution.Id
import org.neo4j.cypher.internal.util.symbols.AnyType
import org.neo4j.cypher.internal.util.test_helpers.CypherFunSuite

class LivenessAnalysisTest extends CypherFunSuite {

  test("simple eager") {
    new PlanWithLiveAsserts()
      .produceResults("a", "c").expectLive("a", "c")
      .eager().expectLive("a", "c")
      .projection("a+b AS c").expectLive("a", "b", "c")
      .projection("1 AS a", "2 AS b").expectLive("a", "b")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("simple sort") {
    new PlanWithLiveAsserts()
      .produceResults("a").expectLive("a")
      .sort("c ASC").expectLive("a", "c")
      .projection("a+b AS c").expectLive("a", "b", "c")
      .projection("1 AS a", "2 AS b").expectLive("a", "b")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("eager with apply") {
    new PlanWithLiveAsserts()
      .produceResults("a", "b").expectLive("a", "b")
      .eager().expectLive("a", "b")
      .apply().expectLive("a", "b")
      .|.eager().expectLive("a")
      .|.argument("a").expectLive("a")
      .eager().expectLive("a", "b")
      .projection("1 AS a", "2 AS b", "3 AS c").expectLive("a", "b", "c")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("eager with nested applies") {
    new PlanWithLiveAsserts()
      .produceResults("a").expectLive("a")
      .eager().expectLive("a")
      .apply().expectLive("a")
      .|.filter("b > 0").expectLive("a", "b")
      .|.apply().expectLive("a", "b")
      .|.|.eager().expectLive("a", "b")
      .|.|.argument("a", "b").expectLive("a", "b")
      .|.eager().expectLive("a", "b")
      .|.argument("a", "b", "c").expectLive("a", "b", "c")
      .projection("1 AS a", "2 AS b", "3 AS c").expectLive("a", "b", "c")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("eager with nested applies 2") {
    new PlanWithLiveAsserts()
      .produceResults("a").expectLive("a")
      .eager().expectLive("a")
      .apply().expectLive("a")
      .|.filter("c > 0").expectLive("a", "c")
      .|.eager().expectLive("a", "c").expectLive("a", "c")
      .|.apply().expectLive("a", "c")
      .|.|.filter("d > 0").expectLive("a", "c", "d")
      .|.|.eager().expectLive("a", "c", "d")
      .|.|.apply().expectLive("a", "c", "d")
      .|.|.|.eager().expectLive("a", "c", "d")
      .|.|.|.argument("a", "b", "c", "d").expectLive("a", "b", "c", "d")
      .|.|.eager().expectLive("a", "b", "c", "d")
      .|.|.argument("a", "b", "c", "d", "e").expectLive("a", "b", "c", "d", "e")
      .|.eager().expectLive("a", "b", "c", "d", "e")
      .|.argument("a", "b", "c", "d", "e", "f").expectLive("a", "b", "c", "d", "e", "f")
      .eager().expectLive("a", "b", "c", "d", "e", "f")
      .projection("i+1 AS a", "i+2 AS b", "i+3 AS c", "i+4 AS d", "i+5 AS e", "i+6 AS f", "i+7 AS g").expectLive(
        "a",
        "b",
        "c",
        "d",
        "e",
        "f",
        "g",
        "i"
      )
      .unwind(s"range(0,size) AS i").expectLive("i", "size")
      .argument("size").expectLive("size")
      .assertCorrectLiveness()
  }

  test("eager on all sides of apply") {
    new PlanWithLiveAsserts()
      .produceResults("readResult1", "readResult2", "readResult3", "readResult4").expectLive(
        "readResult1",
        "readResult2",
        "readResult3",
        "readResult4"
      )
      .eager().expectLive("readResult1", "readResult2", "readResult3", "readResult4")
      .projection(
        "readTopOfApply1 + readTopOfApply2 + readTopOfApply3 AS readResult4",
        "10 AS neverRead4"
      ).expectLive(
        "readResult1",
        "readResult2",
        "readResult3",
        "readResult4",
        "neverRead4",
        "readTopOfApply1",
        "readTopOfApply2",
        "readTopOfApply3"
      )
      .apply().expectLive(
        "readResult1",
        "readResult2",
        "readResult3",
        "readTopOfApply1",
        "readTopOfApply2",
        "readTopOfApply3"
      )
      .|.projection(
        "8 AS neverRead3",
        "9 AS readResult3",
        "readApplyRhs1b + readApplyRhs2 AS readTopOfApply3"
      ).expectLive(
        "neverRead3",
        "readApplyRhs1b",
        "readApplyRhs2",
        "readResult1",
        "readResult2",
        "readResult3",
        "readTopOfApply1",
        "readTopOfApply2",
        "readTopOfApply3"
      )
      .|.eager().expectLive(
        "readResult1",
        "readResult2",
        "readApplyRhs1b",
        "readApplyRhs2",
        "readTopOfApply1",
        "readTopOfApply2"
      )
      .|.projection(
        "readApplyRhs1a + readApplyRhs1b AS readApplyRhs2",
        "7 AS readResult2",
        "8 AS readTopOfApply2",
        "9 AS neverRead2"
      ).expectLive(
        "neverRead2",
        "readApplyRhs1a",
        "readApplyRhs1b",
        "readApplyRhs2",
        "readResult1",
        "readResult2",
        "readTopOfApply1",
        "readTopOfApply2"
      )
      .|.argument("readResult1", "readApplyRhs1a", "readApplyRhs1b", "readTopOfApply1").expectLive(
        "readResult1",
        "readApplyRhs1a",
        "readApplyRhs1b",
        "readTopOfApply1"
      )
      .eager().expectLive("readApplyRhs1a", "readApplyRhs1b", "readResult1", "readTopOfApply1")
      .projection(
        "1 AS neverRead1",
        "3 AS readApplyRhs1a",
        "4 AS readApplyRhs1b",
        "5 AS readTopOfApply1",
        "6 AS readResult1"
      ).expectLive("neverRead1", "readApplyRhs1a", "readApplyRhs1b", "readTopOfApply1", "readResult1")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("eager with semi apply") {
    new PlanWithLiveAsserts()
      .produceResults("a", "b").expectLive("a", "b")
      .eager().expectLive("a", "b")
      .semiApply().expectLive("a", "b")
      .|.eager().expectLive("a")
      .|.argument("a").expectLive("a")
      .eager().expectLive("a", "b").expectLive("a", "b")
      .projection("1 AS a", "2 AS b", "3 AS c").expectLive("a", "b", "c")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("procedure call") {
    val resolver = new LogicalPlanResolver(procedures =
      Set(ProcedureSignature(
        QualifiedName(Seq("test"), "proc"),
        IndexedSeq(FieldSignature("input", AnyType.instance)),
        Some(IndexedSeq(FieldSignature("output", AnyType.instance))),
        None,
        ProcedureReadOnlyAccess,
        id = 0
      ))
    )
    new PlanWithLiveAsserts(resolver)
      .produceResults("output").expectLive("output")
      .procedureCall("test.proc(b)").expectLive("output", "b")
      .eager().expectLive("b", "output")
      .procedureCall("test.proc(a)").expectLive("output", "a", "b")
      .eager().expectLive("a", "b")
      .projection("1 AS a", "2 AS b").expectLive("a", "b")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("function invocation") {
    new PlanWithLiveAsserts()
      .produceResults("sizeA").expectLive("sizeA")
      .eager().expectLive("sizeA")
      .projection("size(ns) AS sizeA").expectLive("ns", "sizeA")
      .eager().expectLive("ns")
      .aggregation(Seq(), Seq("collect(n) AS ns")).expectLive("ns", "n")
      .allNodeScan("n").expectLive("n")
      .assertCorrectLiveness()
  }

  test("eager before apply that uses some live variables") {
    new PlanWithLiveAsserts()
      .produceResults("a").expectLive("a")
      .apply().expectLive("a")
      .|.filter("b > 0").expectLive("b")
      .|.argument("b").expectLive("b")
      .eager().expectLive("a", "b").expectLive("a", "b")
      .projection("1 AS a", "2 AS b", "3 AS c").expectLive("a", "b", "c")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("test works") {
    assertThrows[IncorrectLivenessException](
      new PlanWithLiveAsserts()
        .produceResults("a", "c").expectLive("a", "c")
        .eager().expectLive("a", "b", "c")
        .projection("a+b AS c").expectLive("a", "b", "c")
        .projection("1 AS a", "2 AS b").expectLive("a", "b")
        .argument().expectLive()
        .assertCorrectLiveness()
    )
    assertThrows[IncorrectLivenessException](
      new PlanWithLiveAsserts()
        .produceResults("a", "c").expectLive("a", "c")
        .eager().expectLive("a")
        .projection("a+b AS c").expectLive("a", "b", "c")
        .projection("1 AS a", "2 AS b").expectLive("a", "b")
        .argument().expectLive()
        .assertCorrectLiveness()
    )
  }

  test("projections with discard") {
    new PlanWithLiveAsserts()
      .produceResults("hi").expectLive("hi")
      .eager().expectLive("hi")
      .projection("keep as hi").expectLive("keep", "hi")
      .eager().expectLive("keep")
      .projection("keep as keep").expectLive("keep")
      .projection("'bla' + a as keep", "'blö' + a as discard").expectLive("keep", "a", "discard")
      .unwind("range(0, 10) AS a").expectLive("a")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("nested plan collect expression") {
    new PlanWithLiveAsserts()
      .produceResults("x").expectLive("x")
      .eager().expectLive("x")
      .nestedPlanCollectExpressionProjection("x", "b.prop").expectLive("UNNAMED1", "a", "b", "x")
      .|.eager()
      .|.expand("(a)-->(b)")
      .|.allNodeScan("a")
      .eager().expectLive()
      .projection("'i' AS i").expectLive("i")
      .argument().expectLive()
      .assertCorrectLiveness()
  }

  test("nested plan collect expression 2") {
    new PlanWithLiveAsserts()
      .produceResults("x").expectLive("x")
      .eager().expectLive("x")
      .nestedPlanCollectExpressionProjection("x", "b.b").expectLive("a", "b", "c", "x")
      .|.eager()
      .|.projection("{b:1} AS b", "'x' AS c")
      .|.argument("a")
      .eager().expectLive("a")
      .nodeByLabelScan("a", "A", "randomArg").expectLive("a", "randomArg")
      .assertCorrectLiveness()
  }

  test("nested plan exist expression") {
    new PlanWithLiveAsserts()
      .produceResults("r", "x").expectLive("r", "x")
      .eager().expectLive("r", "x")
      .nestedPlanExistsExpressionProjection("r").expectLive("UNNAMED1", "a", "b", "r", "x")
      .|.eager()
      .|.expand("(a)-->(b)")
      .|.allNodeScan("a")
      .projection("1 AS x").expectLive("x")
      .argument().expectLive()
      .assertCorrectLiveness()
  }
}

object LivenessAnalysisTest {
  case class IncorrectLivenessException(msg: String) extends IllegalStateException(msg)

  object TestBreakingPolicy extends PipelineBreakingPolicy {
    override def breakOn(lp: LogicalPlan, outerApplyPlanId: Id): Boolean = ???
  }

  object DiscardInAllPlansPolicy extends PipelineBreakingPolicy {
    override def breakOn(lp: LogicalPlan, outerApplyPlanId: Id): Boolean = ???
    override def canBeDiscardingPlan(lp: LogicalPlan): Boolean = true
  }

  class PlanWithLiveAsserts(resolver: Resolver = new LogicalPlanResolver)
      extends AbstractLogicalPlanBuilder[LogicalPlan, PlanWithLiveAsserts](resolver, wholePlan = true) {
    private val expectedLive = new LiveVariables

    def expectLive(variables: String*): PlanWithLiveAsserts = {
      expectedLive.set(idOfLastPlan, variables.toSet)
      this
    }

    def assertCorrectLiveness(): Unit = {
      val plan = build()
      val plans = planById(plan)
      assert(LivenessAnalysis.computeLiveVariables(plan, DiscardInAllPlansPolicy), expectedLive, plans)

      val actualWithDefaultBreaks = LivenessAnalysis.computeLiveVariables(plan, TestBreakingPolicy)
      val expectedWithDefaultBreaks = new LiveVariables()
      expectedLive.iterator.foreach { case (id, live) =>
        if (TestBreakingPolicy.canBeDiscardingPlan(plans(id))) expectedWithDefaultBreaks.set(id, live)
      }
      assert(actualWithDefaultBreaks, expectedWithDefaultBreaks, plans)
    }

    private def assert(actual: LiveVariables, expected: LiveVariables, plans: Map[Id, LogicalPlan]): Unit = {
      if (actual != expected) {
        val maxId = (actual.iterator ++ expected.iterator).map(_._1).maxBy(_.x)
        val diff = Range.inclusive(0, maxId.x)
          .map(Id.apply)
          .collect {
            case id if actual.getOption(id) != expected.getOption(id) =>
              (id, actual.getOption(id), expected.getOption(id))
          }
          .map { case (id, actualValue, expectedValue) =>
            s"$id (${plans(id).getClass.getSimpleName}): actual=${actualValue.map(_.toSeq.sorted)} expected=${expectedValue.map(_.toSeq.sorted)}"
          }

        throw IncorrectLivenessException(
          s"""Live variables diff:
             |${diff.mkString("\n")}""".stripMargin
        )
      }
    }

    private def planById(root: LogicalPlan): Map[Id, LogicalPlan] = {
      root.folder.treeFold(Map.empty[Id, LogicalPlan]) {
        case plan: LogicalPlan => acc => TraverseChildren(acc.updated(plan.id, plan))
        case _                 => acc => SkipChildren(acc)
      }
    }

    override protected def build(readOnly: Boolean): LogicalPlan = buildLogicalPlan()
  }
}