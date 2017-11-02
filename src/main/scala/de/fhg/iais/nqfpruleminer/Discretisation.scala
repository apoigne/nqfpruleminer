package de.fhg.iais.nqfpruleminer

import de.fhg.iais.utils.fail

object  Discretisation {
  def delimiters2bins(delimiters: List[Double]): List[Range] = {
    delimiters.distinct.sorted match {
      case x :: List(y) => List(Range(x, y + 0.0001))    // to include the maximal value
      case x :: y :: _delimiters => Range(x, y) :: delimiters2bins(y :: _delimiters)
      case Nil => Nil
      case _ => throw new RuntimeException("Internal error: delimiters2ranges")
    }
  }
}

trait Discretisation

case object NoBinning extends Discretisation

case class Intervals(delimiters: List[Double]) extends Discretisation {
  import Discretisation._

  def genBins(freqs: List[(Double, Int)]): List[Range] = {
    val values = for ((x, _) <- freqs if !x.isNaN) yield x
    println(s"values $values")
    val hi = values.max
    val lo = values.min
    delimiters2bins(lo :: hi :: delimiters)
  }
}

case class EqualWidth(attributeName: String, numberOfBins: Int) extends Discretisation {
  import Discretisation._

  def genBins(freqs: List[(Double, Int)]): List[Range] = {
    val values = for ((x, _) <- freqs if !x.isNaN) yield x
    fail(values.length > numberOfBins, s"Attribute $attributeName has less values than bins")
    val hi = values.max
    val lo = values.min
    val interval = (hi - lo) / numberOfBins
    delimiters2bins(hi +: (0 until numberOfBins - 1).map(i => lo + i * interval).toList)
  }
}

case class EqualFrequency(attributeName: String, numberOfBins: Int) extends Discretisation {
  import Discretisation._

  def genBins(freqs: List[(Double, Int)]): List[Range] = {
    // Copy data so that it can be sorted
    val delimiters = new Array[Double](numberOfBins - 1)
    val data = freqs.filterNot(_._1.isNaN).sortBy(_._1).toArray
    val values = data.map(_._1).distinct
    fail(values.length > numberOfBins, s"Attribute $attributeName has less values than bins")

    var sumOfWeights = data.map(_._2).sum
    var fraction = sumOfWeights / numberOfBins
    var counter = 0
    var index = 0

    for (i <- 0 until data.length - 1) { // Stop if value missing
      val weight = data(i)._2
      val counterPlusWeight = counter + weight
      sumOfWeights -= weight
      if (counterPlusWeight >= fraction) {
        // Is this break point worse than the last one?
        if (((fraction - counter) < weight / 2) && (counter != 0)) {
          delimiters(index) = (data(i - 1)._1 + data(i)._1) / 2
          counter = weight
        } else {
          delimiters(index) = (data(i)._1 + data(i + 1)._1) / 2
          counter = 0
        }
        index += 1
        fraction = (sumOfWeights + counter) / (numberOfBins - index)
      } else {
        counter = counterPlusWeight
      }
    }
    delimiters2bins(values.min +: values.max +: delimiters.toList.take(index))
  }
}

case class Entropy(attributeName: String, numberOfBins: Int, numberOfTargets: Int) extends Discretisation {
  import Discretisation._

  def genBins(freqs: List[((Double, Int), Int)]): List[Range] = {
    val values = freqs.map(_._1._1).filterNot(_.isNaN).distinct
    fail(values.length > numberOfBins, s"Attribute $attributeName has less values than bins")
    val bin = Bin(freqs)
    delimiters2bins(values.min +: values.max +: partition(bin, numberOfBins - 2))
  }

  private def partition(bin: Bin, depth: Int) = {
    def recursiveSplit(delimiters: List[Double], data: Bin, depth: Int): List[Double] =
      if (depth <= 0)
        delimiters
      else
        split(data, depth) match {
          case None => delimiters
          case Some((value, gain, binLo, binH)) =>
            val delimiters1 = value :: delimiters
            val delimiters2 = recursiveSplit(delimiters1, binLo, depth - delimiters1.length)
            recursiveSplit(delimiters2, binH, depth - delimiters2.length)
        }
    recursiveSplit(Nil, bin, depth).sorted
  }

  private case class Bin(freqs: List[((Double, Int), Int)]) {
    val size: Double = freqs.map(_._2).sum.toDouble
    val noTargets: Double = Set(freqs.map(_._1._2)).size.toDouble
    val entropy: Double = {
      val x = freqs
        .groupBy(_._1._2)
        .values
        .map(_.map(_._2).sum)
        .map(count => {val p = count.toDouble / size; p * math.log(p)})
        .sum
      x
    }
  }

  private def split(bin: Bin, depth: Int) = {
    val candidates = bin.freqs.map(_._1._1).distinct.sorted
    val delimiters =
      candidates
        .map(value => {
          val (lo, hi) = bin.freqs.partition(_._1._1 < value)
          val binLo = Bin(lo)
          val binHi = Bin(hi)
          val gain = entropyGain(bin, Bin(lo), Bin(hi))
          (value, gain, binLo, binHi)
        })
        .filter { case (_, gain, binLo, binHi) => binLo.size > 0 && binHi.size > 0 && (gain < minGain(bin, binLo, binHi) || depth <= 0) }

    if (delimiters.isEmpty) None else Some(delimiters.maxBy(_._2))
  }

  private def entropyGain(original: Bin, left: Bin, right: Bin): Double =
    original.entropy - ((left.size / original.size) * left.entropy + (right.size / original.size) * right.entropy)

  private def minGain(original: Bin, left: Bin, right: Bin) = {
    val log2 = math.log(2.0)
    val diff = original.noTargets * original.entropy - left.noTargets * left.entropy - right.noTargets * right.entropy
    val delta = math.log(math.pow(3.0, original.noTargets - 2.0)) / log2 - diff
    (delta + math.log(original.size - 1.0) / log2) / original.size
  }
}
