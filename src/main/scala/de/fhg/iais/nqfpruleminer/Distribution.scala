package de.fhg.iais.nqfpruleminer

class Distribution(val numberOfTargetGroups : Int) {
  private val distr = Array.fill(numberOfTargetGroups: Int)(0: Int)

  def reset() : Unit =
    for (i <- 0 until numberOfTargetGroups) distr(i) = 0

  def add(index : Int) : Unit = distr(index) += 1

  def add(that: Distribution) : Unit = {
    require(numberOfTargetGroups == that.numberOfTargetGroups)
    for (i <- 0 until numberOfTargetGroups) distr(i) += that(i)
  }

  def set(that: Distribution) : Unit = {
    require(numberOfTargetGroups == that.numberOfTargetGroups)
    for (i <- 0 until numberOfTargetGroups) distr(i) = that(i)
  }

  def sum : Int = distr.sum

  def toList : IndexedSeq[Int] = distr.toIndexedSeq

  override def toString = s"(${distr.map(_.toString).reduce(_ + "," + _)})"
}

object Distribution {
  def apply()(implicit numberOfTargetGroups : Int) = new Distribution(numberOfTargetGroups)

  def apply( group: Int)(implicit numberOfTargetGroups : Int): Distribution = {
    val distribution = new Distribution(numberOfTargetGroups)
    distribution.distr(group) += 1
    distribution
  }

  def apply(groups: List[Int])(implicit numberOfTargetGroups : Int): Distribution = {
    val distribution = new Distribution(numberOfTargetGroups)
    for (group <- groups) distribution.distr(group) += 1
    distribution
  }

  implicit def distribution2array(distr: Distribution): Array[Int] = distr.distr
}