package nqfpruleminer.io

import better.files._
import nqfpruleminer.actors.BestSubGroups.SubGroup
import nqfpruleminer.utils.binomialSum
import nqfpruleminer.{Context, Distribution}
import spray.json.DefaultJsonProtocol._
import spray.json._

class WriteToJson(numberOfItems: Int,
                  kBestSubGroups: List[SubGroup],
                  decode: Int => String,
                  rootDistribution: Distribution,
                  subgroupCounter: Long
                 )(implicit ctx: Context) {

  def write(): Unit = {
    val outputFile = ctx.outputFile.pathAsString + ".json"
    outputFile.toFile.overwrite("")
    if (kBestSubGroups.isEmpty) {
      outputFile.toFile.append("No best subgroups generated. maybe the configuration file is incorrect.")
    } else {
      val numberOfNodes = binomialSum(numberOfItems.toLong, ctx.lengthOfSubgroups)

      val output =
        Map(
          "target" -> Map("feature" -> ctx.targetName.toJson, "values" -> ctx.targetGroups.toJson).toJson,
          "quality_function" -> ctx.qualityMode.toJson,
          "number_of_items" -> numberOfItems.toJson,
          "target_value_distribution" ->
            (ctx.qualityMode match {
              case "Lift" =>
                Map(
                  ctx.targetGroups.head -> rootDistribution(1).toJson,
                  "others" -> rootDistribution(0).toJson
                ).toJson
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
                  "conditions" -> sg.group.sorted.map(decode).toJson,
                  "quality" -> sg.quality.toJson,
                  "size" -> sg.distr.sum.toJson,
                  "generality" -> sg.generality.toJson,
                  "probabilities" ->
                    (ctx.qualityMode match {
                      case "Lift" =>
                        Map("p" -> (sg.distr(0).toDouble / sg.distr.sum.toDouble).toJson).toJson
                      case "Piatetsky" =>
                        Map("p" -> (sg.distr(0).toDouble / sg.distr.sum.toDouble).toJson).toJson
                      case "Binomial" =>
                        Map("p" -> (sg.distr(0).toDouble / sg.distr.sum.toDouble).toJson).toJson
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
  }                                                                                                           }