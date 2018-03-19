package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Expression.BoolExpr
import de.fhg.iais.nqfpruleminer.Item.Position
import de.fhg.iais.nqfpruleminer.Value.Label
import de.fhg.iais.utils.TimeFrame.TimeFrame
import de.fhg.iais.utils._
import org.joda.time.DateTime

import scala.util.{Failure, Success, Try}

case class DataFrame(label: Label, baseItems: Vector[Valued], derivedItems: Vector[Item])
case class TimedDataFrame(label: Label, timestamp: DateTime, baseItems : Vector[Valued], derivedItems: Vector[Item])

trait DataType

trait SimpleType extends DataType

object SimpleType {
  case object BOOLEAN extends SimpleType
  case object NUMERIC extends SimpleType
  case object NOMINAL extends SimpleType
  case object DATE extends SimpleType

  def apply(typ: String): SimpleType =
    typ match {
      case "Boolean" => SimpleType.BOOLEAN
      case "Numeric" => SimpleType.NUMERIC
      case "Nominal" => SimpleType.NOMINAL
      case "Date" => SimpleType.DATE
      case t => fail(s"Data type '$t' not supported"); null
    }
}

sealed trait BinningType extends DataType

object BinningType {
  //  case object NOBINNING extends BinningType
  case class ENTROPY(bins: Int) extends BinningType
  case class INTERVAL(delimiters: List[Double]) extends BinningType
  case class EQUALWIDTH(bins: Int) extends BinningType
  case class EQUALFREQUENCY(bins: Int) extends BinningType
}

sealed trait DerivedType extends DataType
sealed trait AggregatorType extends DerivedType {
  val seqIdPos: Option[Int]
  val history: Either[Int, TimeFrame]
}

object DerivedType {
  case class PREFIX(number: Int, position: Position) extends DerivedType
  case class RANGE(lo: Double, hi: Double, position: Position) extends DerivedType
  case class GROUP(positions: List[Int]) extends DerivedType
  case class AGGREGATE(seqIdPos: Option[Int], position: Int, op: AggregationOp.Value, existsOnly: Boolean,
                       condition: Numeric => Boolean, binning: Option[BinningType], history: Either[Int, TimeFrame]
                      ) extends AggregatorType
  case class COUNT(seqIdPos: Option[Int], positions: List[Int], existsOnly: Boolean,
                   condition: Numeric => Boolean, binning: Option[BinningType], history: Either[Int, TimeFrame]
                  ) extends AggregatorType
}

case class Feature(name: String, typ: DataType, condition: BoolExpr, position: Position = -1)

object Value {
  type Label = Int

  def apply(typ: DataType, value: String, label: Label = 0)(implicit ctx: Context): Option[Value] =
    tryOption(
      typ match {
        case SimpleType.BOOLEAN => Logical(value.toBoolean)
        case SimpleType.NUMERIC => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case SimpleType.NOMINAL => Nominal(value)
        case SimpleType.DATE => Date(ctx.parseDateTime(value))
        case BinningType.ENTROPY(_) => if (value.isEmpty) Labelled(Double.NaN, label) else Labelled(value.toDouble, label)
        case BinningType.INTERVAL(_) => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case BinningType.EQUALWIDTH(_) => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case BinningType.EQUALFREQUENCY(_) => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case _ => fail(s"type $typ is not supported"); null
      }
    )
//      .map(v => Valued(v, position))

  def apply(value: String)(implicit ctx: Context): Value =
    Try(value.toDouble) match {
      case Success(v) => Numeric(v)
      case Failure(_) =>
        Try(value.toBoolean) match {
          case Success(v) => Logical(v)
          case Failure(_) =>
            Try(DateTime.parse(value, ctx.dateTimeFormatter)) match {
              case Success(v) => Date(v)
              case Failure(_) =>
                Nominal(value)
            }
        }
    }
}

