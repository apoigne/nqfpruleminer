package de.fhg.iais.nqfpruleminer

import better.files._
import com.opencsv.CSVParser
import com.typesafe.config.ConfigFactory
import de.fhg.iais.utils.fail
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.JavaConverters._

class Context(configFile: String, val numberOfBestSubgroups: Int, val lengthOfSubgroups: Int) {
  private val config = ConfigFactory.parseFile(configFile.toFile.toJava)
  val separator: Char = try {config.getString("separator").head} catch {case e: Throwable => CSVParser.DEFAULT_SEPARATOR}
  val quoteCharacter: Char = try {config.getString("quoteCharacter").head} catch {case e: Throwable => CSVParser.DEFAULT_QUOTE_CHARACTER}
  val escapeCharacter: Char = try {config.getString("escapeCharacter").head} catch {case e: Throwable => CSVParser.DEFAULT_ESCAPE_CHARACTER}
  val dataFiles: List[String] = try {(config getStringList "dataFiles").asScala.toList} catch {case e: Throwable => fail(e.getLocalizedMessage); List("")}
  val dataFilesHaveHeader: Boolean = try {config getBoolean "dataFilesHaveHeader"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val minimalQuality: Double = try {config getDouble "minimalQuality"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val minP: Double = try {config getDouble "minProbability"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val minG: Double = try {config getDouble "minGenerality"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val computeClosureOfSubgroups: Boolean = try {config getBoolean "computeClosureOfSubgroups"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val refineSubgroups: Boolean = try {config getBoolean "refineSubgroups"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val qualityMode: String = try {config getString "qualityfunction"} catch {case e: Throwable => fail(e.getLocalizedMessage); "Piatetsky"}
  val outputFile: String = try {config getString "outputFile"} catch {case e: Throwable => fail(e.getLocalizedMessage); "result.txt"}
  val dateTimeFormat : String = try { config getString "dateTimeFormat"} catch {case e: Throwable =>"yyyy-MM-dd"}
  val dateTimeFormatter : DateTimeFormatter = DateTimeFormat.forPattern(dateTimeFormat)

  fail(qualityMode == "Piatetsky" || qualityMode == "Binomial" || qualityMode == "Split" ||
    qualityMode == "Pearson" || qualityMode == "Gini", s"Quality mode $qualityMode is not supported.")

  val attributes: List[Attribute] =
    try {
      config.getConfigList("attributes").asScala.toList.map(
        attr => {
          val name = attr.getString("name")
          val hasEntropyBinning = try {attr.getString("binning") == "Entropy"} catch {case e: Throwable => false}
          val typ = DataType(attr.getString("typ"))
          Attribute(name, if (typ == DataType.NUMERIC && hasEntropyBinning) DataType.LNUMERIC else typ)
        }
      )
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); List[Attribute]()
    }

  // target generation
  val targetName: String =
    try {
      config getString "target.name"
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); ""
    }
  val targetGroups: List[String] =
    try {
      config.getStringList("target.groups").asScala.toList
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); List[String]()
    }
  val numberOfTargetGroups: Int = targetGroups.length + 1    // target group 0 is the default group
  fail(attributes.map(_.name).contains(targetName), s"Target attribute $targetName is not contained in the list of feature attributes.")

  val hasNumericValues: Boolean = attributes.exists(attr => attr.typ == DataType.NUMERIC || attr.typ == DataType.LNUMERIC)

  val binning: Map[Attribute, Discretisation] = {
    var map = Map[Attribute, Discretisation]()
    val attributeConfigs = config.getConfigList("attributes").asScala.toList
    for (attr <- attributeConfigs) {
      val name = attr.getString("name")
      val typ = DataType(attr.getString("typ"))
      try {
        val binning = attr.getString("binning")
        binning match {
          case "Entropy" =>
            map = map + (Attribute(name,DataType.LNUMERIC) -> Entropy(name, attr.getInt("bins"), numberOfTargetGroups))
          case "Interval" =>
            map = map + (Attribute(name,typ) -> Intervals(attr.getDoubleList("intervals").asScala.toList.map(_.toDouble)))
          case "EqualWidth" =>
            map = map + (Attribute(name,typ) -> EqualWidth(name, attr.getInt("bins")))
          case "EqualFrequency" =>
            map = map + (Attribute(name,typ) -> EqualFrequency(name, attr.getInt("bins")))
          case x => fail(s"Wrong binning method $x.")
        }
      } catch {
        case _: Throwable =>
          map = map + (Attribute(name,typ) -> NoBinning)
      }
    }
    map
  }

  val usesOverlappingIntervals: Boolean = try {config getBoolean "useOverlappingIntervals"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}

  val delimitersForParallelExecution = List()
}
