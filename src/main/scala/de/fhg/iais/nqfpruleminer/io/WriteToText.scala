package de.fhg.iais.nqfpruleminer.io

import better.files._
import de.fhg.iais.nqfpruleminer.actors.BestSubGroups.SubGroup
import de.fhg.iais.nqfpruleminer.{Context, Distribution}
import de.fhg.iais.utils.binomialSum

class WriteToText(numberOfItems: Int,
                  kBestSubGroups: List[SubGroup],
                  decode: Int => String,
                  rootDistribution: Distribution,
                  subgroupCounter: Long
                 )(implicit ctx: Context) {

  def write(): Unit = {
    val outputFile = ctx.outputFile + ".txt"
    outputFile.toFile.overwrite("")
    outputFile.toFile.overwrite("")
    if (kBestSubGroups.isEmpty) {
      outputFile.toFile.append("Error: no best subgroups generated.")
    } else {
      val targetValues = ctx.targetGroups.map(_.toString).reduce(_ + "," + _)
      val numberOfNodes = binomialSum(numberOfItems.toLong, ctx.lengthOfSubgroups)

      val output =
//        s"Dataset: ${ctx.dataFiles}\n\n" +
        s"Target:  feature: ${ctx.targetName}, values: $targetValues\n" +
          s"Quality function: ${ctx.qualityMode}\n" +
          s"Number of items: $numberOfItems\n\n" +
          s"TargetValueDistribution: " +
          (ctx.qualityMode match {
            case "Piatetsky" =>
              s"$targetValues: ${rootDistribution(1)} "
            case "Binomial" =>
              s"$targetValues= ${rootDistribution(1)} "
            case _ =>
              targetValues.zipWithIndex.map { case (v, i) => s"   $v: ${rootDistribution(i + 1)}" }.reduce(_ + "," + _)
          }) +
          s" others: ${rootDistribution(0)}\n\n" +
          s"The ${ctx.numberOfBestSubgroups} best subgroups:\n" +
          kBestSubGroups.reverse.zipWithIndex.map {
            case (sg: SubGroup, index: Int) =>
              s"\n${index + 1}. " +
                sg.group.sorted.map(decode).map(_.toString).reduce(_ + " && " + _) +
                s"\nQuality = ${sg.quality}" +
                s"\nSize = ${sg.distr.sum}, Generality = ${sg.generality}, " + {
                ctx.qualityMode match {
                  case "Piatetsky" =>
                    s"p = ${sg.distr(0).toDouble / sg.distr.sum.toDouble}"
                  case "Binomial" =>
                    s"p = ${sg.distr(0).toDouble / sg.distr.sum.toDouble}"
                  case _ =>
                    (0 until ctx.numberOfTargetGroups).map(i => s"p($i) = ${sg.distr(i).toDouble / sg.distr.sum.toDouble}").reduce(_ + ", " + _)
                }
              } + "\n"
          }.reduce(_ + _) +
          s"\nConsidered $subgroupCounter subgroups of depth <= ${ctx.lengthOfSubgroups} out of $numberOfNodes with maxDepth ${ctx.lengthOfSubgroups}," +
          s" i.e. ${subgroupCounter.toDouble / numberOfNodes.toDouble * 100.0} % \n"
      outputFile.toFile.append(output)
    }
  }
}