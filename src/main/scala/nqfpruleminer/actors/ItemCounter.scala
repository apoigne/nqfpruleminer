package nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import common.utils.Reader
import nqfpruleminer.Item

object ItemCounter {
  case class Count(table : Map[Item, Int])

  def props(): Props = Props[ItemCounter]
}

class ItemCounter extends Actor with ActorLogging {
  private val countTable = collection.mutable.Map[Item, Int]()

  def receive: Receive = {
    case dataFile: String =>
      log.info(dataFile)
      val listener = sender()
      val reader = new Reader(dataFile)
      reader(countItems)
      listener ! ItemCounter.Count(countTable.toMap)
      self ! PoisonPill
  }

  def countItems(label: Int, instance: Seq[Item]): Unit =
    instance foreach (
      item =>
        countTable get item match {
          case None => countTable += (item -> 1)
          case Some(x) => countTable.update(item, x + 1)
        }
      )
}
