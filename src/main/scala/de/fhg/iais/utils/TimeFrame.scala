package de.fhg.iais.utils

object TimeFrame {
  type TimeFrame = Int
  def apply(definition: String): TimeFrame = {
      val value = definition.take(definition.length - 1).toInt
      definition.last match {
        case 'w' => value * 604800
        case 'd' => value * 86400
        case 'h' => value * 3600
        case 'm' => value * 60
        case 's' => value
        case x => fail(s"Format '$x' is not supported for timeframes."); TimeFrame("")
      }
  }
}