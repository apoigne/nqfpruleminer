package de.fhg.iais.nqfpruleminer

import better.files._
import com.opencsv.CSVParser
import com.typesafe.config.{Config, ConfigFactory}
import de.fhg.iais.nqfpruleminer.Expression.TRUE
import de.fhg.iais.nqfpruleminer.Item.Position
import de.fhg.iais.nqfpruleminer.io.Provider
import de.fhg.iais.utils._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

object AggregationOp extends Enumeration {
  val exists, count, sum, max, min, mean = Value

  def apply(op: String): AggregationOp.Value =
    op.toLowerCase() match {
      case "exists" => exists
      case "count" => count
      case "sum" => sum
      case "max" => max
      case "min" => min
      case "mean" => mean
      case x => fail(s"Aggregation operator $x is not supported."); null
    }
}

class Context(configFile: String) {
  fail(configFile.toFile.exists(), s"Configuration file ${configFile.toFile.path} does not exist")

  private val config: Config =
    Try(ConfigFactory.parseFile(configFile.toFile.toJava)) match {
      case Success(conf) => conf
      case Failure(e) => fail(s"Incorrect configuration file:\n${e.getMessage}"); null
    }

  val configFileName: String = configFile.toFile.nameWithoutExtension

  val statisticsOnly: Boolean = tryWithDefault(config.getBoolean("statisticsOnly"), false)

