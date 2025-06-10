package nqfpruleminer

import Item.Position
import Value.Label
import com.typesafe.scalalogging._
import utils.fail

import Ordering.Double.IeeeOrdering

object Discretization {
  def delimiters2bins(delimiters: List[Double], overlapping: Boolean = false): List[Bin] = {
    def delimiters2bins(delimiters: List[Double]): List[Bin] = {
      delimiters.distinct.sorted match {
        case x :: List(y) => List(Bin(x, y + 1.0E-10))
        case x :: y :: _delimiters => Bin(x, y) :: delimiters2bins(y :: _delimiters)
        case Nil => Nil
        case _ => throw new RuntimeException("Internal error: delimiters2ranges")
      }
    }

    val bins = delimiters2bins(delimiters)
    if (overlapping) makeOverlapping(bins) else bins
  }

  def makeOverlapping(ranges: List[Bin]): List[Bin] = {
    // Ranges should be disjoint
    ranges match {
      case Nil =>
        Nil
      case Bin(lo, _) :: _ranges =>
        ranges.map { case Bin(_, hi) => Bin(lo, hi) } ++ makeOverlapping(_ranges)
    }
  }
}

sealed trait Discretization {
  def genBins(freqs: Map[(Double, Label), Distribution])(implicit ctx: Context, position: Position): List[Bin]
}

case object NoBinning extends Discretization {
  def genBins(freqs: Map[(Double, Label), Distribution])(implicit ctx: Context, position: Position): List[Bin] = Nil
}

case class Intervals(delimiters: List[Double], overlapping: Boolean = false) extends Discretization {

  def genBins(freqs: Map[(Double, Label), Distribution])(implicit ctx: Context, position: Position): List[Bin] = {
    val values = for ((v, _) <- freqs.keys if !v.isNaN) yield v
    Discretization.delimiters2bins(values.min +: values.max +: delimiters, overlapping)
  }
}

case class EqualWidth(numberOfBins: Int, overlapping: Boolean = false) extends Discretization {
  def genBins(freqs: Map[(Double, Label), Distribution])(implicit ctx: Context, position: Position): List[Bin] = {
    val values = for ((v, _) <- freqs.keys if !v.isNaN) yield v
    val hi = values.max
    val lo = values.min
    fail(lo < hi,
      s"Attribute '${ctx.allFeatures(position).name}': min value '$lo' not distinct from max value '$hi'.")
    val interval = (hi - lo) / numberOfBins
    Discretization.delimiters2bins((0 to numberOfBins).map(i => lo + i * interval).toList, overlapping)
  }
}

case class EqualFrequency(numberOfBins: Int, overlapping: Boolean = false) extends Discretization {

  def genBins(freqs: Map[(Double, Label), Distribution])(implicit ctx: Context, position: Position): List[Bin] = {
    // Copy data so that it can be sorted
    val delimiters = new Array[Double](numberOfBins - 1)
    val data = for (((v, _), distr) <- freqs.toList if !v.isNaN) yield (v, distr)
    val values = data.map(_._1).distinct
    fail(values.lengthCompare(numberOfBins) >= 0, s"Attribute '${ctx.allFeatures(position).name}' has less values than bins")
    var sumOfWeights = data.map(_._2.sum).sum
    var fraction = sumOfWeights / numberOfBins
    var counter = 0
    var index = 0
    val sorted = data.sortBy(_._1)
    for (i <- 0 until sorted.length - 1) { // Stop if value missing
      val weight = sorted(i)._2.sum
      val counterPlusWeight = counter + weight
      sumOfWeights -= weight
      if (counterPlusWeight >= fraction) {
        delimiters(index) = (sorted(i - 1)._1 + sorted(i)._1) / 2
        counter = if (((fraction - counter) * 2 < weight) && (counter != 0)) weight else 0
        index += 1
        fraction = (sumOfWeights + counter) / (numberOfBins - index)
      } else {
        counter = counterPlusWeight
      }
    }
//    fail(index == numberOfBins - 1, s"$index != $numberOfBins")
    Discretization.delimiters2bins(values.min +: values.max +: delimiters.toList, overlapping)
  }
}

case class Entropy(numberOfBins: Int, overlapping: Boolean = false) extends Discretization {
  private def log2(x: Double) = math.log(x) / math.log(2.0)
  private val log = Logger("Entropy")

  def genBins(freqs: Map[(Double, Label), Distribution])(implicit ctx: Context, position: Position): List[Bin] = {
    val data = freqs.filterNot(_._1._1.isNaN)
    val values = data.keys.map(_._1).toList.distinct
    fail(values.lengthCompare(numberOfBins) > 0, s"Attribute '${ctx.allFeatures(position).name}' has less values than bins")
    val delimiters = partition(EBin(data), numberOfBins - 1)
    if (delimiters.lengthCompare(numberOfBins - 1) == 0)
      log.info(s"Entropy binning for '${ctx.allFeatures(position).name}' generates only '${delimiters.length + 1}' bins, specified are '$numberOfBins' bins")
    Discretization.delimiters2bins(values.min +: values.max +: delimiters, overlapping)
  }

  private def partition(bin: EBin, depth: Int) = recursiveSplit(Nil, bin, depth).sorted

  private def recursiveSplit(delimiters: List[Double], data: EBin, depth: Int): List[Double] =
    if (depth <= 0)
      delimiters
    else
      split(data) match {
        case None => delimiters
        case Some((value, _, binLo, binH)) =>
          val delimiters1 = value :: delimiters
          val delimiters2 = recursiveSplit(delimiters1, binLo, depth - delimiters1.length)
          recursiveSplit(delimiters2, binH, depth - delimiters2.length)
      }

  private case class EBin(freqs: Map[(Double, Label), Distribution]) {
    private val data = freqs.view.mapValues(_.sum)
    val values: List[Double] = data.keys.map(_._1).toList.distinct
    val size: Double = data.values.sum.toDouble
    val noTargets: Double = Set(freqs.keys.map(_._2)).size.toDouble
    val entropy: Double =
      -freqs
        .groupBy(_._1._2)
        .values
        .map(_.values.map(_.sum).sum)
        .map(count => {val p = count.toDouble / size; p * log2(p)})
        .sum
  }

  private def split(bin: EBin): Option[(Double, Double, EBin, EBin)] = {
    val values = bin.values.sorted
    val candidates = for (i <- 0 until values.length - 1) yield (values(i) + values(i + 1)) / 2.0
    val delimiters =
      candidates
        .map(
          value => {
            val (lo, hi) = bin.freqs.partition(_._1._1 <= value)
            val binLo = EBin(lo)
            val binHi = EBin(hi)
            val gain = entropyGain(bin, EBin(lo), EBin(hi))
            (value, gain, binLo, binHi)
          })
        .filter {
          case (_, gain, binLo, binHi) =>
            binLo.size > 0 && binHi.size > 0 && (gain >= minGain(bin, binLo, binHi))
        }

    if (delimiters.isEmpty) None else Some(delimiters.maxBy(_._2))
  }

  private def entropyGain(original: EBin, left: EBin, right: EBin): Double =
    original.entropy - ((left.size / original.size) * left.entropy + (right.size / original.size) * right.entropy)

  private def minGain(original: EBin, left: EBin, right: EBin) = {
    val diff = original.noTargets * original.entropy - left.noTargets * left.entropy - right.noTargets * right.entropy
    val delta = log2(math.pow(3.0, original.noTargets - 2)) - diff.toDouble
    (delta + log2(original.size - 1.0)) / original.size
  }
}