package de.fhg.iais.nqfpruleminer.io

import better.files._
import de.fhg.iais.nqfpruleminer.actors.BestSubGroups.SubGroup
import de.fhg.iais.nqfpruleminer.{Coding, Context, Distribution}
import de.fhg.iais.utils.binomialSum
import spray.json.DefaultJsonProtocol._
import spray.json._

class WriteToJson(numberOfItems: Int,
                  kBestSubGroups: List[SubGroup],
                  decode: Coding.DecodingTable,
                  rootDistribution: Distribution,
                  subgroupCounter: Long
                 )(implicit ctx: Context) {

  def write(): Unit = {
    val outputFile = ctx.outputFile + ".json"
    outputFile.toFile.overwrite("")
    if (kBestSubGroups.isEmpty) {
      outputFile.toFile.append("Error: no best subgroups generated.")
    } else {
      val numberOfNodes = binomialSum(numberOfItems.toLong, ctx.lengthOfSubgroups)

      val output =
        Map(
          "target" -> Map("feature" -> ctx.targetName.toJson, "values" -> ctx.targetGroups.toJson).toJson,
          "quality_function" -> ctx.qualityMode.toJson,
          "number_of_items" -> numberOfItems.toJson,
          "target_value_distribution" ->
            (ctx.qualityMode match {
              case "Piatetsky" =>
                Map(
                  ctx.targetGroups.head -> rootDistribution(1).toJson,
                  "others" -> rootDistribution(0).toJson
                ).toJson
              case "Binomial" =>
                Map(
                  ctx.targetGroups.head -> rootDistribution(1).toJson,
                  "others" -> rootDistribution(0).toJson
                ).toJson
              case _ =>
                (ctx.targetGroups.zipWithIndex.map { case (v, i) => v -> rootDistribution(i + 1).toJson }.toMap
                  + ("others" -> rootDistribution(0).toJson)
                  ).toJson
            }).toJson,
          "best_subgroups" ->
            kBestSubGroups.reverse.map(
              sg =>
                Map(
                  "conditions" -> sg.group.sorted.map(decode).map(_.toString).toJson,
                  "quality" -> sg.quality.toJson,
                  "size" -> sg.distr.sum.toJson,
                  "generality" -> sg.generality.toJson,
                  "probabilities" ->
                    (ctx.qualityMode match {
                      case "Piatetsky" =>
                        Map("p" -> (sg.distr(0).toDouble / sg.distr.sum.toDouble).toJson).toMap.toJson
                      case "Binomial" =>
                        Map("p" -> (sg.distr(0).toDouble / sg.distr.sum.toDouble).toJson).toMap.toJson
                      case _ =>
                        val n = sg.distr.sum.toDouble
                        (0 until ctx.numberOfTargetGroups).map(i => s"p($i)" -> (sg.distr(i).toDouble / n).toJson).toMap.toJson
                    })
                ).toJson
            ).toJson,
          "info" ->
            (s"\nConsidered $subgroupCounter subgroups of depth <= ${ctx.lengthOfSubgroups} out of $numberOfNodes with maxDepth ${ctx.lengthOfSubgroups}," +
              s" i.e. ${subgroupCounter.toDouble / numberOfNodes.toDouble * 100.0} %").toJson
        ).toJson.prettyPrint

      outputFile.toFile.append(output)
    }
  }
}