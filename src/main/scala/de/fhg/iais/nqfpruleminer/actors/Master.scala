package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.{Broadcast, RoundRobinPool}
import better.files._
import de.fhg.iais.nqfpruleminer.actors.BestSubGroups.MinQ
import de.fhg.iais.nqfpruleminer.io.Reader
import de.fhg.iais.nqfpruleminer.{Coding, Context}
import de.fhg.iais.utils.progress

object Master {
  def props()(implicit ctx: Context): Props = Props(classOf[Master], ctx)

  case object Start
  case class GenerateTrees(nqfptrees: List[(Range, ActorRef)], codingTable: Coding.CodingTable)
}

class Master(implicit ctx: Context) extends Actor with ActorLogging {

  import de.fhg.iais.nqfpruleminer._

  log.info(s"Started.")

  private val itemFrequencies = scala.collection.mutable.Map[Item, Distribution]()
  private val rootDistr: Distribution = Distribution()(ctx.numberOfTargetGroups)

  private val workers = context.actorOf(RoundRobinPool(ctx.numberOfWorkers).props(Worker.props(self)), name = "worker")
  private val listener = if (ctx.requiresAggregation) context.actorOf(Aggregator.props(workers), name = "aggregator") else workers
  val reader = new Reader(io.Provider(ctx.providerData), listener)

  def receive: Receive = {
    case Master.Start =>
      reader.run()
      context become waitForItemsToBeGenerated(ctx.numberOfWorkers)
  }

  def waitForItemsToBeGenerated(n: Int): Receive = {
    case count@Worker.Count(table, distr) =>
      table.foreach {
        case (value, distribution) =>
          itemFrequencies get value match {
            case None => itemFrequencies += (value -> distribution)
            case Some(_distribution) => _distribution.add(distribution)
          }
      }

      rootDistr.add(distr)
      if (n > 1) {
        context become waitForItemsToBeGenerated(n - 1)
      } else {
        log.info(progress("sec needed for counting items"))
        val n0 = rootDistr.sum.toDouble
        assert(n0 > 0.0, "There is no item recorded, i.e. n0 == 0")
        val p0 = Array.tabulate(ctx.numberOfTargetGroups)(rootDistr(_).toDouble / n0)

        implicit val quality: Distribution => Quality =
          ctx.qualityMode match {
            case "Piatetsky" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
            case "Piatetsky-Shapiro" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
            case "Binomial" => Binomial(ctx.minG, ctx.minP, n0, p0).eval
            case "Split" => Split(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
            case "Gini" => Gini(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
            case "Pearson" => Pearson(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
          }

        // We take the  maxNumberOfItems of items with the best quality
        val filteredItems =
          itemFrequencies.filter { case (_, _distr) => _distr.probability.forall(_ >= ctx.minP) }
            .mapValues(quality)
            .toList
            .filter({ case (_, Quality(q, g, _)) => g >= ctx.minG })
            .sortBy { case (_, Quality(q, g, _)) => q }
            .take(ctx.maxNumberOfItems)
            .map(_._1)

        val filteredItemFrequencies =
          filteredItems.map(value => value -> itemFrequencies(value)).toMap

        val statistics = s"${ctx.configFileName}_frequency.txt".toFile
        if (statistics.exists) statistics.delete()
        filteredItemFrequencies.toList.sortWith((x0, x1) => x0._2.sum > x1._2.sum).foreach(x => statistics.appendLine(s"${x._1.toString}: ${x._2.sum}"))

        log.info(s"Statistics are in file '$statistics'")
        if (ctx.statisticsOnly) System.exit(0)

        // Only these are encoded
        val coding: Coding = new Coding(filteredItemFrequencies)

        log.info(progress("sec needed for binning items"))
        log.info(s"Number of items: ${coding.numberOfItems}")

        context.actorOf(BestSubGroups.props(coding.numberOfItems, coding.decodingTable), name = "bestsubgroups")

        val nqFpTrees =
          ctx.delimiterRangesForParallelExecution(coding.numberOfItems)
            .map(
              range =>
                range ->
                  context.actorOf(
                    NqFpTree.props(range, coding.numberOfItems, ctx.lengthOfSubgroups),
                    name = s"nqfptree-${range.start}-${range.end}")
            )

        implicit val trees: List[ActorRef] = nqFpTrees.map(_._2)

        workers ! Broadcast(Master.GenerateTrees(nqFpTrees, coding.codingTable))
        context become waitForTreeGenerationTermination(ctx.numberOfWorkers)
      }

  }

  def waitForTreeGenerationTermination(n: Int)(implicit trees: List[ActorRef], quality: Distribution => Quality): Receive = {
    case Worker.Terminated =>
      if (n <= 1) {
        log.info(progress("sec needed for tree generation"))
        for (tree <- trees) tree ! NqFpTree.NoMoreInstances(quality)
        context become receiveDistributions(trees)
      } else {
        context become waitForTreeGenerationTermination(n - 1)
      }

  }

  private var subgroupCounter = 0

  def receiveDistributions(trees: List[ActorRef]): Receive = {
    case MinQ(minQ) =>
      for (tree <- trees) tree ! MinQ(minQ)
    case NqFpTree.Terminated(tree, numberOfSubgroups) =>
      subgroupCounter += numberOfSubgroups
      if (trees.lengthCompare(1) <= 0) {
        log.info(progress("sec needed for subgroup generation."))
        context.actorSelection("akka://nqfpminer/user/master/bestsubgroups") ! BestSubGroups.GenOutput(rootDistr, subgroupCounter)
      } else {
        context become receiveDistributions(trees.filterNot(_ == tree))
      }
  }
}