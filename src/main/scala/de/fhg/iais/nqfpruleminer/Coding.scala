package de.fhg.iais.nqfpruleminer

import better.files._
import de.fhg.iais.nqfpruleminer.Coding.CodingTable
import de.fhg.iais.nqfpruleminer.Item.Position

object Coding {
  type DecodingTable = IndexedSeq[Item]
  type CodingTable = Map[Item, Int]
}

class Coding(item2frequency: Map[Item, Distribution])(implicit ctx: Context) {
  import Coding.DecodingTable

  val (decodingTable, codingTable) =
    if (ctx.hasFeaturesToBin) {
      val pos2bins: Map[Position, List[Bin]] =
        item2frequency
          .groupBy(_._1.position)  // Map[Int, Map[Item, Int]]
          .map { case (position, frequency) => position -> binning(frequency)(position) }

      val item2binnedItem: Map[Item, Item] =
        item2frequency.flatMap {
          case (value, distribution) =>
            val binnedValues : List[Item] =
              value match {
                case Valued(value: Numeric, position) =>
                  value.toBin(pos2bins(position)).map(bin => Valued(bin, position))
                case Valued(Labelled(value: Double, _), position) =>
                  Numeric(value).toBin(pos2bins(position)).map(bin => Valued(bin, position))
                case _ =>
                  List(value)
              }
            binnedValues.map(v => value -> v)
        }

      val binnedItem2frequency =
        item2frequency
          .map { case (value, distribution) => item2binnedItem(value) -> distribution }
          .toList
          .groupBy(_._1)
          .mapValues(_.map(_._2).map(_.sum).sum)

      val sortedItems: List[Item] = binnedItem2frequency.toList.sortWith((x0, x1) => x0._2 > x1._2).map(_._1)
      val decodingTable: DecodingTable = sortedItems.toIndexedSeq

      val codingTable: CodingTable = item2frequency.keys.map(item => item -> binnedItem2frequency(item2binnedItem(item))).toMap
      (decodingTable, codingTable)
    } else {
      val sortedItems: List[Item] = item2frequency.toList.sortWith((x0, x1) => x0._2.sum > x1._2.sum).map(_._1)
      val decodingTable: DecodingTable = sortedItems.toIndexedSeq
      val codingTable: CodingTable = sortedItems.zipWithIndex.toMap
      (decodingTable, codingTable)
    }

//  val file1 = "frequency.txt".toFile
//  if (file1.exists) file1.delete()
//  item2frequency.toList.sortWith((x0, x1) => x0._2.sum > x1._2.sum).foreach(x => file1.appendLine(s"${x._1.toString}: ${x._2.sum}"))
//
//  val file = "coding.txt".toFile
//  if (file.exists) file.delete()
//  decodingTable.foreach(x => file.appendLine(x.toString))

  val numberOfItems: Int = decodingTable.length

  private def binning(frequencies: Map[Item, Distribution])(implicit position: Position): List[Bin] = {
    ctx.binning(position) match {
      case NoBinning => Nil
      case binning: Intervals =>
        Nil
//        val tbl = frequencies.flatMap { case (Valued(Numeric(v), _), distribution) => List(v -> distribution); case _ => Nil }
//        binning.genBins(tbl)
      case binning: EqualWidth =>
        val tbl = frequencies.flatMap { case (Valued(Numeric(v), _), distribution) => List(v -> distribution); case _ => Nil }
        binning.genBins(tbl)
      case binning: EqualFrequency =>
        val tbl = frequencies.flatMap { case (Valued(Numeric(v), _), distribution) => List(v -> distribution); case _ => Nil }
        binning.genBins(tbl)
      case binning: Entropy =>
        val tbl = frequencies.flatMap { case (Valued(Labelled(v, l), _), distribution) => List((v, l) -> distribution); case _ => Nil }
        binning.genBins(tbl)
    }
  }

  def encode(value: Item): Option[Int] = codingTable get value
}