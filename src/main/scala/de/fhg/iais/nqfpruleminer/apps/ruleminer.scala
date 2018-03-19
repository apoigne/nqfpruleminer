package de.fhg.iais.nqfpruleminer.apps

import akka.actor.ActorSystem
import de.fhg.iais.nqfpruleminer.Context
import de.fhg.iais.nqfpruleminer.actors.Master
import de.fhg.iais.utils.{fail, progress}
import org.backuity.clist._

object ruleminer extends CliMain[Unit](
  name = "ruleminer",
  description =
    """Generates the most interesting subgroups using the "Not Quite FPGrowth" algorithm.
      |    Supported inputs are .csv files and .arff file.
      |    Assumptions:
      |       -.csv files: first line defines the feature names
      |       -.arff files: data are in csv format """.stripMargin

) {

  var configFile: String = arg[String](description = "Input file")

  private val system = ActorSystem("nqfpminer")

  def run: Unit = {
    implicit val ctx: Context = new Context(configFile)

    println(s"Maximal number of best subgroups considered:  ${ctx.numberOfBestSubgroups}")
    println(s"Maximal length of best subgroups considered: ${ctx.lengthOfSubgroups}")
    println(s"Minimal quality:  ${ctx.minimalQuality}")
    println(s"Minimal generality: ${ctx.minG}")
    println(s"Minimal probability: ${ctx.minP}")

    fail(ctx.numberOfTargetGroups == 2 && (ctx.qualityMode == "Piatetsky" || ctx.qualityMode == "Binomial"),
      "Mode is Piatetsky-Shapiro or Binomial. No unique target value is specified.")

    progress("sec needed for code generation")

    system.actorOf(Master.props(), name = "master") ! Master.Start

  }
}