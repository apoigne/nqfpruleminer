package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Value.Label

case class Aggregation(aggregationOp: AggregationOp.Value, getHistory: () => List[Double])(implicit ctx: Context) {
  private val init =
    aggregationOp match {
      case AggregationOp.`sum` => 0.0
      case AggregationOp.`max` => Double.MinValue
      case AggregationOp.`min` => Double.MaxValue
      case AggregationOp.`mean` => 0.0
    }
  private val value = Array.fill(ctx.numberOfTargetGroups)(init)
  private val count = Array.fill(ctx.numberOfTargetGroups)(0)

  val plus: (Numeric, Label) => Unit =
    aggregationOp match {
      case AggregationOp.`sum` => (x: Numeric, label: Label) => value(label) += x.value; count(label) += 1
      case AggregationOp.`max` => (x: Numeric, label: Label) => value(label) = math.max(value(label), x.value); count(label) += 1
      case AggregationOp.`min` => (x: Numeric, label: Label) => value(label) = math.min(value(label), x.value); count(label) += 1
      case AggregationOp.`mean` => (x: Numeric, label: Label) => value(label) += x.value; count(label) += 1
    }

  val minus: (Numeric, Label) => Unit =
    aggregationOp match {
      case AggregationOp.`sum` =>
        (x: Numeric, label: Label) => count(label) -= 1; value(label) -= x.value
      case AggregationOp.`max` =>
        (x: Numeric, label: Label) => {
          count(label) -= 1
          if (x.value == value(label) && count(label) != 0) value(label) = getHistory().max
        }
      case AggregationOp.`min` =>
        (x: Numeric, label: Label) => {
          count(label) -= 1
          if (x.value > value(label) && count(label) != 0) value(label) = getHistory().min
        };
      case AggregationOp.`mean` =>
        (x: Numeric, label: Label) => count(label) -= 1; value(label) -= x.value
    }

  def sum(): Int = count.sum

  def get: Label => Double =
    aggregationOp match {
      case AggregationOp.`mean` => (label: Label) => value(label) / count(label)
      case _ => (label: Label) => value(label)
    }

  override def toString: String = s"counter ${count.toList} value ${value.toList}"
}