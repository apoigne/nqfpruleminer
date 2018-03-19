package de.fhg.iais.nqfpruleminer

import better.files._
import com.opencsv.CSVParser
import com.typesafe.config.{Config, ConfigFactory}
import de.fhg.iais.nqfpruleminer.Expression.{BoolExpr, TRUE}
import de.fhg.iais.nqfpruleminer.Item.Position
import de.fhg.iais.nqfpruleminer.io.Provider
import de.fhg.iais.utils.{TimeFrame, fail, tryFail, tryWithDefault}
import org.joda.time.DateTime
import org.joda.time.format.{DateTimeFormat, DateTimeFormatter, ISODateTimeFormat}

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

class Context(configFile: String) {
  fail(configFile.toFile.exists(), s"Configuration file ${configFile.toFile.path} does not exist")
  private val config =
    Try(ConfigFactory.parseFile(configFile.toFile.toJava)) match {
      case Success(conf) => conf
      case Failure(e) => fail(s"Incorrect configuration file:\n${e.getMessage}"); null
    }

  val outputFile: String = tryFail(config getString "outputFile")
  val outputFormat: String = tryWithDefault(config getString "outputFormat", "txt")

  val providerData: Provider.Data = {
    val providerTyp = tryFail(config getString "provider")
    providerTyp match {
      case "csvReader" =>
        val separator: Char = tryWithDefault(config.getString("csvReader.separator").head, CSVParser.DEFAULT_SEPARATOR)
        val quoteCharacter: Char = tryWithDefault(config.getString("csvReader.quoteCharacter").head, CSVParser.DEFAULT_QUOTE_CHARACTER)
        val escapeCharacter: Char = tryWithDefault(config.getString("csvReader.escapeCharacter").head, CSVParser.DEFAULT_ESCAPE_CHARACTER)
        val dataFilesHaveHeader: Boolean = tryFail(config getBoolean "csvReader.dataFilesHaveHeader")
        val dataFile: String = tryFail(config getString "csvReader.dataFile")
        fail(dataFile.toFile.exists, s"Data file $dataFile does not exist.")
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

  val numberOfBestSubgroups: Int = tryWithDefault(config getInt "numberOfBestSubgroups", 15)
  val lengthOfSubgroups: Int = tryWithDefault(config getInt "lengthOfSubgroups", 3)

  val minimalQuality: Double = tryWithDefault(config getDouble "minimalQuality", 0.0)
  val minP: Double = tryWithDefault(config getDouble "minProbability", 0.0)
  val minG: Double = tryWithDefault(config getDouble "minGenerality", 0.0)
  val qualityMode: String = tryWithDefault(config getString "qualityfunction", "Piatetsky")

  fail(qualityMode == "Piatetsky" || qualityMode == "Binomial" || qualityMode == "Split" ||
    qualityMode == "Pearson" || qualityMode == "Gini", s"Quality mode $qualityMode is not supported.")

  val maxNumberOfItems: Int = tryWithDefault(config getInt "maxNumberOfItems", Int.MaxValue)

  private def genBinnigType(binning: Config) = {
    val mode = binning.getString("mode")
    mode match {
      case "Entropy" => BinningType.ENTROPY(binning.getInt("bins"))
      case "Interval" => BinningType.INTERVAL(binning.getDoubleList("intervals").asScala.toList.map(_.toDouble))
      case "EqualWidth" => BinningType.EQUALWIDTH(binning.getInt("bins"))
      case "EqualFrequency" => BinningType.EQUALFREQUENCY(binning.getInt("bins"))
      case x => fail(s"Wrong binning method $x."); null
    }
  }

  // 'features' comprise the list of all features that are required
  private val features: Vector[Feature] =
    tryFail {
      config.getConfigList("features").asScala.toVector.map(
        feature => {
          val name = feature.getString("attribute")
          val typ = SimpleType(feature.getString("typ"))
          val condition =
            Try(config.getConfig("condition")) match {
              case Success(bexp) => Expression.json2boolExpr(bexp)
              case Failure(_) => TRUE
            }
          if (typ == SimpleType.NUMERIC) {
            val binning = tryWithDefault(Some(feature.getConfig("binning")), None)
            binning match {
              case None => Feature(name, typ, condition)
              case Some(_binning) => Feature(name, genBinnigType(_binning), condition)
            }
          } else {
            Feature(name, typ, condition)
          }
        }
      )

    }

  private val featureNames = features.map(_.name)
  fail(featureNames.lengthCompare(featureNames.distinct.length) == 0, "There are double occurrences of feature names")

  // target generation
  val targetName: String = tryFail(config getString "target.attribute")
  val targetGroups: List[String] = tryFail(config.getStringList("target.labels").asScala.toList)
  implicit val numberOfTargetGroups: Int = targetGroups.length + 1    // target group 0 is the default group

  fail(features.map(_.name).contains(targetName), s"Target feature '$targetName' is not contained in the list of attributes.")

  // TimeFeature generation
  val dateTimeFormatter: DateTimeFormatter =
    tryWithDefault(DateTimeFormat.forPattern(config getString "time.format"), ISODateTimeFormat.dateTime())

  def parseDateTime(value: String): DateTime =
    Try(dateTimeFormatter.parseDateTime(value)) match {
      case Success(v) => v
      case Failure(e) => fail(s"Parsing datetime failed due to incorrect format: ${e.getMessage}"); null
    }

  val timeName: Option[String] = tryWithDefault(Some(config getString "time.attribute"), None)
  if (timeName.nonEmpty)
    fail(features.map(_.name).contains(timeName.get), s"Timestamp feature '$timeName' is not contained in the list of attributes.")

  private val simpleFeaturesNoPosition =
    features
      .filterNot(feature => feature.name == targetName || timeName.isDefined && feature.name == timeName.get)

  private implicit val attributeToPosition: Map[String, Position] =
    simpleFeaturesNoPosition
      .map(_.name)
      .zipWithIndex
      .toMap

  val simpleFeatures: Vector[Feature] =
    simpleFeaturesNoPosition
      .map { case Feature(name, typ, condition, _) => Feature(name, typ, condition.updatePosition, attributeToPosition(name)) }
  //    features
//      .filterNot(feature => feature.name == targetName || timeName.isDefined && feature.name == timeName.get)
//      .zipWithIndex
//      .map { case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position) }
private val attributeToFeature = simpleFeatures.map(feature => feature.name -> feature).toMap
  //  private val attributeToPosition = attributeToFeature.mapValues(_.position)
  private val noFeatures0 = simpleFeatures.length

  val instanceFilter: Expression.BoolExpr =
    Try (config getConfig "instanceFilter") match {
      case Success(config) => Expression.json2boolExpr(config)
      case Failure(_)  => TRUE
    }

  /*
     Checks whether a group definition exists. If yes, it is checked whether the group members are in the list of features, and further,
     if the feature name of the group is not yet used as a feature name

     The intermediate step of introducing groupData is necessary since only after features belonging to exclusive groups are
     eliminated the list of base features and theirt position can be determined and only then the correct positions of group members
     can be defined.
   */
  val groupFeatures: Vector[Feature] = {
    tryWithDefault(config.getConfigList("groups").asScala.toList, Nil)
      .toVector
      .map(
        config => {
          val group: List[String] = Try(config.getStringList("group").asScala.toList) match {
            case Success(_group) =>
              fail(_group.nonEmpty, "There is a group feature with an empty list of grouped features.")
              _group
            case Failure(e) =>
              fail(e.getLocalizedMessage); null
          }
          group.foreach(
            groupElement => {
              fail(features.map(_.name).contains(groupElement),
                s"Group element $groupElement is not contained in the list of features.")
              fail(groupElement == targetName, s"Target attribute $targetName is a group element of a group.")
              timeName.foreach(n => fail(groupElement != n, s"Time feature $n is a group element of a group."))
            }
          )
          val condition =
            Try(config.getConfig("condition")) match {
              case Success(bexp) => Expression.json2boolExpr(bexp)
              case Failure(_) => TRUE
            }
          Feature(s"group(${group.reduce(_ + "," + _)})", DerivedType.GROUP(group.map(attributeToPosition)), condition)
        }
      )
      .zipWithIndex
      .map { case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noFeatures0) }
  }

  private val noFeatures1 = noFeatures0 + groupFeatures.length

  val prefixFeatures: Vector[Feature] =
    tryWithDefault(config.getConfigList("prefixFeatures").asScala.toList, Nil)
      .toVector
      .flatMap(
        config => {
          val name = config.getString("attribute")
          val baseFeature =
            features.find(_.name == name) match {
              case None => fail(s"Prefix features: The name $name does not occur in the list of features."); null
              case Some(f) => f
            }
          fail(baseFeature.typ == SimpleType.NOMINAL, s"Typ of the prefix feature with name $name should be nominal.")
          val prefixes: List[Int] =
            Try(config.getIntList("prefixes").asScala.toList.map(_.toInt)) match {
              case Failure(_) => fail(s"No prefixes defined for prefix feature $name."); null
              case Success(l) => l
            }
          val condition =
            Try(config.getConfig("condition")) match {
            case Success(bexp) => Expression.json2boolExpr(bexp)
            case Failure(_) => TRUE
          }
          prefixes.map(number => Feature(s"${name}_prefix_$number", DerivedType.PREFIX(number, attributeToPosition(name)), condition))
        }
      )
      .zipWithIndex
      .map { case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noFeatures1) }