  val outputFile: String = tryWithDefault(config getString "outputFile", s"${configFileName}_result")
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
    qualityMode == "Pearson" || qualityMode == "Gini" || qualityMode == "Lift",
    s"Quality mode $qualityMode is not supported.")

  val maxNumberOfItems: Int = tryWithDefault(config getInt "maxNumberOfItems", Int.MaxValue)
  val refineSubgroups: Boolean = tryWithDefault(config getBoolean "refineSubgroups", false)
  val computeClosureOfSubgroups: Boolean = tryWithDefault(config getBoolean "computeClosureOfSubgroups", false)

  val numberOfWorkers: Int = tryWithDefault(config getInt "numberOfWorkers", 1)
  private val delimitersParEx: List[Int] =
    0 +: tryWithDefault((config getIntList "delimitersForParallelExecution").asScala.toList.map(_.toInt), List[Int]()) :+ 100

  def delimiterRangesForParallelExecution(upper: Int): List[Range] = {
    val l = delimitersParEx.map(_ * upper / 100)
    l.take(l.length - 1).zip(l.drop(1)).map {
      case (x, y) => Range(x, y)
    }
  }

  private def genBinnigType(s: String, binning: Config): BinningType = {
    val mode = binning.getString("mode")
    val overlapping =
      if (!binning.hasPath("overlapping"))
        false
      else
        Try(binning.getBoolean("overlapping")) match {
          case Success(value) => value
          case Failure(e) => println(e); false
        }
    mode match {
      case "Entropy" => BinningType.ENTROPY(binning.getInt("bins"), overlapping)
      case "EqualWidth" => BinningType.EQUALWIDTH(binning.getInt("bins"), overlapping)
      case "EqualFrequency" => BinningType.EQUALFREQUENCY(binning.getInt("bins"), overlapping)
      case "Interval" =>
        val delimiters = binning.resolveWith(config).getDoubleList("intervals").asScala.toList.map(_.toDouble).sorted
        fail(delimiters.nonEmpty, s"Empty list of intervals for $s in $binning")
        BinningType.INTERVAL(delimiters, overlapping)
      case x => fail(s"Binning method $x not supported in $binning"); null
    }
  }

  // 'features' comprise the list of all features that are required
  private val features: Vector[Feature] =
    tryFail(config.getConfigList("features").asScala.toVector).map(
      feature => {
        val attribute = tryFail(feature.getString("attribute"))
        val typ = tryFail(SimpleType(feature.getString("typ")))
        val condition =
          if (!feature.hasPath("condition"))
            TRUE
          else
            Try(Expression.parseExpression(feature.getString("condition"), s"Context property condition of feature $attribute")) match {
              case Success(cond) => cond
              case Failure(e) => fail(e.getLocalizedMessage); TRUE
            }
        if (typ == SimpleType.NUMERIC) {
          val binning = tryWithDefault(Some(feature.getConfig("binning")), None)
          binning match {
            case None => Feature(attribute, typ)
            case Some(_binning) => Feature(attribute, genBinnigType(s"attribute $attribute", _binning), condition)
          }
        } else {
          Feature(attribute, typ, condition)
        }
      }
    )

  private val featureNames = features.map(_.name)
  fail(featureNames.lengthCompare(featureNames.distinct.length) == 0, "There are double occurrences of feature names")

  // target generation
  val targetName: String = tryFail(config getString "target.attribute")
  val targetGroups: List[String] = tryFail(config.getStringList("target.labels").asScala.toList)
  fail(targetGroups.nonEmpty, "There must be at least one target label.")
  implicit val numberOfTargetGroups: Int = targetGroups.length + 1    // target group 0 is the default group

  fail(features.map(_.name).contains(targetName), s"Target feature '$targetName' is not contained in the list of attributes.")

  // TimeFeature generation
  val timeframe: Option[TimeFrame] =
    Try(config getConfig "time") match {
      case Failure(_) => None
      case Success(conf) =>
        val timestampName = Try(conf getString "attribute") match {
          case Success(attribute) =>
            if (features.map(_.name).contains(attribute)) {
              attribute
            } else {
              fail(s"Time attribute '$attribute'.is not contained in the list of features.")
              ""
            }
          case Failure(exception) =>
            fail(s"Time attribute missing."); ""
        }
        val dateTimeFormatter =
          Try(conf getString "format") match {
            case Success(f) =>
              Try(DateTimeFormat.forPattern(f)) match {
                case Success(p) => p
                case Failure(exception) => fail(exception.getMessage); null
              }
            case Failure(e) => fail("Time format is missing"); null
          }
        val tf = TimeFrame(timestampName, dateTimeFormatter)

        val startTime: Option[DateTime] =
          Try(conf getString "start") match {
            case Success(start) => Some(tf.parseDateTime(start))
            case Failure(e) => None
          }
        val stopTime: Option[DateTime] =
          Try(conf getString "stop") match {
            case Success(start) => Some(tf.parseDateTime(start))
            case Failure(e) => None
          }
        Some(tf.copy(start = startTime, stop = stopTime))
    }

  private val simpleFeaturesNoPosition =
    features
      .filterNot(feature => feature.name == targetName || timeframe.isDefined && feature.name == timeframe.get.attribute)

  implicit val attributeToPosition: Map[String, Position] =
    simpleFeaturesNoPosition
      .map(_.name)
      .zipWithIndex
      .toMap

  val simpleFeatures: Vector[Feature] =
    simpleFeaturesNoPosition.map { case Feature(name, typ, condition, _) => Feature(name, typ, condition, attributeToPosition(name)) }
  //  private val attributeToFeature = simpleFeatures.map(feature => feature.name -> feature).toMap
  val noSimpleFeatures: Int = simpleFeatures.length

  val instanceFilter: Expression.BoolExpr =
    if (!config.hasPath("instanceFilter"))
      TRUE
    else
      Try(config getString "instanceFilter") match {
        case Success(cond) => Expression.parseExpression(cond, "Context property instanceFilter").updatePosition(this.attributeToPosition)
        case Failure(e) => fail(e.getLocalizedMessage); TRUE
      }

  /*
     Checks whether a group definition exists. If yes, it is checked whether the group members are in the list of features, and further,
     if the feature name of the group is not yet used as a feature name

     The intermediate step of introducing groupData is necessary since only after features belonging to exclusive groups are
     eliminated the list of base features and theirt position can be determined and only then the correct positions of group members
     can be defined.
   */
  val compoundFeatures: Vector[Feature] = {
    tryWithDefault(config.getConfigList("compounds").asScala.toList, Nil)
      .toVector
      .map(
        config => {
          val compound: List[String] = Try(config.getStringList("compound").asScala.toList) match {
            case Success(_compound) =>
              fail(_compound.nonEmpty, "There is a compound feature with an empty list of compound features.")
              _compound
            case Failure(e) =>
              fail(e.getLocalizedMessage); null
          }
          compound.foreach(
            compoundElement => {
              fail(features.map(_.name).contains(compoundElement),
                s"Compound element $compoundElement is not contained in the list of features.")
              fail(compoundElement == targetName, s"Target attribute $targetName is an element of a compound.")
              timeframe.foreach(tf => fail(compoundElement != tf.attribute,
                s"Timestamp feature ${tf.attribute} is an element of a compound."))
            }
          )
          Feature(s"Compound(${compound.reduce(_ + "." + _)})", DerivedType.COMPOUND(compound.map(attributeToPosition)))
        }
      )
      .zipWithIndex
      .map { case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noSimpleFeatures) }
  }

  private val noFeatures1 = noSimpleFeatures + compoundFeatures.length

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
          prefixes.map(number => Feature(s"${name}_prefix_$number", DerivedType.PREFIX(number, attributeToPosition(name))))
        }
      )
      .zipWithIndex
      .map { case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noFeatures1) }

  private val noFeatures2 = noFeatures1 + prefixFeatures.length

  val rangeFeatures: Vector[Feature] =
    tryWithDefault(config.getConfigList("rangedFeatures").asScala.toList, Nil)
      .toVector
      .flatMap(
        config => {
          val name = tryFail(config.getString("attribute"))
          val baseFeature =
            features.find(_.name == name) match {
              case None => fail(s"Range features: The name $name does not occur in the list of features."); null
              case Some(f) => f
            }
          fail(baseFeature.typ == SimpleType.NUMERIC, s"Typ of the ranged feature with name $name should be numeric.")
          val rangeConfigs: List[Config] =
            Try(config.getConfigList("ranges").asScala.toList) match {
              case Failure(e) => fail(e.getLocalizedMessage); null
              case Success(rangesList) => rangesList
            }
          rangeConfigs
            .map(
              range => {
                val lo = tryFail(range.getDouble("lo"))
                val hi = tryFail(range.getDouble("hi"))
                Feature(s"${name}_range($lo,$hi)", DerivedType.RANGE(lo, hi, attributeToPosition(name)))
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
          val seqId = tryWithDefault(Some(config.getString("groupBy")), None)
          if (seqId.nonEmpty)
            fail(features.map(_.name).contains(seqId.get),
              s"The seqId attribute '$seqId' of an aggregation field is not contained in the list of attributes.")
          val operator = tryFail(AggregationOp(config.getString("operator").toLowerCase))
          val attributes =
            if (operator == AggregationOp.count || operator == AggregationOp.exists) {
              Try(config.getStringList("attributes").asScala.toList) match {
                case Success(attrs) => attrs
                case Failure(e) => fail(s"For operator 'count' or 'exists' the key 'attributes' is required."); null
              }
            } else {
              Try(config.getString("attribute")) match {
                case Success(attrs) => List(attrs)
                case Failure(e) => fail(s"For operator 'count' or 'exists' the key 'attributes' is required."); null
              }
            }
          attributes.foreach(
            attribute =>
              fail(features.map(_.name).contains(attribute),
                s"The attribute '$attribute' of an aggregation field is not contained in the list of attributes.")
          )
          val seqIdPos = seqId.map(attributeToPosition(_))
          val positions = attributes.map(attributeToPosition)
          val conditionExpr =
            if (!config.hasPath("condition"))
              TRUE          // Default if condition does not exist
            else
              Try(config.getString("condition")) match {
                case Success(cond) =>
                  val condition = Expression.parseExpression(cond, s"Context feature $attributes").updatePosition(this.attributeToPosition)
                  if (condition.attributes.forall(simpleFeatures.map(_.name).contains(_))) {
                    condition
                  } else if (attributes.isEmpty && condition.attributes.nonEmpty) {
                    fail(s"The list of attributes of an aggregator is empty. But there is a condition with attributes" +
                      s" '{${condition.attributes.reduce(_ + "," + _)}}'.")
                    null
                  } else if (attributes.isEmpty && condition.attributes.nonEmpty) {
                    condition
                  } else {
                    fail(s"Only attributes '{${attributes.reduce(_ + "," + _)}}' are allowed in a condition " +
                      s"for an aggregator. Found attributes: '{${condition.attributes.reduce(_ + "," + _)}}'")
                    null
                  }
                case Failure(e) => fail(e.getLocalizedMessage); null
              }
          val condition = (env: Vector[Value]) => conditionExpr.updatePosition(this.attributeToPosition).eval(env)
          val periodsAsString = tryFail(config.getStringList("periods").asScala.toList)
          periodsAsString
            .map(p => (p, Period(p)))
            .map {
              case (timeFrame, tf) =>
                if (operator == AggregationOp.exists || operator == AggregationOp.count) {
                  val name = s"Aggregate($timeFrame)"
                  val minimum =
                    if (config.hasPath("minimum"))
                      Try(config.getInt("minimum")) match {
                        case Success(min) => min
                        case Failure(e) => fail(s"Minimum should be an integer value: ${e.getMessage}"); 0
                      }
                    else
                      0
                  Feature(name, DerivedType.COUNT(seqIdPos, positions, minimum, condition, operator == AggregationOp.exists, tf))
                } else {
                  val name = s"Aggregate($timeFrame)"
                  val binning = tryFail(genBinnigType("an aggregator", config.getConfig("binning")))
                  val minimum =
                    if (config.hasPath("minimum"))
                      Try(config.getDouble("minimum")) match {
                        case Success(min) => min
                        case Failure(e) => fail(s"Minimum should be a double value: ${e.getMessage}"); 0.0
                      }
                    else
                      Double.NegativeInfinity
                  Feature(name, DerivedType.AGGREGATE(seqIdPos, positions.head, operator, minimum, condition, binning, tf))
                }
            }
        }
      )
      .zipWithIndex
      .map {
        case (Feature(name, typ, condition, _), position) => Feature(name, typ, condition, position + noFeatures3)
      }

  val requiresAggregation: Boolean = aggregateFeatures.nonEmpty

  // Important: keep the order since this defines the access to value vectors
  val allFeatures: Vector[Feature] = simpleFeatures ++ compoundFeatures ++ prefixFeatures ++ rangeFeatures ++ aggregateFeatures
  val noAllFeatures: Int = allFeatures.length

  private def typ2binning(typ: BinningType): Discretization =
    typ match {
      case BinningType.NOBINNING => NoBinning
      case BinningType.ENTROPY(bins, overlapping) => Entropy(bins, overlapping)
      case BinningType.INTERVAL(delimiters, overlapping) => Intervals(delimiters, overlapping)
      case BinningType.EQUALWIDTH(bins, overlapping) => EqualWidth(bins, overlapping)
      case BinningType.EQUALFREQUENCY(bins, overlapping) => EqualFrequency(bins, overlapping)
    }

  val binning: Map[Position, Discretization] =
    simpleFeatures.map(
      feature =>
        feature.typ match {
          case aggr: DerivedType.AGGREGATE => feature.position -> typ2binning(aggr.binning)
          case typ: BinningType => feature.position -> typ2binning(typ)
          case _ => feature.position -> NoBinning
        }
    ).toMap

  val hasFeaturesToBin: Boolean = binning.nonEmpty
}
