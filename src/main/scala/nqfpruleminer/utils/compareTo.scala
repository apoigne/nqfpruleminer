package nqfpruleminer.utils



//package de.fhg.iais.utils
//
//import org.joda.time.DateTime
//
//object compareTo {
//  def apply[T <: Comparable[T]](comparator: String) =
//    comparator match {
//      case "eq" => (x: T, y: T) => x.compareTo(y) == 0
//      case "ne" => (x: T, y: T) => x.compareTo(y) != 0
//      case "le" => (x: T, y: T) => x.compareTo(y) <= 0
//      case "ge" => (x: T, y: T) => x.compareTo(y) >= 0
//      case "lt" => (x: T, y: T) => x.compareTo(y) < 0
//      case "gt" => (x: T, y: T) => x.compareTo(y) > 0
//      case c => fail(s"Comparator $c not supported"); null
//    }
//
//  case class RichDouble(x: Double) extends Comparable[RichDouble] {
//    def compareTo(that: RichDouble): Int = x.compareTo(that.x)
//  }
//  private implicit def toRichDouble(x: Double): RichDouble = RichDouble(x)
//
//  case class RichBoolean(x: Boolean) extends Comparable[RichBoolean] {
//    override def compareTo(that: RichBoolean): Int = x.compareTo(that.x)
//  }
//  private implicit def toRichBoolean(x: Boolean): RichBoolean = RichBoolean(x)
//
//  case class RichDateTime(x: DateTime) extends Comparable[RichDateTime] {
//    override def compareTo(that: RichDateTime): Int = x.compareTo(that.x)
//  }
//  private implicit def toRichDateTime(x: DateTime): RichDateTime = RichDateTime(x)
//
//  def apply(comparator: String, y: Double)(x: Double): Boolean = apply[RichDouble](comparator)(x, y)
//  def apply(comparator: String, y: Boolean)(x: Boolean): Boolean = apply[RichBoolean](comparator)(x, y)
//  def apply(comparator: String, y: String)( x: String): Boolean = apply[String](comparator)(x, y)
//  def apply(comparator: String, y: DateTime)(x: DateTime): Boolean = apply[RichDateTime](comparator)(x, y)
//}