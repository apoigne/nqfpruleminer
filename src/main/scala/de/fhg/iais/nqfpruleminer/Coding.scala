package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Item.Position

object Coding {
  type DecodingTable = IndexedSeq[Item]
  type CodingTable = Map[Item, Int]
}

class Coding(item2frequency: Map[Item, Distribution])(implicit ctx: Context) {
  import Coding._

  val (decodingTable, codingTable) = {
    val sortedItems: List[Item] =
      if (ctx.hasFeaturesToBin) {
        val _pos2bins =
          item2frequency
            .groupBy(_._1.position)  // Map[Int, Map[Item, Int]]

        val pos2bins: Map[Position, List[Bin]] =
          _pos2bins
            .map { case (position, frequency) => position -> binning(frequency)(position) }

        val item2binnedItem: Map[Item, Item] =
          item2frequency.flatMap {
            case (item, distribution) =>
              val binnedItems: List[Item] =
                item match {
                  case Valued(value: Numeric, position) =>
                    value.toBin(pos2bins(position)).map(bin => Valued(bin, position))
                  case _ =>
                    List(item)
                }
              binnedItems.map(v => item -> v)
          }

        val binnedItem2frequency =
          item2frequency
            .map { case (value, distribution) => item2binnedItem(value) -> distribution }
            .toList
            .groupBy(_._1)
            .mapValues(_.map(_._2).map(_.sum).sum)

        binnedItem2frequency.toList.sortWith((x0, x1) => x0._2 > x1._2).map(_._1)
      } else {
        item2frequency.toList.sortWith((x0, x1) => x0._2.sum > x1._2.sum).map(_._1)
      }
    val decodingTable: DecodingTable = sortedItems.toIndexedSeq
    val codingTable: CodingTable = decodingTable.zipWithIndex.toMap
    (decodingTable, codingTable)
  }

  val numberOfItems: Int = decodingTable.length

  private def binning(item2frequency: Map[Item, Distribution])(implicit position: Position): List[Bin] = {
    val tbl = item2frequency.flatMap { case (item, distribution) => item.getLabelledDouble.map((_, distribution)) }
    ctx.binning(position) match {
      case NoBinning => Nil
      case binning: Intervals =>
        if (position < ctx.noSimpleFeatures) { // intervals for simple features are computed before coding
          Nil
        } else {
          binning.genBins(tbl)
        }
      case binning: EqualWidth => binning.genBins(tbl)
      case binning: EqualFrequency => binning.genBins(tbl)
      case binning: Entropy => binning.genBins(tbl)
    }
  }

  def encode(value: Item): Option[Int] = codingTable get value
}