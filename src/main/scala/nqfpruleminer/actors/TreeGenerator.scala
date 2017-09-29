package nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import common.utils.Reader
import nqfpruleminer.{Coding, Distribution, Item}

object TreeGenerator {
  case class DataFrame(label: Int, instance: Seq[Int]) {
    assert(instance.nonEmpty)
  }
  case object Terminated

  def props(coding: Coding, lengthOfSubgroups: Int, trees: List[ActorRef]): Props = Props(classOf[TreeGenerator], coding, lengthOfSubgroups, trees)
}

class TreeGenerator(coding: Coding, lengthOfSubgroups: Int, trees: List[ActorRef]) extends Actor with ActorLogging {

  implicit val numberOfTargetGroups: Int = nqfpruleminer.numberOfTargetGroups

  val rootDistr = Distribution()

  def receive: Receive = {
    case dataFile: String =>
      val listener = sender()
      val reader = new Reader(dataFile)
      reader(readInstance)
      listener ! rootDistr
  }

  def readInstance(label: Int, instance: Seq[Item]): Unit = {
    rootDistr.add(Distribution(label))
    if (instance.nonEmpty) {
      val sortedInstance = instance.map(coding.encode).sortWith(_ < _)
      for (tree <- trees) tree ! TreeGenerator.DataFrame(label, sortedInstance)
    }
  }
}
