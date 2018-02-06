package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.History.Elem
import de.fhg.iais.nqfpruleminer.Value.{Label, Position}
import de.fhg.iais.utils.fail
import org.joda.time.DateTime

import scala.collection.mutable.Map

object History {
  case class Elem(dateTime: DateTime, value: GroupBy)

  def apply(feature: Feature)(implicit ctx: Context): History =
    feature.typ match {
      case aggregator: DerivedType.AGGREGATE => AggregateHistory(aggregator, feature.position)
      case aggregator: DerivedType.COUNT => CountHistory(aggregator, feature.position)
      case typ => fail(s"Wrong aggregation type $typ"); null
    }
}

trait History {
  import History._

  val aggr: AggregatorType
  val position: Position

  var lastTime: DateTime = DateTime.parse("0")
  var history: List[Elem] = List[Elem]()

  override def toString: String =
    if (history.nonEmpty) history.map { case Elem(d, v) => s"(${d.getSecondOfDay}, $v)" }.reduce(_ + "," + _) else "empty history"

  def apply(label: Value.Label, actualTime: DateTime, values: List[Value]): List[Value] = {
    val indexed = values.toArray
    val value = aggr match {
      case aggr: DerivedType.COUNT => GroupBy(aggr.seqIdPos.map(indexed), Group(aggr.positions.map(indexed(_))), label, position)
      case aggr: DerivedType.AGGREGATE => GroupBy(aggr.seqIdPos.map(indexed), indexed(aggr.position), label, position)
    }
    if (label > 0) history :+= Elem(actualTime, value)
    if (lastTime.compareTo(actualTime) < 0) {
      clear(actualTime.minusSeconds(aggr.timeFrame))
      lastTime = actualTime
    }
    update(value, label)
  }

  def update(value: GroupBy, label: Value.Label): List[Value]

  def delete(value: GroupBy): Unit

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

case class CountHistory(aggr: DerivedType.COUNT, position: Position)(implicit ctx: Context) extends History {
  import scala.collection.mutable.Map

  implicit val numberOfTargetGroups: Int = ctx.numberOfTargetGroups
  private val distributionMap = Map[Option[Value], Map[Value, Distribution]]()

  def update(groupBy: GroupBy, label: Value.Label): List[Value] = {
    if (label > 0) {
      val value = groupBy.value
      distributionMap get groupBy.seqId match {
        case None => distributionMap += groupBy.seqId -> Map(value -> Distribution(label))
        case Some(map) =>
          map get groupBy.value match {
            case None => map += groupBy.value -> Distribution(label)
            case Some(distribution) => distribution.add(label)
          }
      }
    }
    distributionMap
      .flatMap {
        case (k, m) => m.flatMap {
          case (v, d) =>
            for (i <- 1 until ctx.numberOfTargetGroups;
                 r = d(i) if aggr.condition(r)
            ) yield
              GroupBy(k, Counted(v, if (aggr.existsOnly) None else Some(r), i, position), position)
        }
      }.toList.distinct
  }

  def delete(groupBy: GroupBy): Unit = {
    val distribution = distributionMap(groupBy.seqId)(groupBy.value)
    distribution.minus(groupBy.label)
    if (distribution.sum == 0) distributionMap(groupBy.seqId) -= groupBy.value
  }
}

case class AggregateHistory(aggr: DerivedType.AGGREGATE, position: Position)(implicit ctx: Context) extends History {
  private val aggregationOp = aggr.op

  private val aggregationMap = Map[Option[Value], Aggregation]()

  def getHistory(seqId: Option[Value], label: Label) : () => List[Double] =
    () => for (Elem(_, GroupBy(_seqId, Numeric(_value, _), _label, _)) <- history if _seqId == seqId && _label == label) yield _value

  def update(groupBy: GroupBy, label: Value.Label): List[Value] = {
    if (label > 0) {
      val value = groupBy.value.asInstanceOf[Numeric]
      aggregationMap get groupBy.seqId match {
        case None =>
          val aggregation = Aggregation(aggregationOp, getHistory(groupBy.seqId, label))
          aggregation.plus(value, label)
          aggregationMap += groupBy.seqId -> aggregation
        case Some(aggregation) =>
          aggregation.plus(value, label)
      }
    }
    aggregationMap
      .flatMap {
        case (k, c) =>
          for (
            i <- 1 until ctx.numberOfTargetGroups;
            r = c.get(i) if aggr.condition(r)
          ) yield
            GroupBy(k, Aggregated(aggregationOp, aggr.position, if (aggr.existsOnly) None else Some(c.get(i)), i, position), position)
      }
  }.toList

  def delete(groupBy: GroupBy): Unit = {
    val aggregation = aggregationMap(groupBy.seqId)
    aggregation.minus(groupBy.value.asInstanceOf[Numeric], groupBy.label)
    if (aggregation.sum == 0) aggregationMap -= groupBy.seqId
  }
}