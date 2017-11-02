package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.fhg.iais.nqfpruleminer._

object TreeGenerator {
  case class DataFrame(label: Int, instance: List[Int])
  case object Terminated

  def props(encode: Item => Int, trees: List[ActorRef])(implicit ctx: Context): Props =
    Props(classOf[TreeGenerator], encode,  trees, ctx)
}

class TreeGenerator(encode: Item => Int, trees: List[ActorRef])(implicit ctx: Context) extends Actor with ActorLogging {

  implicit val numberOfTargetGroups: Int = ctx.numberOfTargetGroups

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
      val sortedInstance = instance.map(encode).sortWith(_ < _)
      for (tree <- trees) tree ! TreeGenerator.DataFrame(label, sortedInstance.toList)
    }
  }
}