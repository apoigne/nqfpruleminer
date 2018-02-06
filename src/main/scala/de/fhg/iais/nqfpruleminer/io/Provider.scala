package de.fhg.iais.nqfpruleminer.io

import java.io.FileReader

import better.files._
import com.opencsv.CSVReader
import de.fhg.iais.nqfpruleminer.{Context, Feature}
import de.fhg.iais.utils.fail

object Provider {
  trait Data
  case class Csv(dataFile: String, hasHeader: Boolean, separator: Char, quoteCharacter: Char, escapeCharacter: Char) extends Data
  case class MySql(host: String, port: Int, database: String, table: String, user: String, password: String) extends Data

  def apply(data: Data)(implicit ctx: Context): Provider =
    data match {
      case data: Csv => new CsvProvider(data)
      case data: MySql => new MySqlProvider(data)
    }
}

trait Provider {
  def hasNext: Boolean
  def next: Array[String]
  val targetIndex: Int
  val timeIndex: Option[Int]
  val featureToPosition: List[(Feature, Int)]
}

class CsvProvider(data: Provider.Csv)(implicit ctx: Context) extends Provider {
  fail(data.dataFile.toFile.exists, "")
  private val lines = new CSVReader(new FileReader(data.dataFile), data.separator, data.quoteCharacter, data.escapeCharacter).iterator

  private val header =
    if (data.hasHeader) {
      if (lines.hasNext) {lines.next()} else {fail(s"The file ${data.dataFile} is empty."); null}
    } else {
      Array[String]()
    }

  val featureToPosition: List[(Feature, Int)] =
    ctx.baseFeatures.map {
      feature =>
        if (header.nonEmpty) {
          val columnIndex = header.indexOf(feature.name)
          if (columnIndex < 0) {
            fail(s"Feature ${feature.name} does not occur in the header of ${data.dataFile}")
            null
          } else {
            (feature, columnIndex)
          }
        } else {
          (feature, feature.position)
        }
    }

  val targetIndex: Int = if (header.nonEmpty) header.indexOf(ctx.targetName) else ctx.baseFeatures.indexWhere(_.name == ctx.targetName)
  val timeIndex: Option[Int] = ctx.timeName.map(tn => if (header.nonEmpty) header.indexOf(tn) else ctx.baseFeatures.indexWhere(_.name == tn))

  def hasNext: Boolean = lines.hasNext
  def next: Array[String] = lines.next
}

class MySqlProvider(data: Provider.MySql)(implicit ctx: Context) extends Provider {
  import scalikejdbc._

//  Class.forName("com.mysql.jdbc.Driver")
  ConnectionPool.singleton(s"jdbc:mysql://${data.host}:${data.port}/${data.database}", data.user, data.password)
  implicit val session: AutoSession = AutoSession

  val featureToPosition: List[(Feature, Int)] = ctx.baseFeatures.map(feature => (feature, feature.position) )
  val targetIndex: Int = ctx.baseFeatures.indexWhere(_.name == ctx.targetName)
  val timeIndex: Option[Int] = ctx.timeName.map(tn => ctx.baseFeatures.indexWhere(_.name == tn))

  private case class Record(values: List[String])
  private object Record extends SQLSyntaxSupport[Record] {
    override val tableName: String = data.table
    def apply(e: ResultName[Record])(rs: WrappedResultSet): Record =
      Record(ctx.baseFeatures.map(feature => rs.string(e.column(feature.name))))
  }

  private val r = Record.syntax("r")
  private val lines = withSQL {select.from(Record as r)}.map(Record(r.resultName)(_)).list.apply().iterator

  def hasNext: Boolean = lines.hasNext
  def next: Array[String] = lines.next.values.toArray
}