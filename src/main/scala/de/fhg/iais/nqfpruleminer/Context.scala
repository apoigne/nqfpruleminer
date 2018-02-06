package de.fhg.iais.nqfpruleminer

import better.files._
import com.opencsv.CSVParser
import com.typesafe.config.{Config, ConfigFactory}
import de.fhg.iais.nqfpruleminer.BinningType.NOBINNING
import de.fhg.iais.nqfpruleminer.io.Provider
import de.fhg.iais.utils.{TimeFrame, fail, tryFail, tryWithDefault}
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object AggregationOp extends Enumeration {
  val Count, Sum, Max, Min, Mean = Value

  def apply(op: String): AggregationOp.Value =
    op match {
      case "count" => Count
      case "sum" => Sum
      case "max" => Max
      case "min" => Min
      case "mean" => Mean
      case x => fail(s"Aggregation operator $x is not supported."); null
    }
}

class Context(configFile: String, val numberOfBestSubgroups: Int, val lengthOfSubgroups: Int) {
  fail(configFile.toFile.exists(), s"Configuration file ${configFile.toFile.path} does not exist")
  private val config = tryFail(ConfigFactory.parseFile(configFile.toFile.toJava))

  val outputFile: String = tryFail(config getString "outputFile")
  val outputFormat: String = tryWithDefault(config getString "outputFormat", "txt")
  val dateTimeFormat: String = tryWithDefault(config getString "dateTimeFormat", "yyyy-MM-dd HH:mm:ss")
  val dateTimeFormatter: DateTimeFormatter = DateTimeFormat.forPattern(dateTimeFormat)

  def parseDateTime(value: String): DateTime =
    Try(dateTimeFormatter.parseDateTime(value)) match {
      case Success(v) => v
      case Failure(e) => fail(s"Parsing datetime failed due to incorrect format: ${e.getMessage}"); null
    }

  val providerData: Provider.Data = {
    val providerTyp = tryFail(config getString "provider")
    providerTyp match {
      case "csvReader" =>
        val separator: Char = tryWithDefault(config.getString("csvReader.separator").head, CSVParser.DEFAULT_SEPARATOR)
        val quoteCharacter: Char = tryWithDefault(config.getString("csvReader.quoteCharacter").head, CSVParser.DEFAULT_QUOTE_CHARACTER)
        val escapeCharacter: Char = tryWithDefault(config.getString("csvReader.escapeCharacter").head, CSVParser.DEFAULT_ESCAPE_CHARACTER)
        val dataFilesHaveHeader: Boolean = tryFail(config getBoolean "csvReader.dataFilesHaveHeader")
        val dataFile: String = tryFail(config getString "csvReader.dataFile")
        Provider.Csv(dataFile, dataFilesHaveHeader, separator, quoteCharacter, escapeCharacter)
      case "mySQLDB" =>
        val host: String = tryFail(config getString "mySQLDB.host")
        val port: Int = tryFail(config getInt "mySQLDB.port")
        val db: String = tryFail(config getString "mySQLDB.database")
        val table: String = tryFail(config getString "mySQLDB.table")
        val user: String = tryFail(config getString "mySQLDB.user")
        val password: String = tryFail(config getString "mySQLDB.password")
        Provider.MySql(host, port, db, table, user, password)
    }
  }

  fail(outputFile.toFile.path.getParent.toFile.exists(), s"Path of output file ${outputFile.toFile.path.getParent} does not exíst.")
  fail(outputFormat == "txt" || outputFormat == "json", s"Output format $outputFormat not supported.")

  val minimalQuality: Double = tryWithDefault(config getDouble "minimalQuality", 0.0)
  val minP: Double = tryWithDefault(config getDouble "minProbability", 0.0)
  val minG: Double = tryWithDefault(config getDouble "minGenerality", 0.0)
  val qualityMode: String = tryWithDefault(config getString "qualityfunction", "Piatetsky")

  fail(qualityMode == "Piatetsky" || qualityMode == "Binomial" || qualityMode == "Split" ||
    qualityMode == "Pearson" || qualityMode == "Gini", s"Quality mode $qualityMode is not supported.")

  val maxNumberOfItems: Int = tryWithDefault(config getInt "maxNumberofItems", Int.MaxValue)

  // 'features' comprise the list of all features that are required
  private def genBinnigType(binning: Config) = {
    val mode = binning.getString("mode")
    mode match {
      case "NoBinning" => BinningType.NOBINNING
      case "Entropy" => BinningType.ENTROPY(binning.getInt("bins"))
      case "Interval" => BinningType.INTERVAL(binning.getDoubleList("intervals").asScala.toList.map(_.toDouble))
      case "EqualWidth" => BinningType.EQUALWIDTH(binning.getInt("bins"))
      case "EqualFrequency" => BinningType.EQUALFREQUENCY(binning.getInt("bins"))
      case x => fail(s"Wrong binning method $x."); null
    }
  }

