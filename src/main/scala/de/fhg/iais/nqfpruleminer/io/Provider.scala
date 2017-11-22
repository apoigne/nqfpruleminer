package de.fhg.iais.nqfpruleminer.io

import java.io.FileReader

import com.opencsv.CSVReader
import de.fhg.iais.nqfpruleminer.{Attribute, Context}
import de.fhg.iais.utils.fail

object Provider {
  trait Data
  case class Csv(dataFile: String, hasHeader: Boolean, separator: Char, quoteCharacter: Char, escapeCharacter: Char) extends Data
  case class MySql(host: String, port: Int, database: String, table : String, user: String, password: String)extends Data

  def apply(data: Data)(implicit ctx: Context) : Provider=
    data match {
      case data: Csv => new CsvProvider(data)
      case data : MySql => new MySqlProvider(data)
    }
}

trait Provider {
  def hasNext: Boolean
  def next: Array[String]
  val targetIndex: Int
  val columnsUsed: List[(Attribute, Int)]
}

class CsvProvider(data: Provider.Csv)(implicit ctx: Context) extends Provider {
  private val lines = new CSVReader(new FileReader(data.dataFile), data.separator, data.quoteCharacter, data.escapeCharacter).iterator

  private val header = if (data.hasHeader) lines.next() else Array[String]()

  val columnsUsed: List[(Attribute, Int)] =
    ctx.attributes.filterNot(_.name == ctx.targetName).zipWithIndex.map {
      case (attr, index) =>
        if (header.nonEmpty) {
          val index = header.indexOf(attr.name)
          if (index < 0) {
            fail(s"Attribute ${attr.name} does not occur in the header of ${data.dataFile}")
            (attr, -1)
          } else {
            (attr, index)
          }
        } else {
          (attr, index)
        }
    }

  val targetIndex: Int =
    if (header.nonEmpty)
      header.indexOf(ctx.targetName)
    else
      ctx.attributes.indexWhere(_.name == ctx.targetName)

  def hasNext: Boolean = lines.hasNext
  def next: Array[String] = lines.next
}

class MySqlProvider(data: Provider.MySql)(implicit ctx: Context) extends Provider {
  import scalikejdbc._

  Class.forName("com.mysql.jdbc.Driver")
  ConnectionPool.singleton(s"jdbc:mysql://${data.host}:${data.port}/${data.database}", data.user, data.password)
  implicit val session: AutoSession = AutoSession

  val columnsUsed: List[(Attribute, Int)] = ctx.attributes.filterNot(_.name == ctx.targetName).zipWithIndex
  val targetIndex: Int = ctx.attributes.indexWhere(_.name == ctx.targetName)

  private case class Record(values: List[String])
  private object Record extends SQLSyntaxSupport[Record] {
    override val tableName : String = data.table
    def apply(e: ResultName[Record])(rs: WrappedResultSet): Record =
      Record(ctx.attributes.map(attr => rs.string(e.column(attr.name))))
  }

  private val r = Record.syntax("r")
  private val lines = withSQL {select.from(Record as r)}.map(Record(r.resultName)(_)).list.apply().iterator

  def hasNext: Boolean = lines.hasNext
  def next: Array[String] = lines.next.values.toArray
}