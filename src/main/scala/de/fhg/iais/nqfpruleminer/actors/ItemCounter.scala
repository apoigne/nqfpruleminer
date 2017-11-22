package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import de.fhg.iais.nqfpruleminer.actors.ItemCounter.Start
import de.fhg.iais.nqfpruleminer.io.{Provider, Reader}
import de.fhg.iais.nqfpruleminer.{Context, Instance, Item}

object ItemCounter {
  case object Start
  case class Count(table: Map[Item, Int])

  def props(kind: Provider.Data)(implicit ctx: Context): Props = Props(classOf[ItemCounter], kind, ctx)
}

class ItemCounter(kind: Provider.Data)(implicit ctx: Context) extends Actor with ActorLogging {
  private val frequencies = collection.mutable.Map[Item, Int]()

  def receive: Receive = {
    case Start =>
      val listener = sender()
      val reader = new Reader(Provider(kind))
      reader(countItems)
      listener ! ItemCounter.Count(frequencies.toMap)
      self ! PoisonPill
  }

  def countItems(label: Int, instance: Seq[Item]): Unit = {
    context.parent ! Instance(label, instance)
    instance foreach (
      item =>
        frequencies get item match {
          case None => frequencies += (item -> 1)
          case Some(x) => frequencies.update(item, x + 1)
        }
      )
  }
}
