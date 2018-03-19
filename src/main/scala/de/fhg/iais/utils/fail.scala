package de.fhg.iais.utils

import scala.util.{Failure, Success, Try}

object fail {
  def apply(condition: Boolean, msg: String): Unit =
    if (!condition) {
      println(msg)
      System.exit(1)
    }

  def apply(msg: String): Unit = apply(condition = false, msg)
}

object tryFail {
  def apply[T <: Any](x: => T): T =
    Try(x) match {
      case Success(v) => v
      case Failure(e) => fail(e.getMessage); null.asInstanceOf[T]
    }
}

object tryWithDefault {
  def apply[T <: Any](x: => T, default: T): T =
    Try(x) match {
      case Success(v) => v
      case Failure(_) => default
    }
}

object tryOption {
  def apply[T <: Any](x: => T): Option[T] =
    Try(x) match {
      case Success(v) => Some(v)
      case Failure(_) => None
    }
}