package de.fhg.iais.nqfpruleminer.apps

import akka.actor.ActorSystem
import de.fhg.iais.nqfpruleminer.Context
import de.fhg.iais.nqfpruleminer.actors.Master
import de.fhg.iais.utils.{fail, progress}
import org.backuity.clist._

object ruleminer extends CliMain[Unit](
  name = "ruleminer",
  description =
    """Generates the k most interesting subgroups using the "Not Quite FPGrowth algorithm.
      |    Supported inputs are .csv files and .arff file.
      |    Assumptions:
      |       -.csv files: first line defines the feature names
      |       -.arff files: data are in csv format """.stripMargin

) {

  var configFile: String = arg[String](description = "Input file")
  var numberOfBestSubgroups: Int = opt[Int](description = "Number of the best subgroups considered (default: 10).", default = 100)
  var lengthOfSubgroups: Int = opt[Int](description = "Number of the best subgroups considered (default: 3).", default = 3)

  private val system = ActorSystem("nqfpminer")

  def run: Unit = {
    fail(numberOfBestSubgroups > 0, "Number of of best subgroups must be greater > 0.")
    fail(lengthOfSubgroups > 0, "Length of subgroups must be greater 0.")

    implicit val ctx: Context = new Context(configFile, numberOfBestSubgroups, lengthOfSubgroups)

    println(s"Maximal number of best subgroups considered:  $numberOfBestSubgroups")
    println(s"Maximal length of best subgroups considered: $lengthOfSubgroups")
    println(s"Minimal quality:  ${ctx.minimalQuality}")
    println(s"Minimal generality: ${ctx.minG}")
    println(s"Minimal probability: ${ctx.minP}")

    fail(ctx.numberOfTargetGroups == 2 && (ctx.qualityMode == "Piatetsky" || ctx.qualityMode == "Binomial"),
      "Mode is Piatetsky-Shapiro or Binomial. No unique target value is specified.")

    progress("sec needed for code generation")

    system.actorOf(Master.props(), name = "master") ! Master.Start

  }
}