sealed trait Value extends Ordered[Value] {
  def compare(that: Value): Int =
    (this, that) match {
      case (Nominal(v1), Nominal(v2)) => v1.compare(v2)
      case (Logical(v1), Logical(v2)) => v1.compare(v2)
      case (Numeric(v1), Numeric(v2)) => v1.compare(v2)
      case (Date(v1), Date(v2)) => v1.getMillis.compare(v2.getMillis)
      case (Labelled(v1, _), Labelled(v2, _)) => v1.compare(v2)
      case (Bin(lo1, hi1), Bin(lo2, hi2)) =>
        if (lo1 == lo2 && hi1 == hi2) 0
        else if (lo1 >= lo2 && hi1 <= hi2) -1
        else if (lo1 <= lo2 && hi1 >= hi2) 1
        else {fail(s"Cannot compare bins ${Bin(lo1, hi1)} and ${Bin(lo2, hi2)}."); 0}
    }
}

case class Nominal(value: String) extends Value {
  override def toString: String = value
}
case class Logical(value: Boolean) extends Value {
  override def toString: String = value.toString
}
case class Numeric(value: Double) extends Value {
  def toBin(bins: List[Bin], distribution: Distribution): List[Value] = bins.filter(r => r.lo < value && value <= r.hi)
  override def toString: String = value.toString
}

case class Date(value: DateTime) extends Value
case class Labelled(value: Double, label: Label) extends Value {
  override def toString: String = s"($value, $label)"
}
case class Bin(lo: Double, hi: Double) extends Value {
  assert(lo < hi)
}

object Item {
  type Position = Int

  def toNumeric(item: Item): Numeric =
    Try(item.asInstanceOf[Valued].value.asInstanceOf[Numeric]) match {
      case Success(v) => v
      case Failure(e) => fail(s"Item $item: ${e.getLocalizedMessage}"); null
    }
}
sealed trait Item {
  val position: Position
//  def updatePosition(position: Position): Item
}

case class Valued(value: Value, position: Position)(implicit ctx: Context) extends Item {
//  def updatePosition(position: Position): Item = Valued(value, position)
  override def toString: String =
    if (position < 0)
      "position -1"
    else
      value match {
        case Bin(lo, hi) => f"$lo%1.3f <= ${ctx.allFeatures(position).name} < $hi%1.3f"
        case Date(_value) => s"${ctx.allFeatures(position).name} == ${ctx.dateTimeFormatter.print(_value)}"
        case _ => s"${ctx.allFeatures(position).name} == $value"
      }
}
case class Group(items: List[Item], position: Position = -1) extends Item {
//  def updatePosition(position: Position): Item = Group(items, position)
  override def toString: String = if (items.isEmpty) "" else items.map(_.toString).reduce(_ + " && " + _)
}

case class GroupBy(seqId: Option[Item], item: Item,  label : Value.Label, position: Position) extends Item {
//  def updatePosition(position: Position): Item = GroupBy(seqId, item, position)
//  override def toString: String =
//    (seqId, item) match {
//      case (None, _item) => _item.toString
//      case (Some(_seqId), Group(Nil, _)) => s"${_seqId}"
//      case (Some(_seqId), _item) => s"${_seqId} && ${_item}"
//    }
}

case class Counted(value: Item, result: Option[Int], label: Label, position: Position = -1)
                  (implicit ctx: Context) extends Item {
//  def updatePosition(position: Position): Counted = Counted(value, result, label, position)
//  override def toString: String =
//    result match {
//      case None =>
//        s"${ctx.allFeatures(position).name}.exists(${value.toString})" +
//          s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
//      case Some(r) =>
//        s"${ctx.allFeatures(position).name}.count(${value.toString}) == $r" +
//          s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
//    }
}

case class Aggregated(op: AggregationOp.Value, valuePos: Position, result: Option[Double], label: Label, position: Position = -1)
                     (implicit ctx: Context) extends Item {
//  def updatePosition(position: Position): Item = Aggregated(op, valuePos, result, label, position)
//  override def toString: String =
//    result match {
//      case None =>
//        s"${ctx.allFeatures(position).name}.exists(${ctx.allFeatures(position)})" +
//          s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
//      case Some(r) =>
//        s"${ctx.allFeatures(position).name}.$op(${ctx.allFeatures(valuePos).name}) == $r" +
//          s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
//    }
}
