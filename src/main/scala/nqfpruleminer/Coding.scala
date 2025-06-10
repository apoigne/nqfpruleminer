package nqfpruleminer

import nqfpruleminer.Item.Position

object Coding {
  type DecodingTable = IndexedSeq[Item]
  type CodingTable = Map[Item, Int]
}

class Coding(item2frequency: Map[Item, Distribution])(implicit ctx: Context) {
  import Coding.*

  private def binning(item2frequency: Map[Item, Distribution])(implicit position: Position): List[Bin] = {
    val tbl = item2frequency.flatMap { case (item, distribution) => item.getLabelledDouble.map((_, distribution)) }
    ctx.binning(position) match {
      case NoBinning => Nil
      case binning: Intervals =>
        if (position < ctx.numberSimpleFeatures) { // intervals for simple features are computed before coding
          Nil
        } else {
          binning.genBins(tbl)
        }
      case binning: EqualWidth => binning.genBins(tbl)
      case binning: EqualFrequency => binning.genBins(tbl)
      case binning: Entropy => binning.genBins(tbl)
    }
  }

  private val _pos2bins =
    item2frequency
      .groupBy(_._1.position)  // Map[Int, Map[Item, Int]]

  private val pos2bins =
    _pos2bins
      .map { case (position, frequency) => position -> binning(frequency)(position) }

  val item2binnedItem: Map[Item, List[Item]] =
    item2frequency.map {
      case (item, _) =>
        val binnedItems: List[Item] =
          item match {
            case Valued(value: Numeric, position) =>
              value.toBin(pos2bins(position)).map(bin => Valued(bin, position))
            case _ =>
              List(item)
          }
        item -> binnedItems
    }

  private val sortedItems =
    if (ctx.hasFeaturesToBin) {
      item2frequency
        .flatMap { case (value, distribution) => item2binnedItem(value).map(item => item -> distribution) }
        .toList
        .groupBy(_._1)
        .view
        .mapValues(_.map(_._2).map(_.sum).sum)
        .toList
        .sortWith((x0, x1) => x0._2 > x1._2)
    } else {
      item2frequency
        .view.
        mapValues(_.sum)
        .toList
        .sortWith((x0, x1) => x0._2 > x1._2)
    }

  if (ctx.statistics.exists) ctx.statistics.delete()
  sortedItems.foreach(x => ctx.statistics.appendLine(s"${x._1}: ${x._2}"))

//    log.info(s"Statistics are in file '$ctx.statistics'")
  if (ctx.statisticsOnly) System.exit(0)

  private val decodingTable: DecodingTable = sortedItems.map(_._1).toIndexedSeq
  private val codingTable: CodingTable = decodingTable.zipWithIndex.toMap

  val numberOfItems: Int = decodingTable.length

  def encode: Item => Int = (item: Item) => codingTable(item)
  def decode: Int => String = (i: Int) => decodingTable(i).toString
  def toBin: Item => List[Item] = (item: Item) => item2binnedItem.getOrElse(item, Nil)
}