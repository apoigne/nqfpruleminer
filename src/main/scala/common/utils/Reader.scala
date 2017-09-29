package common.utils

import java.io.FileReader

import com.opencsv.{CSVParser, CSVReader}
import nqfpruleminer.Item

class Reader(dataFile: String,
             hasHeader : Boolean = true,
             separator : Char = CSVParser.DEFAULT_SEPARATOR,
             quoteCharacter : Char = CSVParser.DEFAULT_QUOTE_CHARACTER,
             escapeCharacter : Char = CSVParser.DEFAULT_ESCAPE_CHARACTER
            ) extends CSVReader(new FileReader(dataFile), separator, quoteCharacter, escapeCharacter) {

  private val attributes = nqfpruleminer.attributes
  private val targetGroups = nqfpruleminer.targetGroups
  private val targetIndex = nqfpruleminer.targetIndex
  private val features = nqfpruleminer.features
  private val noOfAttributes = attributes.length

  def apply(ev: (Int, Seq[Item]) => Unit) : Unit = {
    val lines = this.iterator()
    if (hasHeader) lines.next() // drop the attributes
    while (lines.hasNext) {
      val instance = lines.next()
      assert(instance.length == noOfAttributes, s"Number of values $instance is different to the number of attribute $noOfAttributes.")
      val label = targetGroups.indexOf(instance(targetIndex)) + 1
      val values = for ((name, v) <- attributes.zip(instance) if features.contains(name)) yield Item(name, v)
      ev(label, values)
    }
  }

//    def toValue(v: String) =
//    targetKind match {
//      case "Nominal" =>
//        Nominal(v)
//      case "Numeric" =>
//        try {
//          Numeric(v.toDouble)
//        } catch {
//          case _: Throwable =>
//            println(s"Target kind is numeric, but $v is not numeric.")
//            System.exit(1)
//            Numeric(0.0)
//        }
//    }
}

