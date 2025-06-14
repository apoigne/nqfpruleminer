package nqfpruleminer.actors

import nqfpruleminer.io.Reader
import nqfpruleminer.{Context, DataFrame, History, TimedDataFrame}
import org.apache.pekko
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, Props}
import org.apache.pekko.routing.Broadcast
import org.apache.pekko.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.*
    
object Aggregator {
  def props(listener: ActorRef)(implicit ctx: Context): Props = Props(classOf[Aggregator], listener, ctx)
}

class Aggregator(listener: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info("Started")
  implicit val timeout: Timeout = 10.seconds
  implicit val executor: ExecutionContextExecutor = context.dispatcher

  val histories: Vector[History] = ctx.aggregateFeatures.map(feature => History(feature))

  def receive: Receive = {
    case TimedDataFrame(label, dateTime, baseItems, derivedItems) =>
      val aggregatedItems = histories.map(history => history(label, Some(dateTime), baseItems))
      listener ! DataFrame(label, baseItems, derivedItems ++ aggregatedItems.flatten)
    case DataFrame(label, baseItems, derivedItems) =>
      val aggregatedItems = histories.map(history => history(label, None, baseItems))
      listener ! DataFrame(label, baseItems, derivedItems ++ aggregatedItems.flatten)
    case msg@Broadcast(Reader.Terminated) =>
      listener ! msg
  }
}