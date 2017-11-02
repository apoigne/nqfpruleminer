package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import de.fhg.iais.nqfpruleminer.{Context, Item, Reader}

object ItemCounter {
  case class Count(table: Map[Item, Int])

  def props()(implicit ctx: Context): Props = Props(classOf[ItemCounter], ctx)
}

class ItemCounter(implicit ctx: Context) extends Actor with ActorLogging {
  private val frequencies = collection.mutable.Map[Item, Int]()

  def receive: Receive = {
    case dataFile: String =>
      log.info(dataFile)
      val listener = sender()
      val reader = new Reader(dataFile)
      reader(countItems)
      listener ! ItemCounter.Count(frequencies.toMap)
      self ! PoisonPill
  }

  def countItems(label: Int, instance: Seq[Item]): Unit =
    instance foreach (
      item =>
        frequencies get item match {
          case None => frequencies += (item -> 1)
          case Some(x) => frequencies.update(item, x + 1)
        }
      )
}
