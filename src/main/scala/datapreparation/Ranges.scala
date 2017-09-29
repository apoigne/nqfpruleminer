//package datapreparation
//
//import common.utils.fail
//import nqfpruleminer.Item
//import nqfpruleminer.classes.Range
//
//import scala.collection.mutable.HashMap
//
//object Ranges {
//
//  var globalRanges = List[(Double, Double)]()
//  val attributeRanges = HashMap[String, List[(Double, Double)]]()
//
//  def generateFrom(delimiters: List[Value]): List[(Double, Double)] = {
//    delimiters match {
//      case Numeric(x) :: List(Numeric(y)) => List((x, y))
//      case Numeric(x) :: Numeric(y) :: delimiters => (x, y) :: generateFrom(Numeric(y) :: delimiters)
//      case Nil => Nil
//      case _ => fail("Internal error: generate_from");Nil
//    }
//  }
//
//  // the assumption is that the ranges are not overlapping
//  def makeOverlapping(ranges: List[(Double, Double)]) = {
//    def compare(a: (Double, Double), b: (Double, Double)) = {
//      a._1 < b._1 || (a._1 == b._1 && a._2 < b._2)
//    }
//    val sortedRanges = ranges.sortWith(compare)
//    def append1(low: Value, ranges: List[(Double, Double)]): List[(Double, Double)] = {
//      (low, sortedRanges) match {
//        case (_, Nil) =>
//          Nil
//        case (Numeric(low1), (_, up) :: ranges) =>
//          val newRanges = append1(low, ranges)
//          if (low1 == -Double.MaxValue && up == Double.MaxValue) {
//            newRanges
//          } else {
//            (low1, up) :: ranges
//          }
//        case _ => fail("Internal error: mk_overlapping 1");Nil
//      }
//    }
//    def append2(ranges: List[(Double, Double)]): List[(Double, Double)] = {
//      ranges match {
//        case Nil => Nil
//        case (low, up) :: ranges1 =>
//          var ranges2 = append1(Numeric(low), ranges1)
//          ranges2 ++ append2(ranges1)
//      }
//    }
//    sortedRanges ++ append2(ranges)
//  }
//
//  def discretizeAllAttributes(numberOfBins: Int, discretisation_fct: (Int, List[LabeledValue]) => List[Value]) = {
//    val dataTable = DataFile.read(env.inputFile)
//    for (i <- 0 until dataTable.length) {
//      val labeledValues = dataTable(i)
//      if (env.attributes(i).isNumeric) {
//        val delimiters = discretisation_fct(numberOfBins, labeledValues)
//        if (delimiters != Nil) {
//          ps("Attribute:");
//          ps(env.attributes(i).name);
//          ps(" delimiters:")
//          delimiters.foreach(x => ps(x + " \n"));
//          pF()
//        }
//        val ranges1 = generateFrom(delimiters)
//        val ranges = if (env.usesOverlappingIntervals) makeOverlapping(ranges1) else ranges1
//        attributeRanges += (env.attributes(i).name -> ranges)
//      }
//    }
//  }
//
//  def compute() = {
//    discretisation.foreach(
//      x =>
//        x match {
//          case NoDiscretisation =>
//            ()
//          case Delimiters(None, _delimiters) =>
//            val delimiters = (Numeric(-Double.MaxValue)) :: _delimiters ++ List(Numeric(Double.MaxValue))
//            if (delimiters != Nil) {
//              ps("Global delimiters:")
//              delimiters.foreach(x => ps(x + " \n"));
//              pF()
//            }
//            val ranges1 = generateFrom(delimiters)
//            val ranges2 = if (env.usesOverlappingIntervals) {makeOverlapping(ranges1)} else {ranges1}
//            globalRanges = ranges2
//          case Delimiters(Some(attribute), _delimiters) =>
//            var delimiters = (Numeric(-Double.MaxValue)) :: _delimiters ++ List(Numeric(Double.MaxValue))
//            if (delimiters != Nil) {
//              ps("Attribute:" + attribute + " delimiters:")
//              delimiters.foreach(x => ps(x + " "));
//              pn();
//              pF()
//            }
//            val ranges1 = generateFrom(delimiters)
//            val ranges2 = if (env.usesOverlappingIntervals) {makeOverlapping(ranges1)} else {ranges1}
//            attributeRanges += (attribute -> ranges2)
//          case Entropy(List((None, numberOfBins))) =>
//            discretizeAllAttributes(numberOfBins, Discretisation.entropy)
//          case Entropy(attribute_numberOfBins_list) =>
//            ()
//          case Frequency(List((None, numberOfBins))) =>
//            discretizeAllAttributes(numberOfBins, Discretisation.frequency)
//          case Frequency(attribute_numberOfBins_list) =>
//            ()
//          case Interval(List((None, numberOfBins))) =>
//            discretizeAllAttributes(numberOfBins, Discretisation.interval)
//          case Interval(attribute_numberOfBins_list) =>
//            ()
//        }
//    )
//  }
//
//  // assumption is that, if intervals are specified, only labeledValues are of interest that are within an interval
//  def generateMultiple(attribute: Attribute, value: Value): List[(Attribute, Value)] = {
//    if (attribute.isNumeric && env.discretisation != Nil) {
//      val ranges =
//        attributeRanges.get(attribute.name) match {
//          case None => globalRanges
//          case Some(ranges) => ranges
//        }
//      value match {
//        case Numeric(value) =>
//          ranges.foldLeft(Nil: List[(Attribute, Value)])(
//            (list, range) =>
//              if (range._1 < value && value <= range._2)
//                (attribute, Range(range._1, range._2)) :: list
//              else
//                list
//          )
//        case _ =>
//          failwith("Internal error: Ranges.generate")
//      }
//    } else {
//      List((attribute, value))
//    }
//  }
//
//  def generateSingle(attribute: Attribute, value: Value) : (Attribute, Value) = {
//    if (attribute.isNumeric && env.discretisation != Nil) {
//      val ranges =
//        attributeRanges.get(attribute.name) match {
//          case None => globalRanges
//          case Some(ranges) => ranges
//        }
//      value match {
//        case Numeric(value) =>
//          ranges.find(range => range._1 < value && value <= range._2) match {
//            case None => (attribute, Numeric(value))
//            case Some((low, up)) => (attribute, Range(low, up))
//          }
//        case _ => failwith("Internal error: Ranges.generate")
//      }
//    } else {
//      (attribute, value)
//    }
//  }
//
//  def generate(instance: List[Value]) =
//    if (env.usesOverlappingIntervals) {
//      val instances =
//        for ((attr, value) <- env.attributes zip instance) yield Ranges.generateMultiple(attr, value)
//      productOfMultipleLists(instances)
//
//    } else {
//      List(for ((attr, value) <- env.attributes zip instance) yield Ranges.generateSingle(attr, value))
//    }
//
//}