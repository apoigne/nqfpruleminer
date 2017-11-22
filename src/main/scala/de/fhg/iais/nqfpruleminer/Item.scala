package de.fhg.iais.nqfpruleminer

import de.fhg.iais.utils._
import org.joda.time.DateTime

case class Item(attribute: Attribute, value: Value) {
  override def toString: String =
    value match {
      case Range(lo, hi) => f"$lo%1.3f <= ${attribute.name} < $hi%1.3f"
      case value: Group => value.toString
      case _ => s"${attribute.name} == $value"
    }
}

case class Instance(label:Int, instance : Seq[Item])

trait DataType

object DataType {
  case object BOOLEAN extends DataType
  case object NUMERIC extends DataType
  case object LNUMERIC extends DataType
  case object NOMINAL extends DataType
  case object DATE extends DataType
  case class GROUP(names: List[String]) extends DataType

  def apply(typ: String, names: List[String] = Nil): DataType =
    typ match {
      case "Boolean" => DataType.BOOLEAN
      case "Numerical" => DataType.NUMERIC
      case "Nominal" => DataType.NOMINAL
      case "Date" => DataType.DATE
      case "Group" => DataType.GROUP(names)
      case t => fail(s"Data type '$t' not supported"); DataType.BOOLEAN
    }
}

case class Attribute(name: String, typ: DataType)

object Value {
  def apply(typ: DataType, value: String, label: Int)(implicit ctx: Context): Value = {
    typ match {
      case DataType.BOOLEAN => try {Logical(value.toBoolean)} catch {case _: Throwable => Null}
      case DataType.NUMERIC => try {val v = value.toDouble; if (v.isNaN) Null else Numeric(v)} catch {case _: Throwable => Null}
      case DataType.LNUMERIC => try {val v = value.toDouble; if (v.isNaN) Null else LNumeric(v, label)} catch {case _: Throwable => Null}
      case DataType.NOMINAL => if (value.isEmpty) Null else Nominal(value)
      case DataType.DATE => try {Date(ctx.dateTimeFormatter.parseDateTime(value))} catch {case _: Throwable => Null}
      case DataType.GROUP(_) => fail("Wrong mapping of GROUP type"); Null
    }
  }
}

sealed trait Value

case object Null extends Value {
  override def toString: String = "null"
}

case class Nominal(value: String) extends Value {
  override def toString: String = value
}

case class Logical(value: Boolean) extends Value {
  override def toString: String = value.toString
}

case class Numeric(value: Double) extends Value {
  override def toString: String = value.toString

  def toBin(ranges: List[Range]): Value = {
    ranges.find(r => r.lo < value && value <= r.hi) match {
      case None => this
      case Some(r) => r
    }
  }
}

case class LNumeric(value: Double, label: Int) extends Value {
  override def toString: String = s"($value, $label)"
}

case class Date(value: DateTime)(implicit ctx: Context) extends Value {
  override def toString: String = ctx.dateTimeFormatter.print(value)
}

case class Range(lo: Double, hi: Double) extends Value {
  override def toString: String = ""
}

case class Group(names: List[String], value: List[String]) extends Value {
  override def toString: String =
    names.zip(value).map { case (n, v) => s"$n == $v" }.reduce(_ + " & " + _)
}
