package nqfpruleminer

import nqfpruleminer.Expression.{BoolExpr, TRUE}
import nqfpruleminer.Item.Position
import nqfpruleminer.Value.Label
import nqfpruleminer.utils.fail
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter

import scala.util.{Failure, Success, Try}

case class TimeFrame(attribute: String, dateTimeFormatter: DateTimeFormatter, start: Option[DateTime] = None, stop: Option[DateTime] = None) {
  def parseDateTime(value: String): DateTime =
    Try(dateTimeFormatter.parseDateTime(value)) match {
      case Success(v) => v
      case Failure(e) => fail(s"Parsing datetime failed: ${e.getMessage}"); null
    }
}

case class DataFrame(label: Label, simpleItems: Vector[Valued], derivedItems: Vector[Item])
case class TimedDataFrame(label: Label, timestamp: DateTime, simpleItems: Vector[Valued], derivedItems: Vector[Item])

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
  case object NOBINNING extends BinningType
  case class ENTROPY(bins: Int, overlapping: Boolean) extends BinningType
  case class INTERVAL(delimiters: List[Double], overlapping: Boolean) extends BinningType
  case class EQUALWIDTH(bins: Int, overlapping: Boolean) extends BinningType
  case class EQUALFREQUENCY(bins: Int, overlapping: Boolean) extends BinningType
}

sealed trait AggregatorType extends DataType {
  val seqIdPos: Option[Int]
  val history: Period
  val condition: Vector[Value] => Boolean
}

object DerivedType {
  case class PREFIX(number: Int, position: Position) extends DataType
  case class RANGE(lo: Double, hi: Double, position: Position) extends DataType
  case class COMPOUND(positions: List[Int]) extends DataType
  case class AGGREGATE(seqIdPos: Option[Int], position: Int, op: AggregationOperator.Value, minimum: Double,
                       condition: Vector[Value] => Boolean, binning: BinningType, history: Period
                      ) extends AggregatorType
  case class COUNT(seqIdPos: Option[Int], positions: List[Int], minimum: Int,
                   condition: Vector[Value] => Boolean, existsOnly: Boolean, history: Period
                  ) extends AggregatorType
}

case class Feature(name: String, typ: DataType, condition: BoolExpr = TRUE, position: Position = -1)

object Value {
  type Label = Int

  def apply(typ: DataType, value: String, label: Label = 0)(implicit ctx: Context): Value =
    Try(
      typ match {
        case SimpleType.BOOLEAN => Logical(value.toBoolean)
        case SimpleType.NUMERIC => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case SimpleType.NOMINAL => if (value.isEmpty) NoValue else Nominal(value)
        case SimpleType.DATE => ctx.timeframe match {
          case Some(tf) => Date(tf.parseDateTime(value))
          case None => fail("No DateTime pattern specified"); null
        }
        case BinningType.ENTROPY(_, _) => if (value.isEmpty) Numeric(Double.NaN, label) else Numeric(value.toDouble, label)
        case BinningType.INTERVAL(_, _) => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case BinningType.EQUALWIDTH(_, _) => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case BinningType.EQUALFREQUENCY(_, _) => if (value.isEmpty) Numeric(Double.NaN) else Numeric(value.toDouble)
        case _ => fail(s"type $typ is not supported"); null
      }
    ) match {
      case Success(v) => v
      case Failure(_) => NoValue
    }

  def apply(value: String)(implicit ctx: Context): Value =
    Try(value.toDouble) match {
      case Success(v) => Numeric(v)
      case Failure(_) =>
        Try(value.toBoolean) match {
          case Success(v) => Logical(v)
          case Failure(_) =>
            ctx.timeframe match {
              case Some(tf) =>
                Try(tf.parseDateTime(value)) match {
                  case Success(v) => Date(v)
                  case Failure(_) => Nominal(value)
                }
              case None =>
                fail("No date-time formatter specified."); null
            }

        }
    }
}

sealed trait Value extends Ordered[Value] {
  def compare(that: Value): Int =
    (this, that) match {
      case (NoValue, NoValue) => 1
      case (Nominal(v1), Nominal(v2)) => v1.compare(v2)
      case (Logical(v1), Logical(v2)) => v1.compare(v2)
      case (Numeric(v1, _), Numeric(v2, _)) => v1.compare(v2)
      case (Date(v1), Date(v2)) => v1.getMillis.compare(v2.getMillis)
      case (Bin(lo1, hi1), Bin(lo2, hi2)) =>
        if (lo1 == lo2 && hi1 == hi2) 0
        else if (lo1 >= lo2 && hi1 <= hi2) -1
        else if (lo1 <= lo2 && hi1 >= hi2) 1
        else {fail(s"Cannot compare bins ${Bin(lo1, hi1)} and ${Bin(lo2, hi2)}."); 0}
      case (x, y) => fail(s"Cannot compare $x and $y."); 0
    }

