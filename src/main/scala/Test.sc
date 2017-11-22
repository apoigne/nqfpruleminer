import scalikejdbc._

Class.forName("com.mysql.jdbc.Driver")
ConnectionPool.singleton("jdbc:mysql://localhost:3306/connect4", "root", "poignepoigne")
implicit val session = AutoSession

val x = List("a1", "a2", "a3", "a4")

val xxx = "connect4"

case class Record(values: List[String])
object Record extends SQLSyntaxSupport[Record] {
  override val tableName = xxx
  def apply(e: ResultName[Record])(rs: WrappedResultSet): Record =
    Record(x.map(n => rs.string(e.column(n))))
}

val r = Record.syntax("r")
val records = withSQL {select.from(Record as r)}.map(Record(r.resultName)(_)).list.apply()

records.head.values.head == "b"

