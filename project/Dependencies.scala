import sbt._

object Dependencies {

  private val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  private val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
  private val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"

  val allResolvers: Seq[MavenRepository] = Seq(typesafe, sonatype, mvnrepository)

  object Version {
    val opencsv = "3.8"
    val scalaTest = "3.0.8"
    val sprayJson = "1.3.4"
    val akka = "2.5.23"
    val config = "1.3.2"
    val clist = "3.5.1"
    val betterFiles = "3.8.0"
    val jodaTime = "2.9.9"
    val quill = "2.1.0"
    val mySql = "5.1.38"
    val mySqlConnector = "8.0.8-dmr"
    val h2 = "1.4.196"
    val scalikejJDCB = "3.1.0"
    val logback = "1.2.3"
    val fastParse = "2.1.3"
  }

  private val scalaTest = "org.scalatest" %% "scalatest" % Version.scalaTest
  private val sprayJson = "io.spray" % "spray-json_2.12" % Version.sprayJson
  private val config = "com.typesafe" % "config" % Version.config
  private val akkaActor = "com.typesafe.akka" %% "akka-actor" % Version.akka
  private val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.akka
  private val opencsv = "com.opencsv" % "opencsv" % Version.opencsv
  private val clistCore = "org.backuity.clist" %% "clist-core" % Version.clist
  private val clistMacros = "org.backuity.clist" %% "clist-macros" % Version.clist
  private val betterFiles = "com.github.pathikrit" %% "better-files" % Version.betterFiles
  private val jodaTime = "joda-time" % "joda-time" % Version.jodaTime
  private val quillSql = "io.getquill" %% "quill-sql" % Version.quill
  private val quillJdbc = "io.getquill" %% "quill-jdbc" % Version.quill
  private val h2 = "com.h2database" % "h2" % Version.h2
  private val mySqlConnector = "mysql" % "mysql-connector-java" % Version.mySqlConnector
  private val scalikejJDCB = "org.scalikejdbc" %% "scalikejdbc" % Version.scalikejJDCB
  private val logback = "ch.qos.logback" % "logback-classic" % Version.logback
  private val fastParse = "com.lihaoyi" %% "fastparse" % Version.fastParse

  val allDependencies: Seq[ModuleID] = Seq(
    akkaActor,
    akkaTestkit,
    scalaTest % "test",
    sprayJson,
    config,
    opencsv,
    clistCore,
    clistMacros % "provided",
    betterFiles,
    jodaTime,
//    quillSql,
//    quillJdbc,
//    h2 % "test",
    mySqlConnector,
    scalikejJDCB,
    logback,
    fastParse
  )
}
