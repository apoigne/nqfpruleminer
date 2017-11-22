package de.fhg.iais.table

import de.fhg.iais.nqfpruleminer.Instance

import scala.collection.mutable

class ATable {
  private val c = mutable.ArrayBuffer[Array[Instance]]()
  val size = 100000
  def expand(): Unit = c.append(new Array[Instance](size))
  var last = 0

  def add(v: Instance): Unit = {
    try {
      c(last / size)(last % size) = v
      last += 1
    } catch {
      case _: IndexOutOfBoundsException =>
        expand()
        add(v)
    }
  }

  def foreach(f: Instance => Unit): Unit = c.foreach(a => a.foreach(inst => if (inst != null) f(inst) ))
}
