package de.fhg.iais.nqfpruleminer

class Coding(item2frequency: Map[Item, Int])(implicit ctx: Context) {

  val (decodingTable, codingTable) =
    if (ctx.hasNumericValues) {
      val name2ranges: Map[Attribute, List[Range]] =
        item2frequency
          .groupBy(_._1.attribute)                                                           // Map[Attribute, Map[Item, Int]]
          .mapValues(_.map { case (Item(_, value), number) => value -> number })             // Map[Attribute, Map[Value, Int]]
          .map { case (attribute, frequency) => attribute -> binning(attribute, frequency.toList) }

      val items = item2frequency.keys

      val item2binnedItem =
        items.map(
          item => {
            val binnedValue =
              item.value match {
                case value: Numeric => value.toBin(name2ranges(item.attribute))
                case LNumeric(v, _) => Numeric(v).toBin(name2ranges(item.attribute))
                case _ => item.value
              }
            item -> Item(item.attribute, binnedValue)
          }
        ).toMap

      val binnedItem2frequency =
        item2frequency
          .map { case (item, number) => item2binnedItem(item) -> number }
          .toList
          .groupBy(_._1)
          .mapValues(_.map(_._2).sum)

      val sortedItemsFrequencies: List[Item] = binnedItem2frequency.toList.sortWith((x0, x1) => x0._2 > x1._2).map(_._1)
      val decodingTable: IndexedSeq[Item] = sortedItemsFrequencies.toIndexedSeq

      val codingTable = items.map(item => item -> binnedItem2frequency(item2binnedItem(item))).toMap
      (decodingTable, codingTable)
    } else {
      val sortedItemsFrequencies: List[Item] = item2frequency.toList.sortWith((x0, x1) => x0._2 > x1._2).map(_._1)
      val decodingTable = sortedItemsFrequencies.toIndexedSeq
      val codingTable = sortedItemsFrequencies.zipWithIndex.toMap
      (decodingTable, codingTable)
    }

  val numberOfItems: Int = decodingTable.length

  private def binning(attribute: Attribute, frequencies: List[(Value, Int)]): List[Range] = {
    ctx.binning(attribute) match {
      case NoBinning =>
        Nil
      case binning: Intervals =>
        val tbl = frequencies.flatMap { case (Numeric(v), number) => List(v -> number); case _ => Nil }
        binning.genBins(tbl)
      case binning: EqualWidth =>
        val tbl = frequencies.flatMap { case (Numeric(v), number) => List(v -> number); case _ => Nil }
        binning.genBins(tbl)
      case binning: EqualFrequency =>
        val tbl = frequencies.flatMap { case (Numeric(v), number) => List(v -> number); case _ => Nil }
        binning.genBins(tbl)
      case binning: Entropy =>
        val tbl = frequencies.flatMap { case (LNumeric(v, l), number) => List((v, l) -> number); case _ => Nil }
        binning.genBins(tbl)
    }
  }

  def encode(item : Item) = codingTable(item)
}