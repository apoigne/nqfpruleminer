package de.fhg.iais.nqfpruleminer

import org.joda.time.DateTime
import org.scalatest.FunSuite

class HistoryTest4 extends FunSuite {

  implicit private val ctx: Context = new Context("src/test/resources/historytest4.conf", 0, 0)

  val data: List[List[Value]] = List(
    List(Nominal("TARGET", -1), Nominal("4711", 0), Date(ctx.parseDateTime("1"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 0
    List(Nominal("NON_TG", -1), Nominal("4712", 0), Date(ctx.parseDateTime("1"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 1
    List(Nominal("NON_TG", -1), Nominal("4721", 0), Date(ctx.parseDateTime("1"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 2
    List(Nominal("NON_TG", -1), Nominal("4722", 0), Date(ctx.parseDateTime("1"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 3
    List(Nominal("TARGET", -1), Nominal("4711", 0), Date(ctx.parseDateTime("2"), -1), Nominal("a", 1), Numeric(3.0, 2)),        // 4
    List(Nominal("NON_TG", -1), Nominal("4712", 0), Date(ctx.parseDateTime("2"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 5
    List(Nominal("TARGET", -1), Nominal("4721", 0), Date(ctx.parseDateTime("2"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 6
    List(Nominal("TARGET", -1), Nominal("4722", 0), Date(ctx.parseDateTime("2"), -1), Nominal("a", 1), Numeric(0.0, 2)),        // 7
    List(Nominal("TARGET", -1), Nominal("4711", 0), Date(ctx.parseDateTime("3"), -1), Nominal("b", 1), Numeric(1.0, 2)),        // 8
    List(Nominal("NON_TG", -1), Nominal("4712", 0), Date(ctx.parseDateTime("3"), -1), Nominal("b", 1), Numeric(2.0, 2)),        // 9
    List(Nominal("NON_TG", -1), Nominal("4721", 0), Date(ctx.parseDateTime("3"), -1), Nominal("b", 1), Numeric(1.0, 2)),        // 10
    List(Nominal("NON_TG", -1), Nominal("4722", 0), Date(ctx.parseDateTime("3"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 11
    List(Nominal("TARGET", -1), Nominal("4711", 0), Date(ctx.parseDateTime("4"), -1), Nominal("b", 1), Numeric(2.0, 2)),        // 12
    List(Nominal("NON_TG", -1), Nominal("4712", 0), Date(ctx.parseDateTime("4"), -1), Nominal("c", 1), Numeric(2.0, 2)),        // 13
    List(Nominal("NON_TG", -1), Nominal("4721", 0), Date(ctx.parseDateTime("4"), -1), Nominal("a", 1), Numeric(1.0, 2)),        // 14
    List(Nominal("NON_TG", -1), Nominal("4722", 0), Date(ctx.parseDateTime("4"), -1), Nominal("c", 1), Numeric(1.0, 2)),        // 15
    List(Nominal("TARGET", -1), Nominal("4711", 0), Date(ctx.parseDateTime("10"), -1), Nominal("a", 1), Numeric(1.0, 2)),       // 16
    List(Nominal("TARGET", -1), Nominal("4712", 0), Date(ctx.parseDateTime("10"), -1), Nominal("a", 1), Numeric(2.0, 2)),       // 17
    List(Nominal("NON_TG", -1), Nominal("4721", 0), Date(ctx.parseDateTime("10"), -1), Nominal("a", 1), Numeric(1.0, 2)),       // 18
  )

  private val histories =
    ctx.aggregateFeatures.map(
      feature =>
        feature.typ match {
          case typ: DerivedType.COUNT => CountHistory(typ, feature.position)
          case typ: DerivedType.AGGREGATE => AggregateHistory(typ, feature.position)
        }
    )

  def toTarget(target: Value): Int = target match {case Nominal("TARGET", _) => 1; case _ => 0}
  def toTimestamp(ts: Date): DateTime = ts.value

  private def step(instance: List[Value]) = {
    for (history <- histories) yield {
      history(toTarget(instance.head), toTimestamp(instance(2).asInstanceOf[Date]), List(instance(1), instance(3), instance(4)))
    }
  }

  test("History test") {
    {
      println("number 0")
      val result = step(data.head)
      assert(
        result.head ==
          List(GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1))
      )
      assert(
        result(1) ==
          List(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }
    {
      println("number 1")
      val result = step(data(1))
      assert(
        result.head ==
          List(GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1))
      )
      assert(
        result(1) ==
          List(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }
    {
      println("number 2")
      val result = step(data(2))
      assert(
        result.head ==
          List(GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1))
      )
      assert(
        result(1) ==
          List(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }

    {
      println("number 3")
      val result = step(data(3))
      assert(
        result.head ==
          List(GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1))
      )
      assert(
        result(1) ==
          List(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }
    {
      println("number 4")
      val result = step(data(4))
      assert(
        result.head ==
          List(GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1))
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 5")
      val result = step(data(5))
      assert(
        result.head ==
          List(GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1))
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 6")
      val result = step(data(6))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4721", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 7")
      val result = step(data(7))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4721", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4722", 0)), Counted(Group(List(Numeric(0.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 8")
      val result = step(data(8))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4721", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4722", 0)), Counted(Group(List(Numeric(0.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 9")
      val result = step(data(9))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4721", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4722", 0)), Counted(Group(List(Numeric(0.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)

        )
      )
    }
    {
      println("number 10")
      val result = step(data(10))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4721", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4722", 0)), Counted(Group(List(Numeric(0.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)

        )
      )
    }
    {
      println("number 11")
      val result = step(data(11))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(3.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4721", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4722", 0)), Counted(Group(List(Numeric(0.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)

        )
      )
    }
    {
      println("number 12")
      val result = step(data(12))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(2.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 13")
      val result = step(data(13))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(2.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1)

        )
      )
    }
    {
      println("number 14")
      val result = step(data(14))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(2.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 15")
      val result = step(data(15))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4721", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1),
          GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(2.0, 2), Nominal("b", 1)), -1), Some(1), 1, 4), 4, -1)
        )
      )
    }
    {
      println("number 16")
      val result = step(data(16))
      assert(
        result.head ==
          List(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1)
          )
      )
      assert(
        result(1).toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }
    {
      println("number 17")
      val result = step(data(17))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4712", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
          )
      )
      assert(
        result(1).toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
            GroupBy(Some(Nominal("4712", 0)), Counted(Group(List(Numeric(2.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }
    {
      println("number 18")
      val result = step(data(18))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
            GroupBy(Some(Nominal("4712", 0)), Aggregated(AggregationOp.Min, 2, None, 1, 3), 3, -1),
          )
      )
      assert(
        result(1).toSet ==
          Set(
            GroupBy(Some(Nominal("4711", 0)), Counted(Group(List(Numeric(1.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1),
            GroupBy(Some(Nominal("4712", 0)), Counted(Group(List(Numeric(2.0, 2), Nominal("a", 1)), -1), Some(1), 1, 4), 4, -1)
          )
      )
    }

  }
}

