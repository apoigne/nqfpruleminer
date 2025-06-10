package nqfpruleminer.actors

import org.apache.pekko
import pekko.actor.{Actor, ActorLogging, Props}
import pekko.util.Timeout
import nqfpruleminer.{Coding, Context, Distribution}
import nqfpruleminer.io.{WriteToJson, WriteToText}
import nqfpruleminer.utils.fail

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object BestSubGroups {
  type Quality = Double
  type Generality = Double

  case class SubGroup(group: List[Int], distr: IndexedSeq[Int], quality: Quality, generality: Generality)
  case class MinQ(value: Double)
  case class GenOutput(rootDistribution: Distribution, subgroupCounter: Int)

  def props(numberOfItems: Int, coding: Coding)(implicit ctx: Context): Props =
    Props(classOf[BestSubGroups], numberOfItems, coding, ctx)
}

class BestSubGroups(numberOfItems: Int, coding: Coding)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info("Started")

  import BestSubGroups._

  private var _minQ = ctx.minimalQuality
  private val refineSubgroups = ctx.refineSubgroups

  private var kBestSubGroups = List[SubGroup]()

  implicit val ec: ExecutionContextExecutor = context.dispatcher
  implicit val timeout: Timeout = 1.second

  def receive: Receive = {
    case subGroup: SubGroup =>
      if (kBestSubGroups.length < ctx.numberOfBestSubgroups) {
        if (subGroup.distr.sum > 0) {
          kBestSubGroups = insert(subGroup, kBestSubGroups)
          val newMinQ = kBestSubGroups.head.quality
          if (newMinQ > _minQ) update(newMinQ)
        } else {
          log.info(subGroup.toString)
        }
      } else {
        context become receiveFull
        self ! subGroup
      }

    case GenOutput(rootDistribution, subgroupCounter) =>
      genOutput(rootDistribution, subgroupCounter)
  }

  private def receiveFull: Receive = {
    case subGroup: SubGroup =>
      if (subGroup.quality > _minQ && subGroup.distr.sum > 0) {
        kBestSubGroups = insert(subGroup, kBestSubGroups.tail)
        val newMinQ = kBestSubGroups.head.quality
        if (newMinQ > _minQ) update(newMinQ)
      }
    case GenOutput(rootDistribution, subgroupCounter) =>
      genOutput(rootDistribution, subgroupCounter)
  }

  private def update(newMinQ: Double): Unit = {
    context.actorSelection(s"pekoo://nqfpminer/user/master") ! MinQ(newMinQ)
    _minQ = newMinQ
  }

  private def isExtensionOf(groups1: List[Int], groups2: List[Int]) = {
    @scala.annotation.tailrec
    def isExtensionOf(groups1: List[Int], groups2: List[Int]): Boolean =
      (groups1, groups2) match {
        case (Nil, Nil) => true
        case (Nil, _) => false
        case (_, Nil) => true
        case (g1 :: _groups1, g2 :: _groups2) if g1 == g2 => isExtensionOf(_groups1, _groups2)
        case (g1 :: _groups1, g2 :: _) if g1 < g2 => isExtensionOf(_groups1, groups2)
        case (_ :: _, _ :: _groups2) => isExtensionOf(groups1, _groups2)
        case _ => groups1 == groups2
      }

    isExtensionOf(groups1, groups1)
  }

  def insert(sg: SubGroup, groups: List[SubGroup]): List[SubGroup] = {
    groups match {
      case Nil => List(sg)
      case _sg :: best =>
        if (_sg.quality < sg.quality)
          _sg :: insert(sg, best)
        else if (_sg.quality < sg.quality && refineSubgroups)
          if (isExtensionOf(_sg.group, sg.group)) _sg :: best else sg :: _sg :: best
        else
          sg :: _sg :: best
    }
  }

  private def genOutput(rootDistribution: Distribution, subgroupCounter: Int): Unit = {

    ctx.outputFormat match {
      case "txt" => new WriteToText(numberOfItems, kBestSubGroups, coding.decode, rootDistribution, subgroupCounter).write()
      case "json" => new WriteToJson(numberOfItems, kBestSubGroups, coding.decode, rootDistribution, subgroupCounter).write()
      case x => fail(s"Output format $x not supported.")
    }
    context.system.terminate() onComplete {
      case Success(_) =>
        System.exit(0)
      case Failure(e) =>
        log.info(e.getMessage)
    }
  }
}