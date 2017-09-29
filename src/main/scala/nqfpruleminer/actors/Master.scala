package nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import apps.ruleminer.numberOfBestSubgroups
import common.utils.time
import nqfpruleminer.actors.BestSubGroups.MinQ
import nqfpruleminer.actors.NqFpTree.NoMoreInstances

object Master {
  def props(lengthOfSubgroups: Int): Props = Props(classOf[Master], lengthOfSubgroups)
}

class Master(lengthOfSubgroups: Int) extends Actor with ActorLogging {

  import nqfpruleminer._

  private val countTable = scala.collection.mutable.Map[Item, Int]()
  lazy val coding = new Coding(countTable.toMap)

  private var fileCounter = dataFiles.length

  log.info(s"number of files $fileCounter")

  val rootDistr = Distribution()

  private var nqFpTrees =  List[ActorRef]()
  private var treeCounter = 0

  private var subgroupCounter = 0

  for (dataFile <- dataFiles) context.actorOf(ItemCounter.props()) ! dataFile

  def receive: Receive = {
    case ItemCounter.Count(table) =>
      table.foreach {
        case (k, v) =>
          countTable get k match {
            case None => countTable += (k -> v)
            case Some(_v) => countTable.update(k, v + _v)
          }
      }
      fileCounter -= 1
      log.info(s"file number $fileCounter")
      if (fileCounter <= 0) {
        log.info(s"Number of items: ${coding.numberOfItems}")
        log.info(time("sec needed for counting items"))
        context become genTrees
      }
  }

  private val ranges = nqfpruleminer.ranges

  def genTrees: Receive = {
    fileCounter = dataFiles.length
    nqFpTrees =
      (ranges :+  coding.numberOfItems).foldLeft((0, List[ActorRef]()))(
        (acc,n) =>
          (n,acc._2 :+ context.actorOf(NqFpTree.props(acc._1, n, coding, lengthOfSubgroups), name=s"${acc._1}-$n"))
      )._2
    treeCounter = nqFpTrees.length
    log.info(s"file number $fileCounter")
    for (dataFile <- dataFiles) context.actorOf(TreeGenerator.props(coding, lengthOfSubgroups,nqFpTrees)) ! dataFile
    receiveDistributions
  }

  def receiveDistributions: Receive = {
    case distr: Distribution =>
      fileCounter -= 1
      rootDistr.add(distr)
      if (fileCounter == 0) {
        log.info(time("sec needed for tree generation"))
        log.info("Start mining")
        val n0 = rootDistr.sum.toDouble
        assert(n0 > 0.0, "n0: division by 0")
        val p0 = Array.tabulate(numberOfTargetGroups)(rootDistr(_).toDouble / n0)

        val quality: Distribution => Strategy =
          qualityMode match {
            case "Piatetsky" => Piatetsky(minG, minP, n0, p0).eval
            case "Piatetsky-Shapiro" => Piatetsky(minG, minP, n0, p0).eval
            case "Binomial" => Binomial(minG, minP, n0, p0).eval
            case "Split" => Split(minG, numberOfTargetGroups, n0, p0).eval
            case "Gini" => Gini(minG, numberOfTargetGroups, n0, p0).eval
            case "Pearson" => Pearson(minG, numberOfTargetGroups, n0, p0).eval
          }
        context.actorOf(BestSubGroups.props(numberOfBestSubgroups, lengthOfSubgroups, coding), name = "bestsubgroups")
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
