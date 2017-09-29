//package nqfpruleminer.common
//
//object closure {
//
//  // invariant: items are of decreasing order, children as well
//  def lowerClosure(items: List[Int], nodes: List[FPnode]): List[Int] = {
//    def closureForNode(items: List[Int], node: FPnode): List[Int] = {
//      items match {
//        case Nil => Nil
//        case List(item) =>
//          if (item == node.item) {
//            List(item)
//          } else if (item < node.item) {
//            node.parent match {
//              case None => Nil
//              case Some(_parent) => if (_parent.isRoot) Nil else closureForNode(items, _parent)
//            }
//          } else {
//            Nil
//          }
//        case item :: items1 =>
//          if (item == node.item) {
//            node.parent match {
//              case None => List(item)
//              case Some(_parent) => closureForNode(items, _parent)
//            }
//          } else if (item > node.item) {
//            if (node.isRoot) Nil else closureForNode(items1, node)
//          } else {
//            node.parent match {
//              case None => Nil
//              case Some(_parent) => closureForNode(items, _parent)
//            }
//          }
//      }
//    }
//    nodes match {
//      case Nil => Nil: List[Int]
//      case List(node) => closureForNode(items, node)
//      case node :: _nodes => lowerClosure(closureForNode(items, node), _nodes)
//    }
//  }
//
//  // invariant: items are of increasing order, children as well
//  def upperClosure(items: List[Int], nodes: List[FPnode]): List[Int] = {
//    def closureForNode(items: List[Int], children: List[FPnode]): List[Int] = {
//      (items, children) match {
//        case (Nil, _) => Nil
//        case (_items, Nil) => Nil
//        case (List(item), List(child)) =>
//          if (item == child.item) List(item) else Nil
//        case (item :: items1, List(child)) =>
//          if (item == child.item) item :: closureForNode(items1, child.children) else Nil
//        case (item :: items1, child :: children1) =>
//          val items =
//            if (item == child.item)
//              item :: closureForNode(items1, child.children)
//            else if (item < child.item)
//              closureForNode(items1, children)
//            else
//              closureForNode(items1, child.children)
//          closureForNode(items, children1)
//      }
//    }
//    nodes match {
//      case Nil => Nil
//      case List(node) => closureForNode(items, node.children)
//      case node :: _nodes => upperClosure(closureForNode(items, node.children), _nodes)
//    }
//  }
//
//  def compute(headers: Array[List[FPnode]], group: List[Int]) = {
//    val majorItem = group.last
//    val lowerItems = Array.tabulate(majorItem + 1)(majorItem - _).toList
//    val upperItems = Array.tabulate(coding.numberOfItems - majorItem - 1)(majorItem + _ + 1).toList
//    val lowerClosedSet = lowerClosure(lowerItems, headers(majorItem)).reverse
//    val upperClosedSet = upperClosure(upperItems, headers(majorItem))
//    if (lowerClosedSet.intersect(group) == group)
//      lowerClosedSet ++ upperClosedSet
//    else
//      group
//  }
//
//}