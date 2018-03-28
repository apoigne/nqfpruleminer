package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Value.Label
import de.fhg.iais.utils
import de.fhg.iais.utils.fail

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

  def sum: Int = distr.sum

  def positives: Array[Int] = distr.drop(1)

  def probability: Array[Double] = distr.map(_ / sum.toDouble)

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

case class Aggregation(aggregationOp: AggregationOp.Value, getHistory: () => List[Double])(implicit ctx: Context) {
  private val init =
    aggregationOp match {
      case AggregationOp.Sum => 0.0
      case AggregationOp.Max => Double.MinValue
      case AggregationOp.Min => Double.MaxValue
      case AggregationOp.Mean => 0.0
    }
  private val value = Array.fill(ctx.numberOfTargetGroups)(init)
  private val count = Array.fill(ctx.numberOfTargetGroups)(0)

  val plus: (Numeric, Label) => Unit =
    aggregationOp match {
      case AggregationOp.Sum => (x: Numeric, label: Label) => value(label) += x.value; count(label) += 1
      case AggregationOp.Max => (x: Numeric, label: Label) => value(label) = math.max(value(label), x.value); count(label) += 1
      case AggregationOp.Min => (x: Numeric, label: Label) => value(label) = math.min(value(label), x.value); count(label) += 1
      case AggregationOp.Mean => (x: Numeric, label: Label) => value(label) += x.value; count(label) += 1
    }

  val minus: (Numeric, Label) => Unit =
    aggregationOp match {
      case AggregationOp.Sum =>
        (x: Numeric, label: Label) => count(label) -= 1; value(label) -= x.value
      case AggregationOp.Max =>
        (x: Numeric, label: Label) => {
          count(label) -= 1
          if (x.value == value(label) && count(label) != 0) value(label) = getHistory().max
        }
      case AggregationOp.Min =>
        (x: Numeric, label: Label) => {
          count(label) -= 1
          if (x.value > value(label) && count(label) != 0) value(label) = getHistory().min
        };
      case AggregationOp.Mean =>
        (x: Numeric, label: Label) => count(label) -= 1; value(label) -= x.value
    }

  def sum(): Int = count.sum

  def get: (Label) => Double =
    aggregationOp match {
      case AggregationOp.Mean => (label: Label) => value(label) / count(label)
      case _ => (label: Label) => value(label)
    }

  override def toString: String = s"counter ${count.toList} value ${value.toList}"
}