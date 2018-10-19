package de.fhg.iais.nqfpruleminer

import de.fhg.iais.utils.fail

object Period {

  type Length = Either[Int, Int]

  def apply(definition: String): Length = {
    val value = definition.take(definition.length - 1).toInt
    definition.last match {
      case 'w' => Right(value * 604800)
      case 'd' => Right(value * 86400)
      case 'h' => Right(value * 3600)
      case 'm' => Right(value * 60)
      case 's' => Right(value)
      case 'n' => Left(value)
      case x => fail(s"Format '$x' is not supported for timeframes."); Left(0)
    }
  }
}
