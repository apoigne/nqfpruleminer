//package datapreparation
//
//import common.utils.fail
//
//case class LabeledValue(value: Value, label: Int)
//class Attribute(val name: String, var isNumeric: Boolean, var isConsidered: Boolean)
//
//class Coding(numberOfGroups: Int, lengthOfSubgroups: Int) {
//
//  val countTable = collection.mutable.Map[(Attribute, Value), Int]()
//  val codingTable = collection.mutable.Map[(Attribute, Value), Int]()
//  val decodingTable = collection.mutable.Map[Int, String]()
//
//  lazy val numberOfItems = countTable.size
//
//  /* -----------------------------------------------------------------------
// * Read and encode dataenv *
//   ----------------------------------------------------------------------- */
//  def countItems(usesOverlappingIntervals: Boolean)(values: List[(Attribute, Value)]): Unit = {
//    for ((attribute, value) <- values) {
//      if (usesOverlappingIntervals) {
//        for (item <- Ranges.generateMultiple(attribute, value))
//          countTable get item match {
//            case None => countTable += (item -> 1)
//            case Some(x) => countTable.+=(item -> (x + 1))
//          }
//      } else {
//        val item = Ranges.generateSingle(attribute, value)
//        countTable get item match {
//          case None => countTable += (item -> 1)
//          case Some(x) => countTable += (item -> (x + 1))
//        }
//      }
//    }
//  }
//
//  def generateCodingOfItems() = {
//    val allItems = countTable.toList
//    val sortedItems = allItems.sortWith((x0, x1) => x0._2 > x1._2 || x0._2 == x1._2 && x0._1._1.name < x1._1._1.name)
//    for ((item, i) <- sortedItems.zipWithIndex) {
//      val ((attribute, value), _) = item
//      codingTable += ((attribute, value) -> i)
//      decodingTable += (i -> (attribute.name + " -> " + value.toString))
//    }
//
//  }
//
//  def decode(item: Int) =
//    decodingTable.get(item) match {
//      case None => fail("decode: " + item.toString)
//      case Some(value) => value
//    }
//
//  def encode(attribute: Attribute, value: Value): Option[Int] =
//    codingTable.get((attribute, value))
//
//  def encodeInstance(instance: List[(Attribute, Value)]) =
//    instance.foldLeft(List[Int]())(
//      (codedItems, item) => {
//        val (attribute, value) = item
//        encode(attribute, value) match {
//          case None => codedItems
//          case Some(_item) => _item :: codedItems
//        }
//      }
//    )
//}