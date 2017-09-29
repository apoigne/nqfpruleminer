//package datapreparation
//
//import apps.ruleminer
//import com.typesafe.config.Config
//import nqfpruleminer.Item
//
//import scala.collection.JavaConverters._
//
//object Discretisation {
//
// private val configs = (discretisationConfig getConfigList "discretisation").asScala.toList
//
//  val intervalRanges: Map[String, List[Range]] = {
//    val configs = (discretisationConfig getConfigList "intervalbinning").asScala.toList
//    configs.flatMap(
//      conf => {
//        val features = try {(conf getStringList "features").asScala.toList} catch {case _: Throwable => List[String]()}
//        val ranges =
//          try {
//            (conf getConfigList "ranges").asScala.toList
//              .map(range => Range(range getDouble "lo", range getDouble "hi"))
//              .sortWith(_ < _)
//          } catch {
//            case _: Throwable => List[Range]()
//          }
//        features.flatMap(feature => Map(feature -> ranges))
//      }
//    )
//  }
//
//  val equalFrequencyRanges: Map[String, List[Range]] = {
//    val configs = (discretisationConfig getConfigList "equalfrequencyBinning").asScala.toList
//    configs.flatMap(
//      conf => {
//        val numberOfBins = try {conf getInt "numberOfBins"} catch {case _: Throwable => 0}
//        equalFrequencyBinning.computeDelimiters(numberOfBins)
//      }
//    )
//  }
//
//  val equalWidthRanges: Map[String, List[Range]] = {
//    val configs = (discretisationConfig getConfigList "equalwidthbinning").asScala.toList
//    configs.flatMap(
//      config => {
//        val numberOfBins = try {config getInt "numberOfBins"} catch {case _: Throwable => 0}
//        equalWidthBinning.computeDelimiters(numberOfBins)
//      }
//    )
//  }
//
//  val entropyRanges: Map[String, List[Range]] = {
//    val configs = (discretisationConfig getConfigList "entropybinning").asScala.toList
//    configs.flatMap(
//      conf => {
//        val numberOfBins = try {conf getInt "numberOfBins"} catch {case _: Throwable => 0}
//        entropyBinning.computeDelimiters(numberOfBins)
//      }
//    )
//  }
//
//  val listofRanges: List[List[Range]] = {
//    val featureToRanges = intervalRanges ++ equalFrequencyRanges ++ equalWidthRanges ++ entropyRanges
//    ruleminer.reader.features.map(
//      feature =>
//        featureToRanges.get(feature) match {
//          case None => List[Range]()
//          case Some(_ranges) => _ranges
//        }
//    )
//  }
//
//  private val noRanges = listofRanges.length
//
//  // assumption is that, if intervals are specified, only labeledValues are of interest that are within an interval
//  def generate(instance: Seq[Item]): List[List[Item]] = {
//    assert(instance.length == noRanges)
//    if (usesOverlappingIntervals) {
//      generateMultiple(instance)
//    } else {
//      List(generateSingle(instance))
//    }
//  }
//
//  def generateSingle(instance: Seq[Item]): List[Item] = {
//    instance.zip(listofRanges)
//      .map {
//        case (item, ranges) =>
//          item.value match {
//            case Numeric(d) =>
//              ranges.find(range => range.lo < d && d <= range.hi) match {
//                case None => item
//                case Some(r) => Item(item.feature, r)
//              }
//            case _ => item
//          }
//      }.toList
//  }
//
//  def generateMultiple(instance: Seq[Item]): List[List[Item]] = {
//    val multiples =
//      instance.zip(listofRanges)
//        .map {
//          case (item, ranges) =>
//            item.value match {
//              case Numeric(v) => ranges.filter(r => r.lo < v && v <= r.hi).map(Item(item.feature, _))
//              case _ => List(item)
//            }
//        }.toList
//    productOfLists(multiples)
//  }
//
//  def productOfLists[T](ll: List[List[T]]): List[List[T]] =
//    ll match {
//      case Nil => Nil
//      case List(l) => l.map(List(_))
//      case l :: _ll =>
//        val l2 = productOfLists(_ll)
//        l.flatMap(x => l2.map(y => x :: y))
//    }
//
//  trait CommonForBinning {
//    def delimiters2ranges(delimiters: List[Double]): List[Range] = {
//      delimiters match {
//        case x :: List(y) => List(Range(x, y))
//        case x :: y :: _delimiters => Range(x, y) :: delimiters2ranges(y :: _delimiters)
//        case Nil => Nil
//        case _ => throw new RuntimeException("Internal error: delimiters2ranges")
//      }
//    }
//
//    // the assumption is that the ranges are not overlapping
//    def makeOverlapping(ranges: List[Range]) : List[Range] =
//      if (usesOverlappingIntervals) {
//        val sortedRanges = ranges.sortWith((a, b) => a.lo < b.lo || (a.lo == b.lo && a.hi < b.hi))
//        def append1(low: Value, ranges: List[Range]): List[Range] = {
//          (low, sortedRanges) match {
//            case (_, Nil) =>
//              Nil
//            case (Numeric(low1), Range(_, hi) :: _ranges) =>
//              val newRanges = append1(low, _ranges)
//              if (low1 == -Double.MaxValue && hi == Double.MaxValue) {
//                newRanges
//              } else {
//                Range(low1, hi) :: _ranges
//              }
//            case _ => throw new RuntimeException("Internal error: mk_overlapping 1")
//          }
//        }
//        def append2(ranges: List[Range]): List[Range] = {
//          ranges match {
//            case Nil => Nil
//            case Range(low, _) :: ranges1 =>
//              val ranges2 = append1(Numeric(low), ranges1)
//              ranges2 ++ append2(ranges1)
//          }
//        }
//        sortedRanges ++ append2(sortedRanges)
//      } else {
//        ranges
//      }
//  }
//
//  object equalFrequencyBinning extends CommonForBinning {
//    private var table = Map[String, collection.mutable.Map[Double, Int]]()
//
//    def computeDelimiters(numberOfBins: Int): Map[String, List[Range]] = {
//      table.mapValues(
//        freqs => {
//          val binLengthFraction = freqs.values.sum.toDouble / numberOfBins
//          val binLength = if (binLengthFraction - binLengthFraction.toInt > 0.5) binLengthFraction.ceil.toInt else binLengthFraction.toInt
//          val sorted = freqs.toList.sortWith((v1, v2) => v1._1 < v2._1)
//          val delimeters =
//            sorted.foldLeft((List[Double](-Double.MaxValue), 0, 1)) {
//              // (delimiters, number of values, index of bin)
//              case ((l, offset, index), (value, number)) =>
//                if (number > binLength && index < numberOfBins) {
//                  ((value + (number - binLength).toDouble / binLengthFraction) :: l, offset + number - binLength, index + 1)
//                } else if (offset + number > binLength && index < numberOfBins) {
//                  ((value - (offset + number - binLength).toDouble / binLengthFraction) :: l, offset + number - binLength, index + 1)
//                } else {
//                  (l, offset + number, index)
//                }
//            }._1.sorted.tail
//          makeOverlapping(delimiters2ranges(delimeters))
//        }
//      )
//    }
//  }
//
//  object equalWidthBinning extends CommonForBinning {
//    // The table contains the smallest and largest element of a feature
//    private val table = collection.mutable.Map[String, Range]()
//
//    def computeDelimiters(numberOfBins: Int): Map[String, List[Range]] = {
//      table.mapValues(
//        range => {
//          val interval = (range.hi - range.lo) / numberOfBins
//          makeOverlapping(delimiters2ranges((0 until numberOfBins).map(i => range.lo + i * interval).toList))
//        }
//      ).toMap
//    }
//  }
//
//  object entropyBinning extends CommonForBinning {
//
//    private val table : Map[String, collection.mutable.Map[(Double, Int), Int]]
//
//    // Assumption : labeledValues are numeric and ordered
//    def computeDelimiters(numberOfBins: Int): Map[String, List[Range]] = {
//      table.mapValues(
//        freqs => {
//          val labeledValues = freqs.keys.toList
//          val bin = new Bin(labeledValues)
//          partition(bin, 1)
//        }
//      )
//    }
//
//    private def entropyOf(labeledValues: List[(Double, Int)]): Double = {
//      val distribution = Distribution(labeledValues.map(_._2))
//      val size = distribution.sum
//      distribution.distr.foldLeft(0.0) {
//        case (entropy, distr) =>
//          val prob = distr.toDouble / size
//          entropy - prob * math.log(prob)
//      }
//    }
//
//    private class Bin(val data: List[(Double, Int)]) {
//      val size : Double = data.length.toDouble
//      val targets : List[Int] = data.map(_._2).distinct
//      val entropy : Double = entropyOf(data)
//      val noTargets : Int = targets.length
//    }
//
//    private def entropyGain(original: Bin, left: Bin, right: Bin): Double =
//      original.entropy - (left.size / original.size) * left.entropy - (right.size / original.size) * right.entropy
//
//    private def minGain(original: Bin, left: Bin, right: Bin) = {
//      val log2 = math.log(2.0)
//      val diff = original.noTargets * original.entropy - left.noTargets * left.entropy - right.noTargets * right.entropy
//      val delta = math.log(math.pow(3.0, original.noTargets - 2.0)) / log2 - diff
//      (delta + math.log(original.size - 1.0) / log2) / original.size
//    }
//
//    private def split(bin: Bin, depth: Int) = {
//      val candidates = bin.data.map(_._1).sorted
//      val delimiters =
//        candidates
//          .map(value => {
//            val (b1, b2) = bin.data.partition(_._1 < value)
//            val binLo = new Bin(b1)
//            val binHi = new Bin(b2)
//            val gain = entropyGain(bin, binLo, binHi)
//            (value, gain, binLo, binHi)
//          })
//          .filter { case (_, gain, binLo, binHi) => gain >= minGain(bin, binLo, binHi) || depth <= 0 }
//      if (delimiters.isEmpty) None else Some(delimiters.maxBy(_._2))
//    }
//
//    private def partition(bin: Bin, depth: Int) = {
//      def recursiveSplit(delimiters: List[Double], data: Bin, noBins: Int): List[Double] =
//        split(data, noBins) match {
//          case None => delimiters
//          case Some((value, gain, binLo, binH)) =>
//            val delimiters1 = value :: delimiters
//            val delimiters2 = recursiveSplit(delimiters1, binLo, noBins - 1)
//            recursiveSplit(delimiters2, binH, noBins - 1)
//        }
//      makeOverlapping(delimiters2ranges(recursiveSplit(Nil, bin, depth).sorted))
//    }
//  }
//}