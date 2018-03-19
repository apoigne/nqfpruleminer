package de.fhg.iais.nqfpruleminer

import org.joda.time.DateTime
import org.scalatest.FunSuite

class HistoryTest3 extends FunSuite {

  implicit private val ctx: Context = new Context("src/test/resources/historytest3.conf")


  val nonTG = Valued(Nominal("NON_TG"), -1)
  val target = Valued(Nominal("TARGET"), -1)
  val nominal4711 = Valued(Nominal("4711"), 0)
  val nominal4712 = Valued(Nominal("4712"), 0)
  val nominal4721 = Valued(Nominal("4721"), 0)
  val nominal4722 = Valued(Nominal("4722"), 0)
  val time1 = Valued(Date(ctx.parseDateTime("1")), -1)
  val time2 = Valued(Date(ctx.parseDateTime("2")), -1)
  val time3 = Valued(Date(ctx.parseDateTime("3")), -1)
  val time4 = Valued(Date(ctx.parseDateTime("4")), -1)
  val time10 = Valued(Date(ctx.parseDateTime("10")), -1)

  val nominalA = Valued(Nominal("a"), 1)
  val nominalB = Valued(Nominal("b"), 1)
  val nominalC = Valued(Nominal("c"), 1)
  val numeric0 = Valued(Numeric(0.0), 2)
  val numeric1 = Valued(Numeric(1.0), 2)
  val numeric2 = Valued(Numeric(2.0), 2)
  val numeric3 = Valued(Numeric(3.0), 2)

  val data: List[List[Valued]] = List(
    List(target, nominal4711, time1, nominalA, numeric1),        // 0
    List(nonTG, nominal4712, time1, nominalA, numeric1),        // 1
    List(nonTG, nominal4721, time1, nominalA, numeric1),        // 2
    List(nonTG, nominal4722, time1, nominalA, numeric1),        // 3
    List(target, nominal4711, time2, nominalA, numeric3),        // 4
    List(nonTG, nominal4712, time2, nominalA, numeric1),        // 5
    List(target, nominal4721, time2, nominalA, numeric1),        // 6
    List(target, nominal4722, time2, nominalA, numeric0),        // 7
    List(target, nominal4711, time3, nominalB, numeric1),        // 8
    List(nonTG, nominal4712, time3, nominalB, numeric2),        // 9
    List(nonTG, nominal4721, time3, nominalB, numeric1),        // 10
    List(nonTG, nominal4722, time3, nominalA, numeric1),        // 11
    List(target, nominal4711, time4, nominalB, numeric2),        // 12
    List(nonTG, nominal4712, time4, nominalC, numeric2),        // 13
    List(nonTG, nominal4721, time4, nominalA, numeric1),        // 14
    List(nonTG, nominal4722, time4, nominalC, numeric1),        // 15
    List(target, nominal4711, time10, nominalA, numeric1),       // 16
    List(target, nominal4712, time10, nominalA, numeric2),       // 17
    List(nonTG, nominal4721, time10, nominalA, numeric1),       // 18
  )

  private val histories =
    ctx.aggregateFeatures.map(
      feature =>
        feature.typ match {
          case typ: DerivedType.COUNT => new CountHistory(typ, feature.position) with HistoryByTimeframe
          case typ: DerivedType.AGGREGATE => new AggregateHistory(typ, feature.position) with HistoryByTimeframe
        }
    )

  def toTarget(target: Valued): Int = target.value match {case Nominal("TARGET") => 1; case _ => 0}
  def toTimestamp(ts: Valued): DateTime = ts.value match {case Date(d) => d; case _ => null}

  private def step(instance: List[Valued]) = {
    for (history <- histories) yield {
      history(toTarget(instance.head), toTimestamp(instance(2)), Vector(instance(1), instance(3), instance(4)))
    }
  }

  test("History test") {
    {
      println("number 0")
      val result = step(data.head)
      assert(
        result.head ==
          List(GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3))
      )
      assert(result(1).isEmpty)
    }
    {
      println("number 1")
      val result = step(data(1))
      assert(
        result.head ==
          List(GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3))
      )
      assert(result(1).isEmpty)
    }
    {
      println("number 2")
      val result = step(data(2))
      assert(
        result.head ==
          List(GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3))
      )
      assert(result(1).isEmpty)
    }

    {
      println("number 3")
      val result = step(data(3))
      assert(
        result.head ==
          List(GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3))
      )
      assert(result(1).isEmpty)
    }
    {
      println("number 4")
      val result = step(data(4))
      assert(
        result.head ==
          List(GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3))
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(4.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 5")
      val result = step(data(5))
      assert(
        result.head ==
          List(GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3))
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(4.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 6")
      val result = step(data(6))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4721), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(4.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 7")
      val result = step(data(7))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4721), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4722), Aggregated(AggregationOp.Min, 2, Some(0.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(4.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 8")
      val result = step(data(8))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4721), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4722), Aggregated(AggregationOp.Min, 2, Some(0.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(5.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 9")
      val result = step(data(9))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4721), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4722), Aggregated(AggregationOp.Min, 2, Some(0.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(5.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 10")
      val result = step(data(10))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4721), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4722), Aggregated(AggregationOp.Min, 2, Some(0.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(5.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 11")
      val result = step(data(11))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4721), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4722), Aggregated(AggregationOp.Min, 2, Some(0.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(5.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 12")
      val result = step(data(12))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(7.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 13")
      val result = step(data(13))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(7.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 14")
      val result = step(data(14))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(7.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 15")
      val result = step(data(15))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).toSet ==
        Set(
          GroupBy(Some(nominal4711), Aggregated(AggregationOp.Sum, 2, Some(7.0), 1, 4), 1, 4)
        )
      )
    }
    {
      println("number 16")
      val result = step(data(16))
      assert(
        result.head ==
          List(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3)
          )
      )
      assert(result(1).isEmpty)
    }
    {
      println("number 17")
      val result = step(data(17))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4712), Aggregated(AggregationOp.Min, 2, Some(2.0), 1, 3), 1, 3)
          )
      )
      assert(
        result(1) ==
          List(
            GroupBy(Some(nominal4712), Aggregated(AggregationOp.Sum, 2, Some(2.0), 1, 4), 1, 4)
          )
      )
    }
    {
      println("number 18")
      val result = step(data(18))
      assert(
        result.head.toSet ==
          Set(
            GroupBy(Some(nominal4711), Aggregated(AggregationOp.Min, 2, Some(1.0), 1, 3), 1, 3),
            GroupBy(Some(nominal4712), Aggregated(AggregationOp.Min, 2, Some(2.0), 1, 3), 1, 3)
          )
      )
      assert(
        result(1) ==
          List(GroupBy(Some(nominal4712), Aggregated(AggregationOp.Sum, 2, Some(2.0), 1, 4), 1, 4))
      )
    }

  }
}

