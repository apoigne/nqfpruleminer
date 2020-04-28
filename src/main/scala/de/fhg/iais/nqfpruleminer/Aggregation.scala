package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Value.Label

case class Aggregation(aggregationOp: AggregationOperator.Value, getHistory: () => List[Double])(implicit ctx: Context) {
  private val init =
    aggregationOp match {
      case AggregationOperator.`sum` => 0.0
      case AggregationOperator.`max` => Double.MinValue
      case AggregationOperator.`min` => Double.MaxValue
      case AggregationOperator.`mean` => 0.0
    }
  private val value = Array.fill(ctx.numberOfTargetGroups)(init)
  private val count = Array.fill(ctx.numberOfTargetGroups)(0)

  val plus: (Numeric, Label) => Unit =
    aggregationOp match {
      case AggregationOperator.`sum` => (x: Numeric, label: Label) => value(label) += x.value; count(label) += 1
      case AggregationOperator.`max` => (x: Numeric, label: Label) => value(label) = math.max(value(label), x.value); count(label) += 1
      case AggregationOperator.`min` => (x: Numeric, label: Label) => value(label) = math.min(value(label), x.value); count(label) += 1
      case AggregationOperator.`mean` => (x: Numeric, label: Label) => value(label) += x.value; count(label) += 1
    }

  val minus: (Numeric, Label) => Unit =
    aggregationOp match {
      case AggregationOperator.`sum` =>
        (x: Numeric, label: Label) => count(label) -= 1; value(label) -= x.value
      case AggregationOperator.`max` =>
        (x: Numeric, label: Label) => {
          count(label) -= 1
          if (x.value == value(label) && count(label) != 0) value(label) = getHistory().max
        }
      case AggregationOperator.`min` =>
        (x: Numeric, label: Label) => {
          count(label) -= 1
          if (x.value > value(label) && count(label) != 0) value(label) = getHistory().min
        };
      case AggregationOperator.`mean` =>
        (x: Numeric, label: Label) => count(label) -= 1; value(label) -= x.value
    }

  def sum(): Int = count.sum

  def get: Label => Double =
    aggregationOp match {
      case AggregationOperator.`mean` => (label: Label) => value(label) / count(label)
      case _ => (label: Label) => value(label)
    }

  override def toString: String = s"counter ${count.toList} value ${value.toList}"
}