  val getLabelledDouble: Option[(Double, Label)]
  def toLiteral(name: String): String
}

case object NoValue extends Value {
  val getLabelledDouble: Option[(Double, Label)] = None
  def toLiteral(name: String): String = ""
}

case class Nominal(value: String) extends Value {
  val getLabelledDouble: Option[(Double, Label)] = None
  def toLiteral(name: String): String = s"$name == $value"
  override def toString: String = s""""$value""""
}
case class Logical(value: Boolean) extends Value {
  val getLabelledDouble: Option[(Double, Label)] = None
  def toLiteral(name: String): String = if (value) s"$name" else s"!$name"
//  override def toString: String = value.toString
}

case class Numeric(value: Double, label: Label = 0) extends Value {
  val getLabelledDouble: Option[(Double, Label)] = Some((value, -1))
  def toBin(bins: List[Bin]): List[Value] =
    if (bins.nonEmpty) bins.filter(r => r.lo < value && value <= r.hi) else List(this)
  def toLiteral(name: String): String = s"$name == $value"
  override def toString: String = value.toString
}

case class Date(value: DateTime) extends Value {
  val getLabelledDouble: Option[(Double, Label)] = None
  def toLiteral(name: String): String = s"$name == $value"
}

case class Bin(lo: Double, hi: Double) extends Value {
  assert(lo < hi)
  val getLabelledDouble: Option[(Double, Label)] = None
  def toLiteral(name: String): String =
    (lo, hi) match {
      case (Double.MinValue, Double.MaxValue) => fail("Only one bin"); null
      case (Double.MinValue, _hi) => s"$name < ${_hi}"
      case (_lo, Double.MaxValue) => s"${_lo} <= $name"
      case (_lo, _hi) => s"${_lo} <= $name  < ${_hi}"
    }
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
  val getLabelledDouble: Option[(Double, Label)]
}

case class Valued(value: Value, position: Position)(implicit ctx: Context) extends Item {
  val getLabelledDouble: Option[(Double, Label)] = value.getLabelledDouble
  override def toString: String =
    if (position < 0)
      "position -1"
    else
      value match {
        case Bin(lo, hi) => f"$lo%1.3f <= ${ctx.allFeatures(position).name} < $hi%1.3f"
        case Date(_value) => s"${ctx.allFeatures(position).name} == ${ctx.timeframe.get.dateTimeFormatter.print(_value)}"
        case _ => s"${ctx.allFeatures(position).name} == $value"
      }
}
case class Compound(items: List[Item], position: Position = -1) extends Item {
  val getLabelledDouble: Option[(Double, Label)] = None
  override def toString: String =
    if (items.isEmpty) "" else items.map(_.toString).reduce(_ + " && " + _)
}

case class GroupBy(seqId: Option[Item], item: Item, position: Position) extends Item {
  val getLabelledDouble: Option[(Double, Label)] = item.getLabelledDouble
  override def toString: String =
    (seqId, item) match {
      case (None, _item) => _item.toString
      case (Some(_seqId), Compound(Nil, _)) => s"${_seqId}"
      case (Some(_seqId), _item) => s"${_seqId} && ${_item}"
    }
}

case class Counted(item: Item, result: Int, position: Position = -1)(implicit ctx: Context) extends Item {
  val getLabelledDouble: Option[(Double, Label)] = None
  override def toString: String =
    s"${ctx.allFeatures(position).name}.count(${item.toString}) == $result"
}

case class Aggregated(op: AggregationOperator.Value, valuePos: Position, result: Double, position: Position = -1)
                     (implicit ctx: Context) extends Item {
  val getLabelledDouble: Option[(Double, Label)] = None
  override def toString: String =
    val allFeaturePositionName = ctx.allFeatures(position).name
    val allFeatureValuePosName = ctx.allFeatures(valuePos).name
    s"$allFeaturePositionName.$op($allFeatureValuePosName) == $result"
}
case class Existed(item: Item, position: Position = -1)(implicit ctx: Context) extends Item {
  val getLabelledDouble: Option[(Double, Label)] = None
  override def toString: String =
    s"${ctx.allFeatures(position).name}.exists(${item.toString})"
}