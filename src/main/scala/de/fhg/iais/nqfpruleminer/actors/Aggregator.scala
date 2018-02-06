package de.fhg.iais.nqfpruleminer.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.routing.Broadcast
import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.nqfpruleminer.io.Reader

import scala.concurrent.ExecutionContextExecutor

object Aggregator {
  def props(listener: ActorRef)(implicit ctx: Context): Props = Props(classOf[Aggregator], listener, ctx)
}

class Aggregator(listener: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info("Started")
  val histories: List[History] = ctx.aggregateFeatures.map(feature => History(feature))

  implicit val executor: ExecutionContextExecutor = context.dispatcher

  def receive: Receive = {
    case TimedDataFrame(label, dateTime, values) =>
//      val aggregatedValuesFutures: List[Future[List[Value]]] = histories.map(history => Future(history(label, dateTime, values)))
//      Future.sequence(aggregatedValuesFutures) onComplete {
//        case Success(aggregatedValues) => listener ! DataFrame(label, values ++ aggregatedValues.flatten)
//        case Failure(e) => log.info(e.getLocalizedMessage)
//      }
      val aggregatedValues: List[Value] = histories.flatMap(history => history(label, dateTime, values))
      listener ! DataFrame(label, values ++ aggregatedValues)
    case msg@Broadcast(Reader.Terminated) =>
      listener ! msg
    case x =>
      log.info(x.toString)
  }
}