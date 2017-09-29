package apps

import akka.actor.ActorSystem
import better.files._
import common.utils.{fail, time}
import nqfpruleminer._
import nqfpruleminer.actors.Master
import org.backuity.clist._


object ruleminer extends CliMain[Unit](
  name = "ruleminer",
  description =
    """Generates the k most interesting subgroups using the "Not Quite FPGrowth algorithm.
      | Supported inputs are .csv files and.arff file.
      | Assumptions:
      |-.csv files: first line defines the attributes
      |-.arff files: data are in csv format """.stripMargin) {

  var configFile: String = arg[String](description = "Input file")
  var numberOfBestSubgroups: Int = opt[Int](description = "Number of the best subgroups considered (default: 10).", default = 100)
  var lengthOfSubgroups: Int = opt[Int](description = "Number of the best subgroups considered (default: 3).", default = 3)

  private val system = ActorSystem("nqfpminer")

  def run: Unit = {
    fail(configFile.toFile.exists(), s"Configuration file ${configFile.toFile.path} does not exist")
    fail(numberOfBestSubgroups > 0, "Number of of best subgroups must be greater > 0.")
    fail(lengthOfSubgroups > 0, "Length of subgroups must be greater 0.")

    println(s"Maximal number of best subgroups considered:  $numberOfBestSubgroups")
    println(s"Maximal length of best subgroups considered: $lengthOfSubgroups")
    println(s"Minimal quality:  $minimalQuality")
    println(s"Minimal generality: $minG")
    println(s"Minimal probability: $minP")

    fail(numberOfTargetGroups == 2 && (qualityMode == "Piatetsky" || qualityMode == "Binomial"),
      "Mode is Piatetsky-Shapiro or Binomial. No unique target value is specified.")

    time("sec needed for code generation")
    system.actorOf(Master.props(lengthOfSubgroups), name = "master")
  }
}




