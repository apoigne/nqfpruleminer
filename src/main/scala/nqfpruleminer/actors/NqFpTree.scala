package nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, Props}
import nqfpruleminer.{Coding, Distribution, FPnode, Prune, Quality, Strategy, minimalQuality}

object NqFpTree {
  case class Instance(value: List[Int])
  case class NoMoreInstances(quality: Distribution => Strategy)
  case object Next
  case class Terminated(subgroupCounter: Int)

  def props(lower: Int, upper: Int, coding: Coding, lengthOfSubgroups: Int) = Props(classOf[NqFpTree], lower, upper, coding, lengthOfSubgroups)
}

class NqFpTree(lower: Int, upper: Int, _coding: Coding, lengthOfSubgroups: Int) extends Actor with ActorLogging {
  import NqFpTree._

  private val coding = _coding

  private var minQ = minimalQuality

  implicit val numberOfTargetGroups: Int = nqfpruleminer.numberOfTargetGroups
  private val numberOfItems = coding.numberOfItems

  private var subgroupCounter = 0
  private val subGroup = Array.fill(lengthOfSubgroups)(-1)
  subGroup(0) = lower - 1
  private var depth = 0

  def receive: Receive = {
    case TreeGenerator.DataFrame(label, _instance) =>
      val instance = _instance.filter(_ < upper)
      if (instance.exists(_ >= lower)) {
        val distr = Distribution(label)
        addInstance(root, distr, instance)
      }
    case NoMoreInstances(quality) =>
      log.info(s"Tree ($lower, $upper) generated. Number of nodes = $nodeCounter")
      estimates(Nil, upper, quality)
      context become computeSubgroups(quality)
      self ! Next
  }

  val root = FPnode(-1, None)
  val headers: Array[List[FPnode]] = Array.fill(numberOfItems)(List[FPnode]())

  val itemDistributions: Array[Distribution] = Array.tabulate(upper)(_ => Distribution()) // number of target groups is implicitly defined
  val optimisticEstimate: Array[Array[Double]] = Array.tabulate(lengthOfSubgroups)(_ => Array.fill(numberOfItems)(Double.MaxValue))

  def computeSubgroups(quality: Distribution => Strategy): Receive = {
    case Next =>
      subGroup(depth) += 1
      val item = subGroup(depth)
      if (depth > 0 && item < subGroup(depth - 1) || depth == 0 && item < upper) {
        if (depth + 1 < lengthOfSubgroups && optimisticEstimate(depth)(item) > minQ) {
            if (depth == 0) {
              log.info(s"Mining starts for ${coding.decode(item)}  ($item/$upper)")
            }
          headers(item).foreach(pushParents(depth))
          depth += 1
          Array.copy(optimisticEstimate(depth - 1), 0, optimisticEstimate(depth), 0, item)
          estimates(subGroup.toList.filter(_ > -1), item, quality)
        } else if (depth > 0) {
          val item = subGroup(depth - 1)
          subGroup(depth) = -1
          depth -= 1
          headers(item).foreach(popParents(depth))
        }
        if (subGroup.head + 1 <= upper) {
          self ! Next
        } else {
          log.info(s"${self.path} terminated")
          context.actorSelection(s"akka://nqfpminer/user/master") ! NqFpTree.Terminated(subgroupCounter)
        }
      } else if (depth > 0) {
        val item = subGroup(depth)
        subGroup(depth) = -1
        depth -= 1
        if (item <= upper) headers(item).foreach(popParents(depth))
        self ! Next
      } else {
        log.info(s"${self.path} terminated")
        context.actorSelection(s"akka://nqfpminer/user/master") ! NqFpTree.Terminated(subgroupCounter)
      }
    case BestSubGroups.MinQ(_minQ) =>
      minQ = _minQ
  }

  private def pushParents(currentDepth: Int)(node: FPnode): Unit = {
    val distr = node.distr
    def pushParents(_node: FPnode): Unit = {
      _node.parent match {
        case Some(_parent) if _parent.isRoot =>
        case Some(_parent) =>
          itemDistributions(_parent.item).add(distr)
          if (_parent.depth == currentDepth) {
            _parent.distr.set(distr)
            _parent.depth += 1
          } else {
            _parent.distr.add(distr)
          }
          pushParents(_parent)
        case None =>
      }
    }
    if (node.depth == currentDepth) pushParents(node)
  }

  private def popParents(currentDepth: Int)(node: FPnode): Unit =
    if (node.depth == currentDepth) {
      def popParents(_node: FPnode): Unit = {
        _node.parent match {
          case Some(_parent) if _parent.isRoot =>
          case Some(_parent) =>
            if (_parent.depth == currentDepth + 1) _parent.depth -= 1
            popParents(_parent)
          case None =>
        }
      }
      if (node.depth == currentDepth) popParents(node)
    }

  private def estimates(subgroup: List[Int], item: Int, quality: Distribution => Strategy): Unit =
    for (_item <- lower until item) {
      if (optimisticEstimate(depth)(_item) > minQ) {
        optimisticEstimate(depth)(_item) = checkQuality(_item, subgroup :+ _item, quality)
        itemDistributions(_item).reset()
      }
    }

  private val bestSubgroups = context.actorSelection("akka://nqfpminer/user/master/bestsubgroups")

  def checkQuality(item: Int, subGroup: List[Int], quality: Distribution => Strategy): Double = {
    subgroupCounter += 1
    quality(itemDistributions(item)) match {
      case Prune => 0.0
      case Quality(q, g, oe) =>
        if (q > minQ)
          bestSubgroups ! BestSubGroups.ComputeBestSubgroups(headers(item), itemDistributions(item).toList, subGroup, q, g, self.path.name)
        oe
    }
  }

  var nodeCounter = 0

  def addInstance(node: FPnode, distr: Distribution, items: Seq[Int]): Unit = {
    items match {
      case Nil => ()
      case item :: _items =>
        itemDistributions(item).add(distr)
        val child =
          node.children.find(_.item == item) match {
            case Some(_child) =>
              _child.distr.add(distr)
              _child
            case None =>
              val _child = FPnode(item, Some(node))
              nodeCounter += 1
              _child.distr.add(distr)
              node.children = _child :: node.children
              headers(item) = _child :: headers(item)
              _child
          }
        addInstance(child, distr, _items)
    }
  }
}