  private val features: List[Feature] =
    tryFail {
      val features =
        config.getConfigList("features").asScala.toList.map(
          feature => {
            val name = feature.getString("name")
            val typ = SimpleType(feature.getString("typ"))
            if (typ == SimpleType.NUMERIC) {
              val binning = tryWithDefault(Some(feature.getConfig("binning")), None)
              binning match {
                case None => Feature(name, typ)
                case Some(_binning) => Feature(name, genBinnigType(_binning))
              }
            } else {
              Feature(name, typ)
            }
          }
        )
      val featureNames = features.map(_.name)
      fail(featureNames.lengthCompare(featureNames.distinct.length) == 0, "There are double occurrences of feature names")
      features
    }

  // target generation
  val targetName: String = tryFail(config getString "target.name")
  val targetGroups: List[String] = tryFail(config.getStringList("target.labels").asScala.toList)
  implicit val numberOfTargetGroups: Int = targetGroups.length + 1    // target group 0 is the default group
  fail(features.map(_.name).contains(targetName), s"Target feature '$targetName' is not contained in the list of feature attributes.")

  // TimeFeature generation
  val timeName: Option[String] = tryWithDefault(Some(config getString "time.name"), None)
  fail(timeName.isEmpty || features.map(_.name).contains(timeName.get),
    s"Timestamp feature '$timeName' is not contained in the list of feature attributes.")

  val featuresFiltered: List[Feature] =
    features.filterNot(feature => feature.name == targetName || timeName.isDefined && feature.name == timeName.get)

  /*
     Checks whether a group definition exists. If yes, it is checked whether the group members are in the list of features, and further,
     if the feature name of the group is not yet used as a feature name

     The intermediate step of introducing groupData is necessary since only after features belonging to exclusive groups are
     eliminated the list of base features and theirt position can be determined and only then the correct positions of group members
     can be defined.
   */
  val groupData: List[(String, List[String], Boolean)] = {
    val groups = tryWithDefault(config.getConfigList("derivedFeatures.groups").asScala.toList, Nil)
    tryFail(
      groups.map(
        config => {
          val name = config.getString("name")
          fail(features.map(_.name).contains(name), s"Name of the group $name is already used as a feature name.")
          val group = config.getStringList("group").asScala.toList
          group.foreach(groupElement => {
            fail(!features.map(_.name).contains(groupElement),
              s"Group element $groupElement of group $group is not contained in the list of features.")
            fail(groupElement != targetName, s"Target attribute $targetName is contained in some grouped feature.")
            timeName.foreach(n => fail(groupElement != n, s"Target attribute $targetName is contained in some grouped feature."))
          }
          )
          val exclusive = tryWithDefault(config.getBoolean("exclusive"), true)
          (name, group, exclusive)
        }
      )
    )
  }

  private val allGroupedFeatureNames =
    groupData.flatMap {
      case (_, group, exclusive) => if (exclusive) group else Nil
      case _ => Nil
    }

  // TODO ask whether groups are additional or exclusive
  val baseFeatures: List[Feature] =
    featuresFiltered
      .filterNot(feature => allGroupedFeatureNames.contains(feature.name))
      .zipWithIndex
      .map { case (Feature(name, typ, _), position) => Feature(name, typ, position) }

  private val attributeToFeature = baseFeatures.map(feature => feature.name -> feature).toMap
  private val attributeToPosition = attributeToFeature.mapValues(_.position)
  private val noBaseFeatures = baseFeatures.length

  val groupFeatures: List[Feature] = {
    tryFail(
      {
        val groupFeatures =
          groupData.map {
            case (name, group, exclusive) =>
              Feature(name, DerivedType.GROUP(group.map(attributeToPosition), exclusive))
          }
        groupFeatures.zipWithIndex.map { case (Feature(name, typ, _), position) => Feature(name, typ, position + noBaseFeatures) }
      }
    )
  }

  private val noGroupFeatures = groupFeatures.length

