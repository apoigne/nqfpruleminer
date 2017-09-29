//package ruleminer.common
//
//import java.io.File
//
//object DataFile {
//
//  def addtoTable(dataTable: Array[List[LabeledValue]])(label: Int, instance: Seq[Item]) =
//    for ((item, index) <- instance.zipWithIndex) {
//      item.lvalue.value match {
//        case (_, Numeric(_)) =>
//          dataTable(index) = value._2 :: dataTable(index)
//        case _ => ()
//      }
//    }
//
//  def read(file: File) = {
//    val size = ruleminer.attributes.length
//    if (size > 0) {
//      val dataTable = new Array[List[LabeledValue]](size)
//      Reader.evalInstances(file, addtoTable(dataTable))
//      dataTable
//    } else {
//      throw new RuntimeException("List of attributes has length 0. No attributes specified.")
//    }
//  }
//}