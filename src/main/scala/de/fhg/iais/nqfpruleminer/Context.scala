package de.fhg.iais.nqfpruleminer

import better.files._
import com.opencsv.CSVParser
import com.typesafe.config.{Config, ConfigFactory}
import de.fhg.iais.nqfpruleminer.DataType.GROUP
import de.fhg.iais.nqfpruleminer.io.Provider
import de.fhg.iais.utils.fail
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.JavaConverters._

class Context(configFile: String, val numberOfBestSubgroups: Int, val lengthOfSubgroups: Int) {
  private val config = ConfigFactory.parseFile(configFile.toFile.toJava)

  val minimalQuality: Double = try {config getDouble "minimalQuality"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val minP: Double = try {config getDouble "minProbability"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val minG: Double = try {config getDouble "minGenerality"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val computeClosureOfSubgroups: Boolean = try {config getBoolean "computeClosureOfSubgroups"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val refineSubgroups: Boolean = try {config getBoolean "refineSubgroups"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val qualityMode: String = try {config getString "qualityfunction"} catch {case e: Throwable => fail(e.getLocalizedMessage); "Piatetsky"}

  val outputFile: String = try {config getString "outputFile"} catch {case e: Throwable => fail(e.getLocalizedMessage); "result"}
  val outputFormat: String = try {config getString "outputFormat"} catch {case e: Throwable => fail(e.getLocalizedMessage); "txt"}
  val dateTimeFormat: String = try {config getString "dateTimeFormat"} catch {case e: Throwable => "yyyy-MM-dd"}
  val dateTimeFormatter: DateTimeFormatter = DateTimeFormat.forPattern(dateTimeFormat)

  fail(outputFile.toFile.path.getParent.toFile.exists(), s"Path of output file ${outputFile.toFile.path.getParent} does not exíst.")
  fail(outputFormat == "txt" || outputFormat == "json", s"Output format $outputFormat not supported.")

  fail(qualityMode == "Piatetsky" || qualityMode == "Binomial" || qualityMode == "Split" ||
    qualityMode == "Pearson" || qualityMode == "Gini", s"Quality mode $qualityMode is not supported.")

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

  val attributes: List[Attribute] =
    try {
      val attributes =
        config.getConfigList("attributes").asScala.map(
          attr => {
            val name = attr.getString("name")
            val hasEntropyBinning = try {attr.getString("binning") == "Entropy"} catch {case e: Throwable => false}
            val typ = DataType(attr.getString("typ"))
            Attribute(name, if (typ == DataType.NUMERIC && hasEntropyBinning) DataType.LNUMERIC else typ)
          }
        )

      val attributeNames = attributes.map(_.name)
      fail(attributeNames.length == attributeNames.distinct.length, "There are double occurrences of attribute names")
      attributes.toList
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); List[Attribute]()
    }

  fail(attributes.map(_.name).contains(targetName), s"Target attribute $targetName is not contained in the list of feature attributes.")

  val groups: List[Attribute] =
    try {
      val groups =
        config.getConfigList("groups").asScala.toList.map(
          attr => {
            val name = attr.getString("name")
            val group = attr.getStringList("group").asScala.toList
            Attribute(name, DataType.GROUP(group))
          }
        )

      val attributeNames = attributes.map(_.name)
      val allGroupedAttributeNames = groups.flatMap(_.typ match { case GROUP(names) => names; case _ => Nil })
      fail(attributeNames.distinct.length >= allGroupedAttributeNames.distinct.length,
        "There are attributes in the list of grouped features that do not occur in the list of attributes")

      val nominalAttributeNames = attributes.filter(_.typ == DataType.NOMINAL).map(_.name)
      fail(allGroupedAttributeNames.filterNot(nominalAttributeNames.contains(_)) == Nil,
        "The list of grouped features contains featurtes that are not nominal")
      groups
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); Nil
    }

  fail(!groups.map(_.name).contains(targetName), s"Target attribute $targetName is contained in some grouped features.")

  val attributesUsed: List[Attribute] = {
    val allGroupedAttributeNames = groups.flatMap(_.typ match { case GROUP(names) => names; case _ => Nil })
    attributes.filterNot(attr => allGroupedAttributeNames.contains(attr.name))
  }

  val sizeOfRow: Int = attributesUsed.length + groups.length // -1 to get reid if the target attribute

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
            map = map + (Attribute(name, DataType.LNUMERIC) -> Entropy(name, attr.getInt("bins"), numberOfTargetGroups))
          case "Interval" =>
            map = map + (Attribute(name, typ) -> Intervals(attr.getDoubleList("intervals").asScala.toList.map(_.toDouble)))
          case "EqualWidth" =>
            map = map + (Attribute(name, typ) -> EqualWidth(name, attr.getInt("bins")))
          case "EqualFrequency" =>
            map = map + (Attribute(name, typ) -> EqualFrequency(name, attr.getInt("bins")))
          case x =>
            fail(s"Wrong binning method $x.")
        }
      } catch {
        case _: Throwable =>
          map = map + (Attribute(name, typ) -> NoBinning)
      }
    }
    map
  }

  val usesOverlappingIntervals: Boolean = try {config getBoolean "useOverlappingIntervals"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}

  val delimitersForParallelExecution = List()

  val rule: Rule =
    try {
      OrRule(toRules(config.getConfigList("rules").asScala.toList): _*)
    } catch {
      case _: Throwable =>
        NoRule
    }

  def toRules(rules: List[Config]): List[Rule] = rules.map(toRule)

  def toRule(rule: Config): Rule =
    try {
      rule.getString("typ") match {
        case "Nominal" => ItemRule(rule.getString("name"), Value(DataType.NOMINAL, rule.getString("value"), 0)(this))
        case "Numerical" => ItemRule(rule.getString("name"), Value(DataType.NUMERIC, rule.getString("value"), 0)(this))
        case "Boolean" => ItemRule(rule.getString("name"), Value(DataType.BOOLEAN, rule.getString("value"), 0)(this))
        case "Date" => ItemRule(rule.getString("name"), Value(DataType.DATE, rule.getString("value"), 0)(this))
        case "Range" => ItemRule(rule.getString("name"), Range(rule.getString("lo").toDouble, rule.getString("hi").toDouble))
        case "Or" => OrRule(toRules(rule.getConfigList("rules").asScala.toList): _*)
        case "And" => AndRule(toRules(rule.getConfigList("rules").asScala.toList): _*)
      }
    } catch {
      case _: Throwable => fail(s"Ill formed rule'$rule'"); NoRule
    }

  val providers: List[Provider.Data] = {
    val providerTyp = try {config getString "provider"} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
    providerTyp match {
      case "csvReader" =>
        val separator: Char = try {config.getString("csvReader.separator").head} catch {case e: Throwable => CSVParser.DEFAULT_SEPARATOR}
        val quoteCharacter: Char = try {config.getString("csvReader.quoteCharacter").head} catch {case e: Throwable => CSVParser.DEFAULT_QUOTE_CHARACTER}
        val escapeCharacter: Char = try {config.getString("csvReader.escapeCharacter").head} catch {case e: Throwable => CSVParser.DEFAULT_ESCAPE_CHARACTER}
        val dataFilesHaveHeader: Boolean = try {config getBoolean "csvReader.dataFilesHaveHeader"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
        val dataFiles: List[String] = try {(config getStringList "csvReader.dataFiles").asScala.toList} catch {case e: Throwable => fail(e.getLocalizedMessage); List("")}
        dataFiles.map(dateFile => Provider.Csv(dateFile, dataFilesHaveHeader, separator, quoteCharacter, escapeCharacter))
      case "mySQLDB" =>
        val host: String = try {config.getString("mySQLDB.host")} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
        val port: Int = try {config.getInt("mySQLDB.port")} catch {case e: Throwable => fail(e.getLocalizedMessage); 0}
        val db: String = try {config.getString("mySQLDB.database")} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
        val table: String = try {config.getString("mySQLDB.table")} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
        val user: String = try {config.getString("mySQLDB.user")} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
        val password: String = try {config.getString("mySQLDB.password")} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
        List(Provider.MySql(host, port, db, table, user, password))
    }
  }
}
