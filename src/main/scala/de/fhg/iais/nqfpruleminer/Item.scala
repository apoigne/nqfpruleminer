package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Value.{Label, Position}
import de.fhg.iais.utils.TimeFrame.TimeFrame
import de.fhg.iais.utils._
import org.joda.time.DateTime

import scala.util.{Failure, Success, Try}

case class DataFrame(label: Label, instance: List[Value])
case class TimedDataFrame(label: Label, timestamp: DateTime, instance: List[Value])

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
      case "Integer" => SimpleType.NUMERIC
      case "Numerical" => SimpleType.NUMERIC
      case "Nominal" => SimpleType.NOMINAL
      case "Date" => SimpleType.DATE
      case t => fail(s"Data type '$t' not supported"); null
    }
}

sealed trait BinningType extends DataType

object BinningType {
  case object NOBINNING extends BinningType
  case class ENTROPY(bins: Int) extends BinningType
  case class INTERVAL(delimiters: List[Double]) extends BinningType
  case class EQUALWIDTH(bins: Int) extends BinningType
  case class EQUALFREQUENCY(bins: Int) extends BinningType
}

sealed trait DerivedType extends DataType
sealed trait AggregatorType extends DerivedType {
  val seqIdPos: Option[Int]
  val timeFrame: TimeFrame.TimeFrame
}

object DerivedType {
  case class GROUP(positions: List[Int], exclusive: Boolean = false) extends DerivedType
  case class AGGREGATE(seqIdPos: Option[Int], position: Int, op: AggregationOp.Value,
                       existsOnly: Boolean, condition: Rule, binning: BinningType, timeFrame: TimeFrame
                      ) extends AggregatorType
  case class COUNT(seqIdPos: Option[Int], positions: List[Int], existsOnly: Boolean,
                   condition: Rule, binning: BinningType, timeFrame: TimeFrame
                  ) extends AggregatorType
}

case class Feature(name: String, typ: DataType, position: Position = -1)

object Value {
  type Label = Int
  type Position = Int

  private def filterNaN(value: String, label: Option[Int], position: Int)(implicit ctx: Context): Value = {
    val v = value.toDouble
    label match {
      case None => Numeric(v, position)
      case Some(l) => Labelled(Numeric(v, position), l)
    }
  }

  def apply(typ: DataType, value: String, label: Label = 0, position: Position = -1)(implicit ctx: Context): Value = {
    typ match {
      case SimpleType.BOOLEAN => tryWithDefault(Logical(value.toBoolean, position), Null)
      case SimpleType.NUMERIC => tryWithDefault(filterNaN(value, None, position), Null)
      case SimpleType.NOMINAL => if (value.isEmpty) Null else Nominal(value, position)
      case SimpleType.DATE => Date(ctx.parseDateTime(value), position)
      case BinningType.ENTROPY(_) => tryWithDefault(filterNaN(value, Some(label), position), Null)
      case BinningType.INTERVAL(_) => tryWithDefault(filterNaN(value, None, position), Null)
      case BinningType.EQUALWIDTH(_) => tryWithDefault(filterNaN(value, None, position), Null)
      case BinningType.EQUALFREQUENCY(_) => tryWithDefault(filterNaN(value, None, position), Null)
      case _ => fail(s"type $typ is not supported"); null
    }
  }

  def apply(value: String)(implicit ctx: Context): Value =
    Try(value.toDouble) match {
      case Success(v) => Numeric(v, -1)
      case Failure(_) =>
        Try(value.toBoolean) match {
          case Success(v) => Logical(v, -1)
          case Failure(_) =>
            Try(DateTime.parse(value, ctx.dateTimeFormatter)) match {
              case Success(v) => Date(v, -1)
              case Failure(_) =>
                Try(value.toInt) match {
                  case Success(v) => Discrete(v, -1)
                  case Failure(_) => Nominal(value, -1)
                }
            }
        }
    }
}

sealed trait Value {
  val position: Int
  def updatePosition(position: Position): Value
}

