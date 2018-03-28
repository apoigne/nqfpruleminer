package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}
import akka.routing.Broadcast
import akka.pattern.ask
import akka.util.Timeout
import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.nqfpruleminer.io.Reader
import de.fhg.iais.utils.fail

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object Aggregator {
  def props(listener: ActorRef)(implicit ctx: Context): Props = Props(classOf[Aggregator], listener, ctx)
}

class Aggregator(listener: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info("Started")
  implicit val timeout : Timeout = 10.seconds

  val histories: Vector[History] = ctx.aggregateFeatures.map(feature => History(feature))

  implicit val executor: ExecutionContextExecutor = context.dispatcher

  def receive: Receive = {
    case TimedDataFrame(label, dateTime, baseItems, derivedItems) =>
       val aggregatedValues =  histories.map(history => history(label, dateTime, baseItems))
      listener ! DataFrame(label, baseItems, derivedItems ++ aggregatedValues.flatten)
    case msg@Broadcast(Reader.Terminated) =>
      listener ! msg
    case msg: DataFrame =>
      fail(s"Internal error: aggregator got a dataframe instead of a timed dataframe")
  }
}