package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.Broadcast
import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.nqfpruleminer.io.Reader
import de.fhg.iais.utils.fail

import scala.concurrent.ExecutionContextExecutor

object Aggregator {
  def props(listener: ActorRef)(implicit ctx: Context): Props = Props(classOf[Aggregator], listener, ctx)
}

class Aggregator(listener: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info("Started")
  val histories: Vector[History] = ctx.aggregateFeatures.map(feature => History(feature))

  implicit val executor: ExecutionContextExecutor = context.dispatcher

  def receive: Receive = {
    case TimedDataFrame(label, dateTime, baseItems, derivedItems) =>
      val aggregatedValues = histories.map(history => history(label, dateTime, baseItems))
      listener ! DataFrame(label, baseItems, derivedItems ++ aggregatedValues.flatten)
    case msg@Broadcast(Reader.Terminated) =>
      listener ! msg
    case msg: DataFrame =>
      fail(s"Internal error: aggregator got a dataframe instead of a timed dataframe")
  }
}