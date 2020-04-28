package de.fhg.iais.nqfpruleminer

import de.fhg.iais.utils.fail

object Period {

//  type Length = Either[Int, Int]

  def apply(definition: String): Int = {
    val value = definition.take(definition.length - 1).toInt
    definition.last match {
      case 'w' => value * 604800
      case 'd' => value * 86400
      case 'h' => value * 3600
      case 'm' => value * 60
      case 's' => value
//      case 'n' => value
      case x => fail(s"Format '$x' is not supported for timeframes."); 0
    }
  }
}

trait Period {
  val asString : String
  val offset: Int
  val length: Int
}
case class TimePeriod(asString:String, offset: Int, length: Int) extends Period
case class DiscretePeriod(asString:String, offset: Int, length: Int) extends Period
