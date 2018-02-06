package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.nqfpruleminer.io.Reader

import scala.collection.mutable

object Worker {
  case class Count(table: Map[Value, Distribution], rootDistr: Distribution)
  case object Terminated

  class Table {
    private val c = mutable.ArrayBuffer[Array[DataFrame]]()
    val size = 10000
    var last = 0

    def expand(): Unit = c.append(new Array[DataFrame](size))

    def add(v: DataFrame): Unit = {
      try {
        c(last / size)(last % size) = v
        last += 1
      } catch {
        case _: IndexOutOfBoundsException =>
          expand()
          add(v)
      }
    }

    def foreach(f: DataFrame => Unit): Unit = c.foreach(a => a.foreach(inst => if (inst != null) f(inst)))
  }

  def props(listener: ActorRef)(implicit ctx: Context): Props =
    Props(classOf[Worker], listener, ctx)
}

class Worker(listener: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info(s"Started.")

  private val distributions = collection.mutable.Map[Value, Distribution]()
  private val instances = new Worker.Table
  private val rootDistr: Distribution = Distribution()(ctx.numberOfTargetGroups)

  def receive: Receive = {
    case dataFrame@DataFrame(label, values) =>
      rootDistr.add(label)
      instances.add(dataFrame)
//      log.debug(frequencies.toString)
      values.foreach(
        item =>
          distributions get item match {
            case None => distributions += (item -> Distribution(label)(ctx.numberOfTargetGroups))
            case Some(distribution) => distribution.add(label)
          }
      )
    case Reader.Terminated =>
      log.info("Reader.Terminated")
      log.debug(s"table.size ${instances.last}")
      listener ! Worker.Count(distributions.toMap, rootDistr)
    case Master.GenerateTrees(nqFpTrees, coding) =>
      instances.foreach {
        case DataFrame(label, values) =>
          if (values.nonEmpty) {
            rootDistr.add(label)
            val sortedInstance = values.flatMap(coding.encode).sortWith(_ < _)  // encoding is "partial"
            for ((range, tree) <- nqFpTrees) {
              val instance = sortedInstance.filter(_ < range.end)
              if (instance.exists(_ >= range.start)) {
                tree ! NqFpTree.EncodedInstance(label, instance)
              }
            }
          }
      }
      listener ! Worker.Terminated
      self ! PoisonPill
  }
}