case object Null extends Value {
  val position = 0
  def updatePosition(position: Position): Value = this
  override def toString: String = "null"
}

case class Nominal(value: String, position: Int)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Nominal(value, position)
//  override def toString: String = s"${ctx.featureAtPosition(position).name} == $value"

}

case class Logical(value: Boolean, position: Int)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Logical(value, position)
//  override def toString: String =
//    if (value) s"${ctx.featureAtPosition(position).name}" else s"!(${ctx.featureAtPosition(position).name})"
}

case class Numeric(value: Double, position: Int)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Numeric(value, position)
  def toBin(binRanges: List[BinRange], distribution: Distribution): List[Value] = {
    val ranges = binRanges.filter(r => r.lo < value && value <= r.hi)
//    ranges.foreach(_.add(distribution))
    ranges
  }
//  override def toString: String = s"${ctx.featureAtPosition(position).name} == $value"
}

case class Discrete(value: Int, position: Int)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Discrete(value, position)
//  override def toString: String = s"${ctx.featureAtPosition(position).name} == $value"
}

case class Date(value: DateTime, position: Int)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Date(value, position)
//  override def toString: String = s"${ctx.featureAtPosition(position).name} == ${ctx.dateTimeFormatter.print(value)}"
}

case class Labelled(value: Value, label: Label) extends Value {
  val position: Int = value.position
  def updatePosition(position: Position): Value = Labelled(value.updatePosition(position), label)
//  override def toString: String = s"($value, $label)"
}

//case class BinRange(lo: Double, hi: Double, distribution: Distribution, position: Int = -1)(implicit ctx: Context) extends Value {
//  assert(lo < hi)
//  def updatePosition(position: Position): Value = BinRange(lo, hi, distribution, position)
//  def add(distr: Distribution): Unit = distribution add distr
//  override def toString: String = f"$lo%1.3f <= ${ctx.featureAtPosition(position).name} < $hi%1.3f"
//}

case class BinRange(lo: Double, hi: Double, position: Int = -1)(implicit ctx: Context) extends Value {
  assert(lo < hi)
  def updatePosition(position: Position): Value = BinRange(lo, hi, position)
  override def toString: String = f"$lo%1.3f <= ${ctx.featureAtPosition(position).name} < $hi%1.3f"
}

case class Group(values: List[Value], position: Position = -1) extends Value {
  def updatePosition(position: Position): Value = Group(values, position)
//  override def toString: String = values.map(_.toString).reduce(_ + " & " + _)
}

case class GroupBy(seqId: Option[Value], value: Value, label: Label = -1, position: Position = -1) extends Value {
  def updatePosition(position: Position): Value = GroupBy(seqId, value, position)
//  override def toString: String =
//    (seqId, value) match {
//      case (None, _value) => _value.toString
//      case (Some(_seqId), _value) => s"${_seqId} & ${_value}"
//    }
}

//case class Exists(value: Either[Value, Position], label: Label, position: Position = -1)(implicit ctx: Context) extends Value {
//  def updatePosition(position: Position): Value = Exists(value, label, position)
//  override def toString: String =
//    s"${ctx.featureAtPosition(position).name}.exists(${value.toString})" +
//      s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
//}

case class Counted(value: Value, result: Option[Int], label: Label, position: Position = -1)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Counted(value, result, label, position)
//  override def toString: String =
//    s"${ctx.featureAtPosition(position).name}.count(${value.toString}) == $result" +
//      s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
}

case class Aggregated(op: AggregationOp.Value, valuePos: Position, result: Option[Double], label: Label, position: Position = -1)(implicit ctx: Context) extends Value {
  def updatePosition(position: Position): Value = Aggregated(op, valuePos, result, label, position)
//  override def toString: String =
//    s"${ctx.featureAtPosition(position).name}.$op(${ctx.featureAtPosition(positionValue)}) " +
//      s"== ${result match {case Left(v) => v.toString; case Right(v) => v.toString}}" +
//      s"${if (ctx.numberOfTargetGroups <= 2) "" else s" & label == ${ctx.targetGroups(label - 1)}"}"
}