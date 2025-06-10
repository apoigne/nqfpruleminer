package nqfpruleminer.apps

import nqfpruleminer.Context
import nqfpruleminer.actors.Master
import nqfpruleminer.utils.{fail, progress}
import org.apache.pekko.actor.ActorSystem

object ruleminer {
  def main(args:Array[String]): Unit = {
    val configFile: String = args(0)

    val system = ActorSystem("nqfruleminer")

    implicit val ctx: Context = Context(configFile)

    println(s"Maximal number of best subgroups considered:  ${ctx.numberOfBestSubgroups}")
    println(s"Maximal length of best subgroups considered: ${ctx.lengthOfSubgroups}")
    println(s"Minimal quality:  ${ctx.minimalQuality}")
    println(s"Minimal generality: ${ctx.minG}")
    println(s"Minimal probability: ${ctx.minP}")

    fail(ctx.numberOfTargetGroups == 2 || !(ctx.qualityMode == "Piatetsky" || ctx.qualityMode == "Binomial" || ctx.qualityMode == "Lift"),
      "Mode is Piatetsky-Shapiro, Binomial, or lift. No unique target value is specified.")

    progress("sec needed for code generation")

    system.actorOf(Master.props(), name = "master") ! Master.Start
  }
}