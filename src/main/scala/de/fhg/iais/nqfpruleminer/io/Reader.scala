package de.fhg.iais.nqfpruleminer.io

import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.utils.fail

class Reader(provider: Provider)(implicit ctx: Context) {
  def apply(ev: (Int, Seq[Item]) => Unit): Unit = {
    val columnsUsed = provider.columnsUsed
    val length = columnsUsed.length

    while (provider.hasNext) {
      val instance = provider.next
      assert(instance.length >= length, s"Number of instance values ${instance.length} is different to the number of attribute ${ctx.attributes.length}.")
      val label = ctx.targetGroups.indexOf(instance(provider.targetIndex)) + 1 // non target values have label 0

      val values = for ((attr, i) <- columnsUsed if ctx.attributesUsed.contains(attr)) yield Item(attr, Value(attr.typ, instance(i), label))

      if (!ctx.rule.check(values)) {
        val attributeNameToIndex = columnsUsed.map { case (attr, i) => (attr.name, i) }.toMap

        val groupedValues =
          ctx.groups.map {
            case attr@Attribute(name, DataType.GROUP(names)) =>
              Item(attr, Group(names, names.map(n => instance(attributeNameToIndex(n)))))
            case attr =>
              fail(""); Item(attr, Null)
          }

        val allValues = values ++ groupedValues

        ev(label, allValues)
      }
    }
  }
}

