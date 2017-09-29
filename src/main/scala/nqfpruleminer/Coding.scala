package nqfpruleminer

class Coding(countTable: Map[Item, Int]) {
  val sortedItems: List[Item] = countTable.toList.sortWith((x0, x1) => x0._2 > x1._2).map(_._1)

  private val decodingTable: Array[Item] = sortedItems.toArray
  private val codingTable = sortedItems.zipWithIndex.toMap

  val numberOfItems: Int = countTable.size

//  (0 until numberOfItems).foreach(n => println(s"$n ${decode(n)}"))

  def decode(item: Int): String = decodingTable(item).toString
  def encode(item: Item): Int = codingTable(item)
}