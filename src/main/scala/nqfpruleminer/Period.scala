package nqfpruleminer

import utils.fail

object Period {
  def apply(definition: String): Int = {
    val value = definition.take(definition.length - 1).toInt
    definition.last match {
      case 'w' => value * 604800000
      case 'd' => value * 86400000
      case 'h' => value * 3600000
      case 'm' => value * 60000
      case 's' => value * 1000
      case 'u' => value
//      case 'n' => value
      case x => fail(s"Format '$x' is not supported for timeframes."); 0
    }
  }
}

trait Period {
  val offset: Option[String]
  val length: String

  override def toString: String =
    if (length.last == 'n')
      if (offset.isEmpty || offset.get == "0") s"over $length instants" else s"over $length instants offset ${offset.get}"
    else if (offset.isEmpty || offset.get == "0") s"over $length" else s"over $length offset ${offset.get}"
  val getOffset: Int = offset.map(Period(_)).getOrElse(0)
  val getLength: Int = Period(length)
}

case class TimePeriod(offset: Option[String], length: String) extends Period
case class DiscretePeriod(offset: Option[String], length: String) extends Period

case class PeriodConfig(_offset: String, _length: String) {
  private def toValue(definition: String): Int = {
    val value = definition.take(definition.length - 1).toInt
    definition.last match {
      case 'w' => value * 604800000
      case 'd' => value * 86400000
      case 'h' => value * 3600000
      case 'm' => value * 60000
      case 's' => value * 1000
      case 'u' => value
      case 'n' => value
      case x => fail(s"Format '$x' is not supported for timeframes."); 0
    }
  }
  val isDiscrete : Boolean=  _length.last == 'n'

  val offset: Int = toValue(_offset)
  val length: Int = toValue(_length)
}