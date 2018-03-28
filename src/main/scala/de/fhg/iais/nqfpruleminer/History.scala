package de.fhg.iais.nqfpruleminer

import akka.actor.Props
import de.fhg.iais.nqfpruleminer.History.Elem
import de.fhg.iais.nqfpruleminer.Item.Position
import de.fhg.iais.nqfpruleminer.Value.Label
import de.fhg.iais.utils.TimeFrame.TimeFrame
import de.fhg.iais.utils.fail
import org.joda.time.DateTime

import scala.collection.mutable.Map
import scala.util.{Failure, Success, Try}

object History {
  case class Elem(dateTime: DateTime, value: GroupBy)

  def apply(feature: Feature)(implicit ctx: Context): History =
    feature.typ match {
      case aggregator: DerivedType.AGGREGATE =>
        aggregator.history match {
          case Left(lengthOfHistory) => new AggregateHistory(aggregator, feature.position) with HistoryByLength
          case Right(timeFrame) => new AggregateHistory(aggregator, feature.position) with HistoryByTimeframe
        }
      case aggregator: DerivedType.COUNT =>
        aggregator.history match {
          case Left(lengthOfHistory) => new CountHistory(aggregator, feature.position) with HistoryByLength
          case Right(timeFrame) => new CountHistory(aggregator, feature.position) with HistoryByTimeframe
        }
      case typ => fail(s"Wrong aggregation type $typ"); null
    }
}

trait History extends {
  val aggr: AggregatorType
  val position: Position

  def apply(label: Value.Label, actualTime: DateTime, values: Vector[Valued]): Vector[Item]
  def update(value: GroupBy, label: Value.Label): Unit
  def getAggregatedValues: Vector[Item]
  def delete(value: GroupBy): Unit
}

trait HistoryByTimeframe extends History {

  var history: List[Elem] = List[Elem]()
  private var lastTime: DateTime = DateTime.parse("0")
  private val timeframe: TimeFrame = aggr.history match {case Left(_) => 0; case Right(t) => t}

  override def toString: String =
    if (history.nonEmpty) history.map { case Elem(d, v) => s"(${d.getSecondOfDay}, $v)" }.reduce(_ + "," + _) else "empty history"

  def apply(label: Value.Label, actualTime: DateTime, baseItems: Vector[Valued]): Vector[Item] = {
    val value = aggr match {
      case aggr: DerivedType.COUNT =>
        GroupBy(aggr.seqIdPos.map(baseItems(_)), Group(aggr.positions.map(baseItems(_))), label, position)
      case aggr: DerivedType.AGGREGATE =>
        GroupBy(aggr.seqIdPos.map(baseItems(_)), baseItems(aggr.position), label, position)
    }
    if (label > 0) {
      history :+= Elem(actualTime, value)
      update(value, label)
    }
    if (actualTime.compareTo(lastTime) > 0) {
      clear(actualTime.minusSeconds(timeframe))
      lastTime = actualTime
    }
    getAggregatedValues
  }

  def clear(borderTime: DateTime): Unit = {
    history match {
      case Elem(time, value) :: tail if borderTime.compareTo(time) > 0 =>
        history = tail
        delete(value)
        clear(borderTime)
      case _ =>
    }
  }
}

trait HistoryByLength extends History {

  var history: List[Elem] = List[Elem]()
  private val lengthOfHistory = aggr.history match {case Left(l) => l; case Right(_) => 0}

  override def toString: String =
    if (history.nonEmpty) history.map { case Elem(d, v) => s"(${d.getSecondOfDay}, $v)" }.reduce(_ + "," + _) else "empty history"

  def apply(label: Value.Label, actualTime: DateTime, values: Vector[Valued]): Vector[Item] = {
    val value = aggr match {
      case aggr: DerivedType.COUNT => GroupBy(aggr.seqIdPos.map(values(_)), Group(aggr.positions.map(values(_))), label, position)
      case aggr: DerivedType.AGGREGATE => GroupBy(aggr.seqIdPos.map(values(_)), values(aggr.position), label, position)
    }
    if (label > 0) {
      history :+= Elem(actualTime, value)
      update(value, label)
    }
    history = history.take(lengthOfHistory)
    getAggregatedValues
  }
}

abstract class CountHistory(val aggr: DerivedType.COUNT, val position: Position)(implicit ctx: Context) extends History {

  def history: List[Elem]

