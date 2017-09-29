package nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import better.files._
import nqfpruleminer.{Coding, Distribution, FPnode}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BestSubGroups {
  type Qual = Double
  type Gen = Double

  case class SubGroup(group: List[Int], distr: IndexedSeq[Int], quality: Qual, generality: Gen, path : String)
  case class ComputeBestSubgroups(headers: List[FPnode], distr: IndexedSeq[Int], group: List[Int], q: Qual, g: Gen, path: String)
  case class MinQ(value: Double)
  case class GenOutput(rootDistribution: Distribution, subgroupCounter: Int)

  def props(numberOfBestSubgroups: Int, lengthOfSubgroups: Int, coding: Coding) =
    Props(classOf[BestSubGroups], numberOfBestSubgroups, lengthOfSubgroups, coding)
}

class BestSubGroups(numberOfBestSubgroups: Int,
                    lengthOfSubgroups: Int,
                    coding: Coding
                   ) extends Actor with ActorLogging {

  import BestSubGroups._

  private var _minQ = nqfpruleminer.minimalQuality
  private val refineSubgroups = nqfpruleminer.refineSubgroups
  private val computeClosureOfSubgroups = nqfpruleminer.computeClosureOfSubgroups

  private var kBestSubGroups = List[SubGroup]()

  implicit val ec: ExecutionContextExecutor = context.dispatcher
  implicit val timeout: Timeout = 1.second

  def receive: Receive = {
    case msg@ComputeBestSubgroups(headerOfMajorItems, distr, group, q, g, path) =>
//      log.info(s"$path $group $q")
      if (kBestSubGroups.length < numberOfBestSubgroups) {
        val _group = if (computeClosureOfSubgroups) computeClosure(headerOfMajorItems, group) else group
        val subgroup = SubGroup(_group, distr, q, g, path)
        kBestSubGroups = insert(subgroup, kBestSubGroups)
        val newMinQ = kBestSubGroups.head.quality
        if (newMinQ > _minQ) update(newMinQ, path, subgroup)
      } else {
        context become receiveFull
        self ! msg
      }

    case GenOutput(rootDistribution, subgroupCounter) =>
      genOutput(rootDistribution, subgroupCounter)
  }

  def receiveFull: Receive = {
    case ComputeBestSubgroups(headerOfMajorItems, distr, group, q, g, path) =>
//      log.info(s"$path $group $q")
      if (q > _minQ) {
        val _group = if (computeClosureOfSubgroups) computeClosure(headerOfMajorItems, group) else group
        val subgroup = SubGroup(_group, distr, q, g, path)
        kBestSubGroups = insert(subgroup, kBestSubGroups.tail)
        val newMinQ = kBestSubGroups.head.quality
        if (newMinQ > _minQ) update(newMinQ, path, subgroup)
      }
    case GenOutput(rootDistribution, subgroupCounter) =>
      genOutput(rootDistribution, subgroupCounter)
  }

  private def update(newMinQ: Double, path : String, group : SubGroup): Unit = {
//    log.info(s"newMinQ $newMinQ $path ${group.group} ${group.quality}")
    context.actorSelection(s"akka://nqfpminer/user/master") ! MinQ(newMinQ)
    _minQ = newMinQ
  }

  private def isExtensionOf(groups1: List[Int], groups2: List[Int]) = {
    def isExtensionOf(groups1: List[Int], groups2: List[Int]): Boolean =
      (groups1, groups2) match {
        case (Nil, Nil) => true
        case (Nil, _) => false
        case (_, Nil) => true
        case (g1 :: _groups1, g2 :: _groups2) if g1 == g2 => isExtensionOf(_groups1, _groups2)
        case (g1 :: _groups1, g2 :: _) if g1 < g2 => isExtensionOf(_groups1, groups2)
        case (g1 :: _, g2 :: _groups2) => isExtensionOf(groups1, _groups2)
        case _ => groups1 == groups2
      }
    isExtensionOf(groups1, groups1)
  }

  def insert(sg: SubGroup, groups: List[SubGroup]): List[SubGroup] = {
    groups match {
      case Nil => List(sg)
      case _sg :: best =>
        if (_sg.quality < sg.quality)
          _sg :: insert(sg, best)
        else if (isExtensionOf(_sg.group, sg.group) && _sg.quality < sg.quality && refineSubgroups)
          _sg :: best
        else
          sg :: _sg :: best
    }
  }

  // invariant: items are of decreasing order, children as well
  def lowerClosure(items: List[Int], nodes: List[FPnode]): List[Int] = {
    def closureForNode(items: List[Int], node: FPnode): List[Int] = {
      items match {
        case Nil => Nil
        case List(item) =>
          if (item == node.item) {
            List(item)
          } else if (item < node.item) {
            node.parent match {
              case None => Nil
              case Some(_parent) => if (_parent.isRoot) Nil else closureForNode(items, _parent)
            }
          } else {
            Nil
          }
        case item :: items1 =>
          if (item == node.item) {
            node.parent match {
              case None => List(item)
              case Some(_parent) => closureForNode(items, _parent)
            }
          } else if (item > node.item) {
            if (node.isRoot) Nil else closureForNode(items1, node)
          } else {
            node.parent match {
              case None => Nil
              case Some(_parent) => closureForNode(items, _parent)
            }
          }
      }
    }
    nodes match {
      case Nil => Nil: List[Int]
      case List(node) => closureForNode(items, node)
      case node :: _nodes => lowerClosure(closureForNode(items, node), _nodes)
    }
  }

  // invariant: items are of increasing order, children as well
  def upperClosure(items: List[Int], nodes: List[FPnode]): List[Int] = {
    def closureForNode(items: List[Int], children: List[FPnode]): List[Int] = {
      (items, children) match {
        case (Nil, _) => Nil
        case (_, Nil) => Nil
        case (List(item), List(child)) =>
          if (item == child.item) List(item) else Nil
        case (item :: items1, List(child)) =>
          if (item == child.item) item :: closureForNode(items1, child.children) else Nil
        case (item :: items1, child :: children1) =>
          val items =
            if (item == child.item)
              item :: closureForNode(items1, child.children)
            else if (item < child.item)
              closureForNode(items1, children)
            else
              closureForNode(items1, child.children)
          closureForNode(items, children1)
      }
    }
    nodes match {
      case Nil => Nil
      case List(node) => closureForNode(items, node.children)
      case node :: _nodes => upperClosure(closureForNode(items, node.children), _nodes)
    }
  }

  private def computeClosure(headerOfMajorItems: List[FPnode], group: List[Int]) = {
    val majorItem = group.last
    val lowerItems = Array.tabulate(majorItem + 1)(majorItem - _).toList
    val upperItems = Array.tabulate(coding.numberOfItems - majorItem - 1)(majorItem + _ + 1).toList
    val lowerClosedSet = lowerClosure(lowerItems, headerOfMajorItems).reverse
    val upperClosedSet = upperClosure(upperItems, headerOfMajorItems)
    if (lowerClosedSet.intersect(group) == group)
      lowerClosedSet ++ upperClosedSet
    else
      group
  }

  private val outputFile = nqfpruleminer.outputFile
  private val targetGroups =  nqfpruleminer.targetGroups
  private val numberOfTargetGroups =  nqfpruleminer.numberOfTargetGroups
  private val dataFiles = nqfpruleminer.dataFiles
  private val targetName =  nqfpruleminer.targetName
  private val qualityMode = nqfpruleminer.qualityMode

  private def genOutput(rootDistribution: Distribution, subgroupCounter: Int): Unit = {
    outputFile.toFile.overwrite("")
    outputFile.toFile.append(subgroupsToString(rootDistribution, subgroupCounter))
    context.system.terminate() onComplete {
      case Success(_) =>
        System.exit(0)
      case Failure(e) =>
        log.info(e.getMessage)
    }
  }
  def subgroupsToString(rootDistribution: Distribution, subgroupCounter: Long): String = {
    val targetValues = targetGroups.map(_.toString).reduce(_ + "," + _)
    val numberOfNodes = binomialSum(coding.numberOfItems.toLong, lengthOfSubgroups)

    s"Dataset: $dataFiles\n\n" +
      s"Target:  attribute: $targetName, values: $targetValues\n" +
      s"Quality function: $qualityMode\n" +
      s"Number of items: ${coding.numberOfItems}\n\n" +
      s"TargetValueDistribution: " +
      (qualityMode match {
        case "Piatetsky" =>
          s"$targetValues= ${rootDistribution(1)} "
        case "Binomial" =>
          s"$targetValues= ${rootDistribution(1)} "
        case _ =>
          targetValues.zipWithIndex.map { case (v, i) => s"   $v: ${rootDistribution(i + 1)}" }.reduce(_ + "," + _)
      }) +
      s" others: ${rootDistribution(0)}\n\n" +
      s"The $numberOfBestSubgroups best subgroups:\n" +
      kBestSubGroups.reverse.zipWithIndex.map {
        case (sg: SubGroup, index: Int) =>
          s"\n${index + 1}. " +
            sg.group.sorted.map(coding.decode).map(_.toString).reduce(_ + " & " + _) +
            s"\nQuality = ${sg.quality}" +
            s"\nSize = ${sg.distr.sum}, Generality = ${sg.generality}, " +
            (qualityMode match {
              case "Piatetsky" =>
                val n = sg.distr.sum.toDouble
                s"p = ${sg.distr(0).toDouble / n}"
              case "Binomial" =>
                val n = sg.distr.sum.toDouble
                s"p = ${sg.distr(0).toDouble / n}"
              case _ =>
                val n = sg.distr.sum.toDouble
                (0 until numberOfTargetGroups).map(i => s"p($i) = ${sg.distr(i).toDouble / n}").reduce(_ + ", " + _)
            }) + "\n"
      }.reduce(_ + _) +
      s"\nConsidered $subgroupCounter subgroups of depth <= $lengthOfSubgroups out of ${numberOfNodes.toString} with maxDepth $lengthOfSubgroups," +
      s" i.e. ${subgroupCounter.toDouble / numberOfNodes.toDouble * 100.0} % \n"
  }

  def binomialCoefficient(n: Long, k: Int): Long =
    (1 to k).foldLeft(1L)((x, i) => (x * (n + 1 - i)) / i)

  def binomialSum(n: Long, k: Int): Long =
    if (k > 0) binomialCoefficient(n, k) + binomialSum(n, k - 1) else 0
}