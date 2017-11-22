package de.fhg.iais.nqfpruleminer

trait Rule {
  def apply(item: Item): Boolean
  def check(instance: List[Item]): Boolean = instance.exists(apply)
}

case object NoRule extends Rule {
  def apply(item: Item): Boolean = false
}

case class ItemRule(name: String, value: Value) extends Rule {
  def apply(item: Item): Boolean =
    name == item.attribute.name &&
      ((value, item.value) match {
        case (Range(lo, hi), Numeric(v)) => lo <= v && v < hi
        case _ => value == item.value
      })
}

case class OrRule(rules: Rule*) extends Rule {
  def apply(item: Item): Boolean = rules.exists(_ (item))
}

case class AndRule(rules: Rule*) extends Rule {
  def apply(item: Item): Boolean = rules.forall(_ (item))
}

