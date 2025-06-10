package nqfpruleminer.actors

import nqfpruleminer.actors.BestSubGroups.MinQ
import nqfpruleminer.io.{Provider, Reader}
import nqfpruleminer.utils.progress
import nqfpruleminer.{Coding, Context}
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props}
import org.apache.pekko.routing.{Broadcast, RoundRobinPool}

import scala.Ordering.Double.IeeeOrdering

object Master {
  def props()(implicit ctx: Context): Props = Props(classOf[Master], ctx)

  case object Start
  case class GenerateTrees(nqfptrees: List[(Range, ActorRef)], coding: Coding)
}

class Master(implicit ctx: Context) extends Actor with ActorLogging {

  import nqfpruleminer.*

  log.info(s"Started.")

  private val itemFrequencies = scala.collection.mutable.Map[Item, Distribution]()
  private val rootDistr: Distribution = Distribution()(ctx.numberOfTargetGroups)

  private val workers = context.actorOf(RoundRobinPool(ctx.numberOfWorkers).props(Worker.props(self)), name = "worker")
  private val listener = if (ctx.requiresAggregation) context.actorOf(Aggregator.props(workers), name = "aggregator") else workers
  val reader = new Reader(Provider(ctx.providerData), listener)

  def receive: Receive = {
    case Master.Start =>
      reader.run()
      context become waitForItemsToBeGenerated(ctx.numberOfWorkers)
  }

  def waitForItemsToBeGenerated(n: Int): Receive = {
    case Worker.Count(table, distr) =>
      table.foreach {
        case (key, distribution) =>
          itemFrequencies get key match {
            case None => itemFrequencies += (key -> distribution)
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
            case "Lift" => Lift(ctx.minG, ctx.minP, n0, p0).eval
            case "Piatetsky" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
            case "Piatetsky-Shapiro" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
            case "Binomial" => Binomial(ctx.minG, ctx.minP, n0, p0).eval
            case "Split" => Split(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
            case "Gini" => Gini(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
            case "Pearson" => Pearson(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
          }

        // We take the  maxNumberOfItems of items with the best quality
        val filteredItems =
          itemFrequencies
            .filter { case (_, _distr) => _distr.probability.forall(_ >= ctx.minP) }
            .view
            .mapValues(quality)
            .toList
            .filter({ case (_, Quality(_, g, _)) => g >= ctx.minG })
            .sortBy { case (_, Quality(q, _, _)) => q }
            .take(ctx.maxNumberOfItems)
            .map(_._1)

        val filteredItemFrequencies =
          filteredItems.map(value => value -> itemFrequencies(value)).toMap

        // Only these are encoded
        val coding: Coding = new Coding(filteredItemFrequencies)

        log.info(progress("sec needed for binning items"))
        log.info(s"Number of items: ${coding.numberOfItems}")

        val bestSubgroups = context.actorOf(BestSubGroups.props(coding.numberOfItems, coding), name = "bestsubgroups")

        val nqFpTrees =
          ctx.delimiterRangesForParallelExecution(coding.numberOfItems)
            .map(
              range =>
                range ->
                  context.actorOf(
                    NqFpTree.props(range, coding.numberOfItems, ctx.lengthOfSubgroups, self, bestSubgroups),
                    name = s"nqfptree-${range.start}-${range.end}"
                  )
            )

        implicit val trees: List[ActorRef] = nqFpTrees.map(_._2)

        workers ! Broadcast(Master.GenerateTrees(nqFpTrees, coding))
        context become waitForTreeGenerationTermination(ctx.numberOfWorkers)
      }
  }

  def waitForTreeGenerationTermination(n: Int)(implicit trees: List[ActorRef], quality: Distribution => Quality): Receive = {
    case Worker.Terminated =>
      if (n <= 1) {
        log.info(progress("sec needed for tree generation"))
        for (tree <- trees) tree ! NqFpTree.NoMoreInstances(quality)
        context become receiveDistributions(trees,0)
      } else {
        context become waitForTreeGenerationTermination(n - 1)
      }

  }

//  private var subgroupCounter = 0

  def receiveDistributions(trees: List[ActorRef], _subgroupCounter : Int): Receive = {
    case MinQ(minQ) =>
      for (tree <- trees) tree ! MinQ(minQ)
    case NqFpTree.Terminated(tree, numberOfSubgroups) =>
      val subgroupCounter = _subgroupCounter + numberOfSubgroups
      if (trees.lengthCompare(1) <= 0) {
        log.info(progress("sec needed for subgroup generation."))
        context.actorSelection("pekoo://nqfpminer/user/master/bestsubgroups") ! BestSubGroups.GenOutput(rootDistr, subgroupCounter)
      } else {
        context become receiveDistributions(trees.filterNot(_ == tree), subgroupCounter)
      }
  }
}