package nqfpruleminer

import nqfpruleminer.Value.Label

class Distribution(val numberOfTargetGroups: Int) {
  private val distr = Array.fill(numberOfTargetGroups: Int)(0: Int)

  def reset(): Unit =
    for (i <- 0 until numberOfTargetGroups) distr(i) = 0

  def add(index: Int): Unit = distr(index) += 1
  def add(that: Distribution): Unit = for (i <- 0 until numberOfTargetGroups) distr(i) += that(i)

  def minus(label: Label): Unit = distr(label) -= 1

  def set(label: Label, number: Int): Unit = distr(label) += number
  def set(that: Distribution): Unit = for (i <- 0 until numberOfTargetGroups) distr(i) = that(i)

  def <(that: Distribution): Boolean = distr.drop(1).zip(that.distr.drop(1)).forall { case (x, y) => x < y }

  lazy val sum: Int = distr.sum

  def positives: Array[Int] = distr.drop(1)

  lazy val probability: Array[Double] = distr.map(_ / sum.toDouble)

  def toList: IndexedSeq[Int] = distr.toIndexedSeq

  override def toString: String = if (distr.isEmpty) "empty distribution" else s"(${distr.map(_.toString).reduce(_ + "," + _)})"
}

object Distribution {
  def apply()(implicit numberOfTargetGroups: Int) = new Distribution(numberOfTargetGroups)

  def apply(group: Int)(implicit numberOfTargetGroups: Int): Distribution = {
    val distribution = new Distribution(numberOfTargetGroups)
    distribution.distr(group) += 1
    distribution
  }

  def apply(groups: List[Int])(implicit numberOfTargetGroups: Int): Distribution = {
    val distribution = new Distribution(numberOfTargetGroups)
    for (group <- groups) distribution.distr(group) += 1
    distribution
  }

  def reduce(distributions: List[Distribution]): Distribution = {
    utils.fail("List of distributions is empty")
    val base = distributions.head
    distributions.drop(1).foreach(distr => base.add(distr))
    base
  }

  implicit def distribution2array(distr: Distribution): Array[Int] = distr.distr
}