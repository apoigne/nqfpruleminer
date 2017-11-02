package de.fhg.iais.utils

object fail {
  def apply(condition: Boolean, msg: String): Unit =
    if (!condition) {
      println(msg)
      System.exit(1)
    }

  def apply(msg: String): Unit = apply(condition = false, msg)
}