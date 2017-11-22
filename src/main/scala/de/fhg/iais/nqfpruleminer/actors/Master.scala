package de.fhg.iais.nqfpruleminer.actors

import javax.swing.table.AbstractTableModel

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.fhg.iais.nqfpruleminer.Context
import de.fhg.iais.nqfpruleminer.actors.BestSubGroups.MinQ
import de.fhg.iais.nqfpruleminer.actors.NqFpTree.NoMoreInstances
import de.fhg.iais.table.ATable
import de.fhg.iais.utils.time

object Master {
  def props(lengthOfSubgroups: Int, numberOfBestSubgroups: Int)(implicit ctx: Context): Props = Props(classOf[Master], lengthOfSubgroups, numberOfBestSubgroups, ctx)
}

class Master(lengthOfSubgroups: Int, numberOfBestSubgroups: Int)(implicit ctx: Context) extends Actor with ActorLogging {

  import de.fhg.iais.nqfpruleminer._

  private val itemFrequencies = scala.collection.mutable.Map[Item, Int]()

  lazy val coding: Coding = new Coding(itemFrequencies.toMap)

  private val providers = ctx.providers

  private var fileCounter = providers.length

  log.info(s"number of files $fileCounter")

  val rootDistr: Distribution = Distribution()(ctx.numberOfTargetGroups)

  private var nqFpTrees = List[ActorRef]()
  private var treeCounter = 0

  private var subgroupCounter = 0

  private var instances = new ATable

  for (provider <- providers) context.actorOf(ItemCounter.props(provider)) ! ItemCounter.Start

  def receive: Receive = {
    case instance: Instance =>
      instances.add(instance)
    case ItemCounter.Count(table) =>
      table.foreach {
        case (k, v) =>
          itemFrequencies get k match {
            case None => itemFrequencies += (k -> v)
            case Some(_v) => itemFrequencies.update(k, v + _v)
          }
      }
      fileCounter -= 1
      log.info(s"file number $fileCounter")
      if (fileCounter <= 0) {
        log.info(time("sec needed for counting items"))
        log.info(s"Number of items: ${coding.numberOfItems}")
        context become genTrees
      }
  }

  def genTrees: Receive = {
    fileCounter = providers.length
    nqFpTrees =
      (ctx.delimitersForParallelExecution :+ coding.numberOfItems)
        .foldLeft((0, List[ActorRef]()))(
          (acc, n) =>
            (n, acc._2 :+ context.actorOf(NqFpTree.props(acc._1, n, coding.numberOfItems, lengthOfSubgroups), name = s"${acc._1}-$n"))
        )._2
    log.info(time("sec needed for binning items"))
    treeCounter = nqFpTrees.length
    log.info(s"file number $fileCounter")
    for (provider <- providers) context.actorOf(TreeGenerator.props(provider, coding.codingTable, nqFpTrees)) ! TreeGenerator.Start
    receiveDistributions

//    instances.foreach {
//      case Instance(label, instance) =>
//        if (instance.nonEmpty) {
//          rootDistr.add(label)
//          val sortedInstance = instance.map(coding.encode).sortWith(_ < _)
//          for (tree <- nqFpTrees) tree ! TreeGenerator.DataFrame(label, sortedInstance.toList)
//        }
//    }
//
//    log.info(time("sec needed for tree generation"))
//    log.info("Start mining")
//    val n0 = rootDistr.sum.toDouble
//    assert(n0 > 0.0, "n0: division by 0")
//    val p0 = Array.tabulate(ctx.numberOfTargetGroups)(rootDistr(_).toDouble / n0)
//
//    val quality: Distribution => Strategy =
//      ctx.qualityMode match {
//        case "Piatetsky" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
//        case "Piatetsky-Shapiro" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
//        case "Binomial" => Binomial(ctx.minG, ctx.minP, n0, p0).eval
//        case "Split" => Split(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
//        case "Gini" => Gini(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
//        case "Pearson" => Pearson(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
//      }
//    context.actorOf(BestSubGroups.props(coding.numberOfItems, coding.decodingTable.toIndexedSeq), name = "bestsubgroups")
//    for (tree <- nqFpTrees) tree ! NoMoreInstances(quality)
//    receiveDistributions
  }

  def receiveDistributions: Receive = {
    case distr: Distribution =>
      log.info(rootDistr.toString)
      log.info(distr.toString)
      fileCounter -= 1
      rootDistr.add(distr)
      if (fileCounter == 0) {
        log.info(time("sec needed for tree generation"))
        log.info("Start mining")
        val n0 = rootDistr.sum.toDouble
        assert(n0 > 0.0, "n0: division by 0")
        val p0 = Array.tabulate(ctx.numberOfTargetGroups)(rootDistr(_).toDouble / n0)

        val quality: Distribution => Strategy =
          ctx.qualityMode match {
            case "Piatetsky" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
            case "Piatetsky-Shapiro" => Piatetsky(ctx.minG, ctx.minP, n0, p0).eval
            case "Binomial" => Binomial(ctx.minG, ctx.minP, n0, p0).eval
            case "Split" => Split(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
            case "Gini" => Gini(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
            case "Pearson" => Pearson(ctx.minG, ctx.numberOfTargetGroups, n0, p0).eval
          }
        context.actorOf(BestSubGroups.props(coding.numberOfItems, coding.decodingTable.toIndexedSeq), name = "bestsubgroups")
        for (tree <- nqFpTrees) tree ! NoMoreInstances(quality)
      }
    case MinQ(minQ) =>
//      log.info(s"master $minQ")
      for (tree <- nqFpTrees) tree ! MinQ(minQ)
    case NqFpTree.Terminated(_subgroupCounter) =>
      treeCounter -= 1
      subgroupCounter += _subgroupCounter
      if (treeCounter == 0) {
        log.info(time("sec needed for subgroup generation."))
        context.actorSelection("akka://nqfpminer/user/master/bestsubgroups") ! BestSubGroups.GenOutput(rootDistr, subgroupCounter)
      }
  }

}
