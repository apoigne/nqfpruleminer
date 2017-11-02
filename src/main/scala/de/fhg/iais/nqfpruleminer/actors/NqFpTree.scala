package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, Props}
import de.fhg.iais.nqfpruleminer._

object NqFpTree {
  case class Instance(value: List[Int])
  case class NoMoreInstances(quality: Distribution => Strategy)
  case object Next
  case class Terminated(subgroupCounter: Int)

  def props(lower: Int, upper: Int, numberOfItems : Int, lengthOfSubgroups: Int)(implicit ctx : Context) =
    Props(classOf[NqFpTree], lower, upper, numberOfItems, lengthOfSubgroups, ctx)
}

class NqFpTree(lower: Int, upper: Int, numberOfItems : Int, lengthOfSubgroups: Int)(implicit ctx : Context) extends Actor with ActorLogging {
  import NqFpTree._


  private var minQ = ctx.minimalQuality

  implicit val numberOfTargetGroups: Int = ctx.numberOfTargetGroups

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
      estimates(Nil, upper, 0, quality)
      context become computeSubgroups(quality)
      self ! Next
  }

  val root = FPnode(-1, None)
  val headers: Array[List[FPnode]] = Array.fill(numberOfItems)(List[FPnode]())

  val itemDistributions: Array[Distribution] = Array.tabulate(numberOfItems)(_ => Distribution()) // number of target groups is implicitly defined
  val optimisticEstimate: Array[Array[Double]] = Array.tabulate(lengthOfSubgroups)(_ => Array.fill(numberOfItems)(Double.MaxValue))

  def computeSubgroups(quality: Distribution => Strategy): Receive = {
    case Next =>
      subGroup(depth) += 1
      val item = subGroup(depth)
      if (depth > 0 && item < subGroup(depth - 1) || depth == 0 && item < upper) {
        if (depth + 1 < lengthOfSubgroups && optimisticEstimate(depth)(item) > minQ) {
          if (depth == 0)
            log.info(s"Eval item $item")
          headers(item).foreach(pushParents(depth))
          depth += 1
          Array.copy(optimisticEstimate(depth - 1), 0, optimisticEstimate(depth), 0, item)
          estimates(subGroup.toList.filter(_ > -1), item, 0, quality)
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
        val _item = subGroup(depth)
        subGroup(depth) = -1
        depth -= 1
        if (_item <= upper) headers(_item).foreach(popParents(depth))
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

  private def estimates(subgroup: List[Int], item: Int, init: Int, quality: Distribution => Strategy): Unit =
    for (_item <- init until item) {
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
        if (q > minQ && subGroup.head >= lower)
          bestSubgroups ! BestSubGroups.SubGroup(subGroup, itemDistributions(item).toList, q, g, self.path.name)
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