  implicit val numberOfTargetGroups: Int = ctx.numberOfTargetGroups
  private val distributionMap = scala.collection.mutable.Map[Option[Item], scala.collection.mutable.Map[Item, Distribution]]()

  def update(groupBy: GroupBy, label: Value.Label): Unit = {
    //    println(s"Count update $groupBy")
    val value = groupBy.item
    distributionMap get groupBy.seqId match {
      case None => distributionMap += groupBy.seqId -> scala.collection.mutable.Map(value -> Distribution(label))
      case Some(map) =>
        map get groupBy.item match {
          case None => map += groupBy.item -> Distribution(label)
          case Some(distribution) => distribution.add(label)
        }
    }
//    println(s"distributionMap after update $distributionMap")
  }

  def getAggregatedValues: Vector[Item] =
    distributionMap
      .flatMap {
        case (k, m) => m.flatMap {
          case (item, d) =>
            for (label <- 1 until ctx.numberOfTargetGroups;
                 value = d(label) if aggr.condition(Numeric(value.toDouble))
            ) yield {
              val x = aggr.condition(Numeric(value.toDouble))
              item match {    // takes case of empty groups, then exists or count operator enclose the seqId
                case Group(values, _) if values.isEmpty =>
                  Counted(GroupBy(k, item, label, position), if (aggr.existsOnly) None else Some(value), label, position)
                case _ =>
                  GroupBy(k, Counted(item, if (aggr.existsOnly) None else Some(value), label, position), label, position)
              }
            }

        }
      }.toVector.distinct

  def delete(groupBy: GroupBy): Unit = {
    //    println(s"Count delete $groupBy")
    val distribution = Try(distributionMap(groupBy.seqId)) match {
      case Success(map) =>
        Try(map(groupBy.item)) match {
          case Success(x) => x
          case Failure(e) =>
            fail(s"Count: $e  \ngroupBy: ${groupBy.item} \nmap: $distributionMap \nhistory: $history"); null
        }
      case Failure(e) =>
        fail(s"Count: $e  \nvalue: $groupBy \nmap: $distributionMap \nhistory: $history"); null
    }
    distribution.minus(groupBy.label)
    if (distribution.sum == 0) distributionMap(groupBy.seqId) -= groupBy.item
//    println(s"Count distributionMap $distributionMap")

  }
}

abstract class AggregateHistory(val aggr: DerivedType.AGGREGATE, val position: Position)(implicit ctx: Context) extends History {
  private val aggregationOp = aggr.op

  def history: List[Elem]
  private val aggregationMap = scala.collection.mutable.Map[Option[Item], Aggregation]()

  def getHistory(seqId: Option[Item], label: Label): () => List[Double] =
    () => for (Elem(_, GroupBy(_seqId, Valued(Numeric(_value), _), _label, _)) <- history if _seqId == seqId && _label == label) yield _value

  def update(groupBy: GroupBy, label: Value.Label): Unit = {
    //    println(s"Aggregate update $groupBy")
    val value = Item.toNumeric(groupBy.item)
    aggregationMap get groupBy.seqId match {
      case None =>
        val aggregation = Aggregation(aggregationOp, getHistory(groupBy.seqId, label))
        aggregation.plus(value, label)
        aggregationMap += groupBy.seqId -> aggregation
      case Some(aggregation) =>
        aggregation.plus(value, label)
    }
//    println(s"aggregationMap after update $aggregationMap")
  }

  def getAggregatedValues: Vector[Item] =
    aggregationMap
      .flatMap {
        case (k, c) =>
          for (
            label <- 1 until ctx.numberOfTargetGroups;
            r = c.get(label) if aggr.condition(Numeric(r))
          ) yield {
            r
            val x = aggr.condition(Numeric(r.toDouble))
            GroupBy(k, Aggregated(aggregationOp, aggr.position, if (aggr.existsOnly) None else Some(c.get(label)), label, position), label, position)
          }
      }.toVector

  def delete(groupBy: GroupBy): Unit = {
    val aggregation =
      Try(aggregationMap(groupBy.seqId)) match {
        case Success(x) => x
        case Failure(e) =>
          fail(s"Aggregate: $e  \ngroupBy: $groupBy \nmap: $aggregationMap \nhistory: $history")
          null
      }
    aggregation.minus(Item.toNumeric(groupBy.item), groupBy.label)
    if (aggregation.sum == 0) aggregationMap -= groupBy.seqId
  }

}