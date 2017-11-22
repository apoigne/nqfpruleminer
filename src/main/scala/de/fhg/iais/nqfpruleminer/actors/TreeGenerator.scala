package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.nqfpruleminer.actors.TreeGenerator.Start
import de.fhg.iais.nqfpruleminer.io.{Provider, Reader}

object TreeGenerator {
  case class DataFrame(label: Int, instance: List[Int])
  case object Start
  case object Terminated

  def props(kind : Provider.Data, encode: Item => Int, trees: List[ActorRef])(implicit ctx: Context): Props =
    Props(classOf[TreeGenerator], kind, encode,  trees, ctx)
}

class TreeGenerator(data : Provider.Data, encode: Item => Int, trees: List[ActorRef])(implicit ctx: Context) extends Actor with ActorLogging {

  implicit val numberOfTargetGroups: Int = ctx.numberOfTargetGroups

  val rootDistr = Distribution()

  def receive: Receive = {
    case Start =>
      val listener = sender()
      val reader = new Reader(Provider(data))
      reader(readInstance)
      listener ! rootDistr
  }

  def readInstance(label: Int, instance: Seq[Item]): Unit = {
    rootDistr.add(label)
    if (instance.nonEmpty) {
      val sortedInstance = instance.map(encode).sortWith(_ < _)
      for (tree <- trees) tree ! TreeGenerator.DataFrame(label, sortedInstance.toList)
    }
  }
}