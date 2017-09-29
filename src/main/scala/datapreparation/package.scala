//import apps.ruleminer.configFile
//import com.typesafe.config.{Config, ConfigFactory}
//import common.utils.fail
//import better.files._
//
//import scala.collection.JavaConverters._
//
//package object datapreparation {
//  private val config : Config = ConfigFactory.parseFile(configFile.toFile.toJava)
//
//  val separator: String = try {config getString "separator"} catch {case e: Throwable => fail(e.getLocalizedMessage); ""}
//  val dataFiles: List[String] = try {List(config getString "datafile")} catch {case e: Throwable => fail(e.getLocalizedMessage); List("")}
//
//  val discretisationConfig : Config = try {config getConfig "discretisation"} catch {case e: Throwable => fail(e.getLocalizedMessage); config}
//
//  val usesOverlappingIntervals  : Boolean = try {config getBoolean "use_overlaping_intervals"} catch {case e: Throwable => fail(e.getLocalizedMessage); false}
//
//
//}
