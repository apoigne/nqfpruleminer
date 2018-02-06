package de.fhg.iais.nqfpruleminer

case class FPnode(item: Int, parent: Option[FPnode])(implicit numberOfTargetGroups : Int) {

  val distr = Distribution()
  val isRoot : Boolean= parent.isEmpty
  var children :  List[FPnode] = List[FPnode]()
  var depth = 0

  def getParents(l: List[FPnode]): List[FPnode] =
    parent match {
      case None => this :: l
      case Some(_parent) => _parent.getParents(this :: l)
    }

  def clear(): Unit = {
    children.foreach(_.clear())
    children = Nil
  }
}