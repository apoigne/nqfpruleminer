package de.fhg.iais.nqfpruleminer

object Comparator extends Enumeration {
  val GT, GE, LT, LE = Value
}

trait Rule {
  def apply(value: Value): Boolean
  // Just to avoid code duplication
  def apply(value: Int)(implicit ctx : Context): Boolean = apply(Numeric(value.toDouble, -1))
  def apply(value: Double)(implicit ctx : Context): Boolean = apply(Numeric(value, -1))
  def check(instance: List[Value]): Boolean = instance.exists(apply)
}

case object NoRule extends Rule {
  def apply(value: Value): Boolean = false
}

case class EqRule(arg: Value) extends Rule {
  def apply(value: Value): Boolean = arg == value
}

case class CompRule(op: Comparator.Value, arg: Double) extends Rule {
  def _apply: Numeric => Boolean =
    op match {
      case Comparator.GT => (value: Numeric) => value.value > arg
      case Comparator.GE =>
        (value: Numeric) =>
          value.value >= arg
      case Comparator.LT => (value: Numeric) => value.value < arg
      case Comparator.LE => (value: Numeric) => value.value >= arg
    }
  def apply(value: Value) = _apply(value.asInstanceOf[Numeric])
}

case class OrRule(rules: Rule*)(ctx: Context) extends Rule {
  def apply(value: Value): Boolean = rules.exists(_ (value))
}

case class AndRule(rules: Rule*)(ctx: Context) extends Rule {
  def apply(value: Value): Boolean = rules.forall(_ (value))
}