  // TODO: timeFrame by number of Instances
  val aggregateFeatures: List[Feature] = {
    val aggregators = tryWithDefault(config.getConfigList("derivedFeatures.aggregators").asScala.toList, Nil)
    val noOfFeatures = noBaseFeatures + noGroupFeatures
    val aggregateFeatures: List[Feature] =
      aggregators.flatMap(
        aggr => {
          val aggregationField = tryFail(aggr.getString("aggregationField"))
          val seqId = tryWithDefault(Some(aggr.getString("seqId")), None)
          val seqIdPos = seqId.map(attributeToPosition(_))
          val operator = tryFail(AggregationOp(aggr.getString("operator").toLowerCase))
          val attributes =
            if (operator == AggregationOp.Count)
              aggr.getStringList("attributes").asScala.toList
            else
              List(aggr.getString("attribute"))
          if (operator != AggregationOp.Count) {
            fail(attributeToFeature(attributes.head).typ == SimpleType.NUMERIC,
              s"Attribute ${attributeToFeature(attributes.head).name} for derived feature $aggregationField is not numeric.")
          }
          val positions = attributes.map(attributeToPosition)
          val existsOnly = tryFail(aggr.getBoolean("existsOnly"))
          val condition = tryWithDefault(toRule(aggr.getConfig("condition")), NoRule)
          val timeFrames = tryFail(aggr.getStringList("timeframes").asScala.toList)
          val binning = tryWithDefault(genBinnigType(aggr.getConfig("binning")), NOBINNING)
          timeFrames.map(
            timeFrame => {
              val tf = TimeFrame(timeFrame)
              if (tf <= 0) fail(s"A time frame of derived feature $aggregationField is less than or equal 0.")
              val name = s"Aggregate($timeFrame)"
              if (operator == AggregationOp.Count)
                Feature(name, DerivedType.COUNT(seqIdPos, positions, existsOnly, condition, binning, tf))
              else
                Feature(name, DerivedType.AGGREGATE(seqIdPos, positions.head, operator, existsOnly, condition, binning, tf))
            }
          )
        })
    aggregateFeatures.zipWithIndex.map { case (Feature(name, typ, _), position) => Feature(name, typ, position + noOfFeatures) }
  }

  val requiresAggregation: Boolean = aggregateFeatures.nonEmpty

  private val allFeatures: List[Feature] = featuresFiltered ++ groupFeatures ++ aggregateFeatures
  val featureAtPosition: IndexedSeq[Feature] = allFeatures.toIndexedSeq

  private def typ2binning(typ: BinningType) =
    typ match {
      case BinningType.ENTROPY(bins) => Entropy(bins)
      case BinningType.INTERVAL(delimiters) => Intervals(delimiters)
      case BinningType.EQUALWIDTH(bins) => EqualWidth(bins)
      case BinningType.EQUALFREQUENCY(bins) => EqualFrequency(bins)
      case _ => NoBinning
    }

  val binning: Map[Int, Discretization] =
    featuresFiltered.map(
      feature =>
        feature.typ match {
          case aggr: DerivedType.AGGREGATE => aggr.position -> typ2binning(aggr.binning)
          case typ: BinningType => feature.position -> typ2binning(typ)
          case _ => feature.position -> NoBinning
        }
    ).toMap

  val hasFeaturesToBin: Boolean = binning.nonEmpty

  val rule: Rule = tryWithDefault(OrRule(toRules(config.getConfigList("rules").asScala.toList): _*)(this), NoRule)

  def toRules(rules: List[Config]): List[Rule] = rules.map(toRule)

  private def toRule(rule: Config): Rule =
    Try(rule.getString("op")) match {
      case Success("eq") => EqRule(Value(rule.getString("arg"))(this))
      case Success("gt") => CompRule(Comparator.GT, rule.getString("arg").toDouble)
      case Success("ge") => CompRule(Comparator.GE, rule.getString("arg").toDouble)
      case Success("lt") => CompRule(Comparator.LT, rule.getString("arg").toDouble)
      case Success("le") => CompRule(Comparator.LE, rule.getString("arg").toDouble)
      case Success("or") => OrRule(toRules(rule.getConfigList("rules").asScala.toList): _*)(this)
      case Success("and") => AndRule(toRules(rule.getConfigList("rules").asScala.toList): _*)(this)
      case Success(op) => fail(s"Ill formed operator'$op'."); NoRule
      case Failure(e) => fail(s"Ill formed rule'$rule': ${e.getLocalizedMessage}"); NoRule
    }

  // Program switches
  val refineSubgroups: Boolean = tryWithDefault(config getBoolean "refineSubgroups", false)
  val usesOverlappingIntervals: Boolean = tryWithDefault(config getBoolean "useOverlappingIntervals", false)
  val computeClosureOfSubgroups: Boolean = tryWithDefault(config getBoolean "computeClosureOfSubgroups", false)

  val delimitersForParallelExecution = List(50, 60, 65, 70, 75, 80, 90)

  def delimiterRangesForParallelExecution(upper: Int): List[Range] =
    (delimitersForParallelExecution :+ upper).foldLeft((0, List[Range]()))((acc, n) => (n, acc._2 :+ Range(acc._1, n)))._2

  val numberOfWorkers: Int = tryWithDefault(config getInt "numberOfWorkers", 1)
}
