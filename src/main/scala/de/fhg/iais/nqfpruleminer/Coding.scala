package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Coding.CodingTable
import de.fhg.iais.nqfpruleminer.Item.Position

import better.files._

object Coding {
  type DecodingTable = IndexedSeq[Item]
  type CodingTable = Map[Item, Int]
}

class Coding(value2frequency: Map[Item, Distribution])(implicit ctx: Context) {
  import Coding.DecodingTable

  val (decodingTable, codingTable) =
    if (ctx.hasFeaturesToBin) {
      val name2bins: Map[Int, List[Bin]] =
        value2frequency
          .groupBy(_._1.position)  // Map[Int, Map[Value, Int]]
          .map { case (position, frequency) => position -> binning(frequency)(position) }

      val item2binnedItem: Map[Item, Item] =
        value2frequency.flatMap {
          case (value, distribution) =>
            val binnedValues =
              value match {
                case Valued(value: Numeric, position) =>
                  value.toBin(name2bins(position), distribution).map(value => Valued(value, position))
                case Valued(Labelled(value: Double, _), position) =>
                  Numeric(value).toBin(name2bins(position), distribution).map(value => Valued(value, position))
                case _ => List(value)
              }
            binnedValues.map(v => value -> v)
        }

      val binnedItem2frequency =
        value2frequency
          .map { case (value, distribution) => item2binnedItem(value) -> distribution }
          .toList
          .groupBy(_._1)
          .mapValues(_.map(_._2).map(_.sum).sum)

      val sortedItemsFrequencies: List[Item] = binnedItem2frequency.toList.sortWith((x0, x1) => x0._2 > x1._2).map(_._1)
      val decodingTable: DecodingTable = sortedItemsFrequencies.toIndexedSeq

      val codingTable: CodingTable = value2frequency.keys.map(item => item -> binnedItem2frequency(item2binnedItem(item))).toMap
      (decodingTable, codingTable)
    } else {
      val sortedItemsFrequencies: List[Item] = value2frequency.toList.sortWith((x0, x1) => x0._2.sum > x1._2.sum).map(_._1)
      val decodingTable: DecodingTable = sortedItemsFrequencies.toIndexedSeq
      val codingTable: CodingTable = sortedItemsFrequencies.zipWithIndex.toMap
      (decodingTable, codingTable)
    }

    val file = "coding.txt".toFile
    codingTable.foreach(x => file.appendLine(x._1.toString))


  val numberOfItems: Int = decodingTable.length

  private def binning(frequencies: Map[Item, Distribution])(implicit position: Position): List[Bin] = {
    ctx.binning get position match {
      case None => Nil
//      case Some(NoBinning) => Nil
      case Some(binning: Intervals) =>
        val tbl = frequencies.flatMap { case (Valued(Numeric(v), _), distribution) => List(v -> distribution); case _ => Nil }
        binning.genBins(tbl)
      case Some(binning: EqualWidth) =>
        val tbl = frequencies.flatMap { case (Valued(Numeric(v), _), distribution) => List(v -> distribution); case _ => Nil }
        binning.genBins(tbl)
      case Some(binning: EqualFrequency) =>
        val tbl = frequencies.flatMap { case (Valued(Numeric(v), _), distribution) => List(v -> distribution); case _ => Nil }
        binning.genBins(tbl)
      case Some(binning: Entropy) =>
        val tbl = frequencies.flatMap { case (Valued(Labelled(v, l), _), distribution) => List((v, l) -> distribution); case _ => Nil }
        binning.genBins(tbl)
    }
  }

  def encode(value: Item) : Option[Int] = codingTable get value
}