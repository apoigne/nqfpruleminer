import apps.ruleminer.configFile
import better.files._
import com.typesafe.config.ConfigFactory
import common.utils.fail

import scala.collection.JavaConverters._

package object nqfpruleminer {
  private val config = ConfigFactory.parseFile(configFile.toFile.toJava)
  val separator: String = try {config getString "separator"} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
  val dataFiles: List[String] = try {List(config getString "datafile")} catch {case e: Throwable => fail(e.getLocalizedMessage); List("")}
  val minimalQuality: Double = try {config getDouble "minimalQuality"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val minP: Double = try {config getDouble "minProbability"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val minG: Double = try {config getDouble "minGenerality"} catch {case e: Throwable => fail(e.getLocalizedMessage); 0.0}
  val computeClosureOfSubgroups: Boolean = try {config getBoolean "computeClosureOfSubgroups"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val refineSubgroups: Boolean = try {config getBoolean "refineSubgroups"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
  val qualityMode: String = try {config getString "qualityfunction"} catch {case e: Throwable => fail(e.getLocalizedMessage); "Piatetsky"}
  val outputFile: String = try {config getString "outputFile"} catch {case e: Throwable => fail(e.getLocalizedMessage); "result.txt"}

  fail(qualityMode == "Piatetsky" || qualityMode == "Binomial" || qualityMode == "Split" ||
    qualityMode == "Pearson" || qualityMode == "Gini", s"Quality mode $qualityMode is not supported.")

  val attributes: List[String] =
    try {
      config.getStringList("attributes").asScala.toList
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); Nil
    }

  // target generation
  val targetName: String =
    try {
      config getString "target.name"
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); ""
    }
  val targetIndex: Int = attributes.indexWhere(_ == targetName)
  val targetGroups: List[String] =
    try {
      config.getStringList("target.groups").asScala.toList
    } catch {
      case e: Throwable => fail(e.getLocalizedMessage); List[String]()
    }

  implicit val numberOfTargetGroups: Int = targetGroups.length + 1

  private val featuresToBeUsed =
    try {
      (config getStringList "featuresToConsider").asScala.toList
    } catch {
      case _: Throwable => attributes
    }

  fail(featuresToBeUsed.contains(targetName), s"Target $targetName is contained in the list of features to be used.")
  val features: List[String] = featuresToBeUsed.filter(_ != targetName)

  val ranges = List(43)
}