  private val noFeatures2 = noFeatures1 + prefixFeatures.length

  val rangeFeatures: Vector[Feature] =
    tryWithDefault(config.getConfigList("rangeFeatures").asScala.toList, Nil)
      .toVector
      .flatMap(
        config => {
          val name = config.getString("attribute")
          val baseFeature =
            features.find(_.name == name) match {
              case None => fail(s"Range features: The name $name does not occur in the list of features."); null
              case Some(f) => f
            }
          fail(baseFeature.typ == SimpleType.NUMERIC, s"Typ of the prefix feature with name $name should be numeric.")
          val rangeConfigs: List[Config] =
            Try(config.getConfigList("ranges").asScala.toList) match {
              case Failure(_) => fail(s"No prefixes defined for prefix feature $name."); null
              case Success(rangesList) => rangesList
            }
          val condition =
            Try(config.getConfig("condition")) match {
            case Success(bexp) => Expression.json2boolExpr(bexp)
            case Failure(_) => TRUE
          }
          rangeConfigs
            .map(
              range => {
                val lo = tryFail(range.getDouble("lo"))
                val hi = tryFail(range.getDouble("hi"))
                Feature(s"${name}range($lo,$hi)", DerivedType.RANGE(lo, hi, attributeToPosition(name)), condition)
              }
            )
        }
      )
      .zipWithIndex
      .map { case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noFeatures2) }

  private val noFeatures3 = noFeatures2 + rangeFeatures.length

  val aggregateFeatures: Vector[Feature] =
    tryWithDefault(config.getConfigList("aggregators").asScala.toList, Nil)
      .toVector
      .flatMap(
        config => {
          val seqId = tryWithDefault(Some(config.getString("sequenceIdAttribute")), None)
          if (seqId.nonEmpty)
            fail(features.map(_.name).contains(seqId.get),
              s"The seqId attribute '$seqId' of an aggregation field is not contained in the list of attributes.")
          val operator = tryFail(AggregationOp(config.getString("operator").toLowerCase))
          val attributes =
            if (operator == AggregationOp.Count)
              config.getStringList("attributes").asScala.toList
            else
              List(config.getString("attribute"))
          attributes.foreach(
            attribute =>
              fail(features.map(_.name).contains(attribute),
                s"The attribute '$attribute' of an aggregation field is not contained in the list of attributes.")
          )
          val existsOnly = tryFail(config.getBoolean("existsOnly"))
          import de.fhg.iais.nqfpruleminer.Expression._
          val timeFrames = tryWithDefault(config.getStringList("history").asScala.toList, Nil)
          val binning = tryWithDefault(Some(genBinnigType(config.getConfig("binning"))), None)
          val tfs = timeFrames.map(
            timeFrame => {
              timeFrame.last match {
                case 'n' =>
                  val n = timeFrame.take(timeFrame.length - 1).toInt
                  fail(n > 0, s"A time frame of an aggregate feature is less than or equal 0.")
                  (timeFrame, Left(n))
                case _ =>
                  val tf = TimeFrame(timeFrame)
                  fail(tf > 0, s"A time frame of an aggregate feature is less than or equal 0.")
                  (timeFrame, Right(tf))
              }
            }
          )
          val seqIdPos = seqId.map(attributeToPosition(_))
          if (operator != AggregationOp.Count) {
            fail(attributeToFeature(attributes.head).typ == SimpleType.NUMERIC,
              s"Attribute ${attributeToFeature(attributes.head).name} for aggregate feature is not numeric.")
          }
          val positions = attributes.map(attributeToPosition)
          val condition =
            (x: Numeric) =>
              (Try(config.getConfig("condition")) match {
                case Success(bexp) =>
                  Expression.json2boolExpr(bexp)
                case Failure(_) => TRUE
              })
                .updatePosition(Map("self" -> 0))
                .eval(Vector(x))
          tfs.map {
            case (timeFrame, tf) =>
              val name = s"Aggregate($timeFrame)"
              if (operator == AggregationOp.Count) {
                Feature(name, DerivedType.COUNT(seqIdPos, positions, existsOnly, condition, binning, tf), TRUE)
              } else {
                Feature(name, DerivedType.AGGREGATE(seqIdPos, positions.head, operator, existsOnly, condition, binning, tf), TRUE)
              }
          }
        }
      )
      .zipWithIndex
      .map {
        case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noFeatures3)
      }

  val requiresAggregation: Boolean = aggregateFeatures.nonEmpty

  val allFeatures: Vector[Feature] = simpleFeatures ++ groupFeatures ++ aggregateFeatures ++ prefixFeatures

  private def typ2binning(typ: BinningType): Discretization =
    typ match {
      case BinningType.ENTROPY(bins) => Entropy(bins)
      case BinningType.INTERVAL(delimiters) => Intervals(delimiters)
      case BinningType.EQUALWIDTH(bins) => EqualWidth(bins)
      case BinningType.EQUALFREQUENCY(bins) => EqualFrequency(bins)
    }

  val binning: Map[Int, Discretization] =
    simpleFeatures.flatMap(
      feature =>
        feature.typ match {
          case aggr: DerivedType.AGGREGATE => aggr.binning.map(typ => aggr.position -> typ2binning(typ))
          case typ: BinningType => Some(feature.position -> typ2binning(typ))
          case _ => None
        }
    ).toMap

  val hasFeaturesToBin: Boolean = binning.nonEmpty

  val rule: BoolExpr =
    Try(config.getConfig("filter")) match {
      case Success(config) => Expression.json2boolExpr(config)
      case Failure(_) => TRUE
    }

  // Program switches
  val refineSubgroups: Boolean = tryWithDefault(config getBoolean "refineSubgroups", false)
  val usesOverlappingIntervals: Boolean = tryWithDefault(config getBoolean "useOverlappingIntervals", false)
  val computeClosureOfSubgroups: Boolean = tryWithDefault(config getBoolean "computeClosureOfSubgroups", false)

  private val delimitersParEx: List[Int] =
    0 +: tryWithDefault((config getIntList "delimitersForParallelExecution").asScala.toList.map(_.toInt), List[Int]()) :+ 100

  def delimiterRangesForParallelExecution(upper: Int): List[Range] = {
    val l = delimitersParEx.map(_ * upper / 100)
    l.take(l.length - 1).zip(l.drop(1)).map {
      case (x, y) => Range(x, y)
    }
  }

  val numberOfWorkers: Int = tryWithDefault(config getInt "numberOfWorkers", 1)
  val multiThreading = tryWithDefault(config getBoolean "multiThreading", true)
}
