package datapreparation

import common.utils.fail

sealed trait Value extends Ordered[Value] {
  def compare(that: Value) : Int= {
    (this, that) match {
      case (Numeric(n1), Numeric(n2)) => if (n1 < n2) -1 else if (n1 > n2) 1 else 0
      case (Nominal(s1), Nominal(s2)) => if (s1 < s2) -1 else if (s1 > s2) 1 else 0
      case (Null, Null) => 0
      case (Range(low1, up1), Range(low2, up2)) =>
        if (low1 <= low2 && up1 < up2 || low1 < low2) -1
        else if (low1 == low2 && up1 == up2) 0
        else 1
      case (v1, v2) =>
        fail(s"The values $v1 and $v2 are not comparable");0
    }
  }

  override def toString : String= this match {
    case Null => ""
    case Nominal(v) => v
    case Numeric(v) => v.toString
    case Date(v) => v
    case Range(lo, hi) => s"($lo,$hi)"
  }
}

case object Null extends Value
case class Nominal(nominal: String) extends Value
case class Numeric(numeric: Double) extends Value
case class Date(date: String) extends Value
case class Range(lo: Double, hi: Double) extends Value

