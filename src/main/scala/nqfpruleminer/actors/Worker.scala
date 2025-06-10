package nqfpruleminer.actors

import nqfpruleminer.*
import nqfpruleminer.io.Reader
import org.apache.pekko.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props}

import scala.collection.mutable

object Worker {
  case class Count(table: List[(Item, Distribution)], rootDistr: Distribution)
  case object Terminated

  class Table {
    private val c = mutable.ArrayBuffer[Array[DataFrame]]()
    val size = 10000
    var last = 0

    def expand(): Unit = c.append(new Array[DataFrame](size))

    def add(v: DataFrame): Unit = {
      try {
        c(last / size)(last % size) = v
      } catch {
        case _: IndexOutOfBoundsException =>
          expand()
          add(v)
      }
      last += 1
    }

    def foreach(f: DataFrame => Unit): Unit = c.foreach(a => a.foreach(inst => if (inst != null) f(inst)))
    def update(f: DataFrame => DataFrame): Unit = c.foreach(a => for (i <- a.indices) if (a(i) != null) a(i) = f(a(i)))
  }

  def props(listener: ActorRef)(implicit ctx: Context): Props =
    Props(classOf[Worker], listener, ctx)
}

class Worker(listener: ActorRef)(implicit ctx: Context) extends Actor with ActorLogging {
  log.info(s"Started.")

  private val distributions = collection.mutable.Map[Item, Distribution]()
  private val instances = new Worker.Table
  private val rootDistr: Distribution = Distribution()(ctx.numberOfTargetGroups)

//  private val intervalBinning: Map[Position, List[Bin]] =
//    ctx.simpleFeatures
//      .flatMap(
//        feature =>
//          feature.typ match {
//            case BinningType.INTERVAL(delimiters, overlapping) =>
//              Some(feature.position -> Discretization.delimiters2bins(delimiters, overlapping))
//            case _ =>
//              None
//          }
//      ).toMap

//  println(intervalBinning.toString())

  def receive: Receive = {
    case DataFrame(label, simpleItems, derivedItems) =>
      rootDistr.add(label)
      val filteredSimpleItems =
        simpleItems
          .filter(_.value match { case Numeric(v, _) => !v.isNaN; case NoValue => false; case _ => true })
//          .flatMap {
//            case item@Valued(value: Numeric, position) =>
//              intervalBinning.get(position) match {
//                case Some(bins) =>
//                  value.toBin(bins).map(v => Valued(v, position))
//                case None =>
//                  List(item)
//              }
//            case item => List(item)
//          }

      val allItems = filteredSimpleItems ++ derivedItems
      allItems.foreach(
        item =>
          distributions get item match {
            case None => distributions += (item -> Distribution(label)(ctx.numberOfTargetGroups))
            case Some(distribution) => distribution.add(label)
          }
      )
      instances.add(DataFrame(label, filteredSimpleItems, derivedItems))

    case Reader.Terminated =>
      listener ! Worker.Count(distributions.toList, rootDistr)

    case Master.GenerateTrees(nqFpTrees, coding) =>
      instances.foreach {
        case DataFrame(label, baseItems, derivedItems) =>
          val allItems = baseItems.flatMap(coding.toBin) ++ derivedItems
          if (allItems.nonEmpty) {
            rootDistr.add(label)
            val sortedInstance = allItems.map(coding.encode).sortWith(_ < _)  // encoding is "partial"
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