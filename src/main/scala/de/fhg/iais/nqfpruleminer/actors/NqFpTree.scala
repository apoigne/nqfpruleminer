package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import de.fhg.iais.nqfpruleminer.Value.Label
import de.fhg.iais.nqfpruleminer._

object NqFpTree {
  case class EncodedInstance(label: Label, values: Vector[Int])
  case class NoMoreInstances(quality: Distribution => Quality)
  case object Next
  case class Terminated(tree: ActorRef, subgroupCounter: Int)

  def props(range: Range, numberOfItems: Int, lengthOfSubgroups: Int, master: ActorRef, bestSubgroups: ActorRef)(implicit ctx: Context) =
    Props(classOf[NqFpTree], range.start, range.end, numberOfItems, lengthOfSubgroups, master, bestSubgroups, ctx)
}

class NqFpTree(lower: Int, upper: Int, numberOfItems: Int, lengthOfSubgroups: Int, master: ActorRef, bestSubgroups: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  import NqFpTree._

  log.info("Started")
  private var minQ = ctx.minimalQuality
  implicit val numberOfTargetGroups: Int = ctx.numberOfTargetGroups

  private var subgroupCounter = 0
  private val subGroup = Array.fill(lengthOfSubgroups)(-1)
  subGroup(0) = lower - 1
  private var depth = 0

  val root = FPnode(-1, None)
  val headers: Array[List[FPnode]] = Array.fill(numberOfItems)(List[FPnode]())

  private val itemDistributions: Array[Distribution] = Array.tabulate(numberOfItems)(_ => Distribution()) // number of target groups is implicitly defined
  private val optimisticEstimate: Array[Array[Double]] = Array.tabulate(lengthOfSubgroups)(_ => Array.fill(numberOfItems)(Double.MaxValue))

  def receive: Receive = {
    case EncodedInstance(label, instance) =>
      val distr = Distribution(label)
      addInstance(root, distr, instance)
    case NoMoreInstances(quality) =>
      log.info(s"Number of nodes = $nodeCounter")
      estimates(Nil, upper, 0, quality)
      context become computeSubgroups(quality)
      self ! Next
  }

  def computeSubgroups(quality: Distribution => Quality): Receive = {
    case Next =>
      subGroup(depth) += 1
      val item = subGroup(depth)
      if (depth > 0 && item < subGroup(depth - 1) || depth == 0 && item < upper) {
        if (depth + 1 < lengthOfSubgroups && optimisticEstimate(depth)(item) > minQ) {
          if (depth == 0) log.info(s"Evaluation of item $item started")
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
          log.info("Terminated")
          master ! NqFpTree.Terminated(self, subgroupCounter)
        }
      } else if (depth > 0) {
        val _item = subGroup(depth)
        subGroup(depth) = -1
        depth -= 1
        if (_item <= upper) headers(_item).foreach(popParents(depth))
        self ! Next
      } else {
        log.info("Terminated")
        master ! NqFpTree.Terminated(self, subgroupCounter)
        self ! PoisonPill
      }
    case BestSubGroups.MinQ(_minQ) =>
      minQ = _minQ
  }

  private def pushParents(currentDepth: Int)(node: FPnode): Unit = {
    val distr = node.distr

    @scala.annotation.tailrec
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
      @scala.annotation.tailrec
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

  private def estimates(subgroup: List[Int], item: Int, init: Int, quality: Distribution => Quality): Unit =
    for (_item <- init until item) {
      if (optimisticEstimate(depth)(_item) > minQ) {
        optimisticEstimate(depth)(_item) = checkQuality(_item, subgroup :+ _item, quality)
        itemDistributions(_item).reset()
      }
    }

  def checkQuality(item: Int, subGroup: List[Int], quality: Distribution => Quality): Double = {
    subgroupCounter += 1
    quality(itemDistributions(item)) match {
      case Quality(q, g, oe) =>
        if (q > minQ && subGroup.head >= lower) {
          val _subGroup = if (ctx.computeClosureOfSubgroups) computeClosure(headers(item), subGroup) else subGroup
          bestSubgroups ! BestSubGroups.SubGroup(_subGroup, itemDistributions(item).toList, q, g)
        }
        oe
    }
  }

  var nodeCounter = 0

  def addInstance(node: FPnode, distr: Distribution, items: Seq[Int]): Unit = {
    items.toList match {
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

  // invariant: items are of decreasing order, children as well
  def lowerClosure(items: List[Int], nodes: List[FPnode]): List[Int] = {
    @scala.annotation.tailrec
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

  private def computeClosure(headerOfMajorItems: List[FPnode], subGroup: List[Int]) = {
    val majorItem = subGroup.last
    val lowerItems = Array.tabulate(majorItem + 1)(majorItem - _).toList
    val upperItems = Array.tabulate(numberOfItems - majorItem - 1)(majorItem + _ + 1).toList
    val lowerClosedSet = lowerClosure(lowerItems, headerOfMajorItems).reverse
    val upperClosedSet = upperClosure(upperItems, headerOfMajorItems)
    if (lowerClosedSet.intersect(subGroup) == subGroup)
      lowerClosedSet ++ upperClosedSet
    else
      subGroup
  }
}