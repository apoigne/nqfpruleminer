package nqfpruleminer

import nqfpruleminer.History.Elem
import nqfpruleminer.Item.Position
import nqfpruleminer.utils.fail
import org.joda.time.DateTime

object History {
  case class Elem(dateTime: Option[DateTime], value: GroupBy)

  def apply(feature: Feature)(implicit ctx: Context): History =
    feature.typ match {
      case aggregator: DerivedType.AGGREGATE =>
        aggregator.history match {
          case _: DiscretePeriod => new AggregateHistory(aggregator, feature.position) with HistoryByLength
          case _: TimePeriod => new AggregateHistory(aggregator, feature.position) with HistoryByTimeframe
        }
      case aggregator: DerivedType.COUNT =>
        aggregator.history match {
          case _: DiscretePeriod => new CountHistory(aggregator, feature.position) with HistoryByLength
          case _: TimePeriod => new CountHistory(aggregator, feature.position) with HistoryByTimeframe
        }
      case typ => fail(s"Wrong aggregation type $typ"); null
    }
}

trait History {
  import History.*

  val aggr: AggregatorType
  val position: Position
  var history: List[Elem] = List[Elem]()

  val lengthOfHistory: Int = aggr.history.getOffset + aggr.history.getLength
  def apply(label: Value.Label, actualTime: Option[DateTime], baseItems: Vector[Valued]): Vector[Item]
  def update(actualTime: Option[DateTime], baseItems: Vector[Valued], label: Value.Label): Unit
  def getAggregatedValues(history: List[Elem]): Vector[Item]
}

trait HistoryByTimeframe extends History {
  private var lastTime: DateTime = DateTime.parse("0")

  def apply(label: Value.Label, actualTimeOption: Option[DateTime], baseItems: Vector[Valued]): Vector[Item] = {
    if (aggr.condition(baseItems.map(_.value))) update(actualTimeOption, baseItems, label)
    val actualTime = actualTimeOption.get
    if (actualTime.compareTo(lastTime) > 0) {
      history = history.filterNot {
        elem =>
          val elemTime = elem.dateTime.get
          actualTime.minusSeconds(lengthOfHistory).compareTo(elemTime) > 0 || elemTime.compareTo(actualTime) > 0
      }
      lastTime = actualTime
    }
    getAggregatedValues(history.filter(elem => {actualTime.minusSeconds(aggr.history.getOffset).compareTo(elem.dateTime.get) > 0}))
  }
}

trait HistoryByLength extends History {
  private var conditions = List[Boolean]()

  def apply(label: Value.Label, actualTime: Option[DateTime], baseItems: Vector[Valued]): Vector[Item] = {
    val aggregatedValues = getAggregatedValues(history.zip(conditions).drop(aggr.history.getOffset).filter(_._2).map(_._1))
    update(None, baseItems, label)
    history = history.take(lengthOfHistory)
    conditions = (aggr.condition(baseItems.map(_.value)) +: conditions).take(lengthOfHistory)
    aggregatedValues
  }
}

abstract class CountHistory(val aggr: DerivedType.COUNT, val position: Position)(implicit ctx: Context) extends History {

  def update(actualTime: Option[DateTime], baseItems: Vector[Valued], label: Value.Label): Unit = {
    val elem = Elem(actualTime, GroupBy(aggr.seqIdPos.map(baseItems(_)), Compound(aggr.positions.map(baseItems(_))), position))
    history = elem +: history
  }

  def getAggregatedValues(history: List[Elem]): Vector[Item] =
    history
      .map(_.value)
      .groupBy(identity)
      .view
      .mapValues(_.length)
      .filter(_._2 >= aggr.minimum)
      .map {
        case (GroupBy(k, item, pos), count) =>
          if (aggr.existsOnly)
            GroupBy(k, Existed(item, pos), pos)
          else
            GroupBy(k, Counted(item, count, pos), pos)
      }.toVector
}

abstract class AggregateHistory(val aggr: DerivedType.AGGREGATE, val position: Position)(implicit ctx: Context) extends History {

  def update(actualTime: Option[DateTime], baseItems: Vector[Valued], label: Value.Label): Unit = {
    val elem = Elem(actualTime, GroupBy(aggr.seqIdPos.map(baseItems(_)), baseItems(aggr.position), position))
    history = elem +: history
  }

  def getAggregatedValues(history: List[Elem]): Vector[Item] =
    history
      .map(_.value)
      .groupBy(_.seqId)
      .view
      .mapValues(x => x.map(_.item.asInstanceOf[Valued].value.asInstanceOf[Numeric].value).sum)
      .filter(_._2 >= aggr.minimum)
      .map {
        case (item, sum) =>
          GroupBy(item, Aggregated(aggr.op, aggr.position, sum, position), position)
      }.toVector
}