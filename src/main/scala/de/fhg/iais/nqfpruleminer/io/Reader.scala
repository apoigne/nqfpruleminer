package de.fhg.iais.nqfpruleminer.io

import akka.actor.ActorRef
import akka.routing.Broadcast
import de.fhg.iais.nqfpruleminer._
import de.fhg.iais.utils.fail
import org.joda.time.DateTime

import scala.util.{Failure, Success, Try}

object Reader {
  case object Terminated
}

class Reader(provider: Provider, listener: ActorRef)(implicit ctx: Context) {
  //  only simple features are considered, i.e. excluding the time feature if it exists
  private val columnsUsed: Vector[(Feature, Int)] = provider.featureToPosition
  private val length = columnsUsed.length

  private var counter = 0L
  private var lastDateTime: DateTime = DateTime.parse("0")

  def run(): Unit = {
    while (provider.hasNext) {
      val instance = provider.next
      assert(instance.length >= length,
        s"Input line $counter: Number of instance values ${instance.length} is different to the number of features ${ctx.simpleFeatures.length}.")
      val label =
        Try(ctx.targetGroups.indexOf(instance(provider.targetIndex))) match {
          case Success(index) => index + 1
          case Failure(_) => 0
        }
      // non target values have label 0

      val values = columnsUsed
        .map { case (feature, columnIndex) => Value(feature.typ, instance(columnIndex), label) }
      if (!ctx.instanceFilter.eval(values)) {
        println(s"Instance (${instance.reduce(_ + "," + _)}) does not satisfy the instance filter rule.")
      } else {
        val simpleItems =
          columnsUsed
            .map {
              case (feature, columnIndex) =>
                if (feature.condition.eval(values)) {
                  Valued(values(feature.position), feature.position)
                } else {
                  Valued(NoValue, feature.position)
                }

            }
        counter += 1

        if (values.length == columnsUsed.length && ctx.instanceFilter.eval(values)) {
          val compoundItems =
            ctx.compoundFeatures.map {
              case Feature(_, DerivedType.COMPOUND(positions), _, position) =>
                val itemTuple = positions.map(pos => simpleItems(pos))
                if (itemTuple.exists(_.value == NoValue)) {
                  Valued(NoValue, position)
                } else {
                  Compound(itemTuple, position)
                }
              case _ => fail("Grouped features should only have derived type Compound"); null
            }
          val prefixedItems =
            ctx.prefixFeatures.map {
              case Feature(_, DerivedType.PREFIX(number, basePosition), _, position) =>
                simpleItems(basePosition).value match {
                  case NoValue => Valued(NoValue, position)
                  case Nominal(s) => Valued(Nominal(s.take(number)), position)
                  case _ => fail("Internal error: Prefix features should only bre defined for Nominals"); null
                }
//                Value(SimpleType.NOMINAL, simpleItems(basePosition).toString.take(number), label).map(value => Valued(value, position))
              case _ =>
                fail("Prefix features should only have derived type PREFIX"); null
            }
          val rangedItems =
            ctx.rangeFeatures
              .map {
                case Feature(_, DerivedType.RANGE(lo, hi, basePosition), _, position) =>
                  simpleItems(basePosition).value match {
                    case NoValue => Valued(NoValue, position)
                    case Numeric(v,_) => if (lo <= v && v <= hi) Valued(Logical(true), position) else Valued(NoValue, position)
                    case _ => fail("Internal error: Range features should only bre defined for Numerics"); null
                  }
                case _ =>
                  fail("Ranged features should only have derived type RANGE"); null
              }
          val derivedItems: Vector[Item] = compoundItems ++ prefixedItems ++ rangedItems

          val timeIndex = provider.timeIndex
          if (ctx.requiresAggregation && timeIndex.isDefined) {
            val dateTime = ctx.timeframe.get.parseDateTime(instance(timeIndex.get))
            if (dateTime.compareTo(lastDateTime) < 0) {
              println(s"Input line $counter: timestamp of this instance is smaller than that for the previous instance. Instance is skipped")
            } else {
              lastDateTime = dateTime
              listener ! TimedDataFrame(label, dateTime, simpleItems, derivedItems)
            }
          } else {
            listener ! DataFrame(label, simpleItems, derivedItems)
          }
        } else {
          println(s"Instance skipped: ${if (instance.isEmpty) "empty instance" else instance.reduce(_ + "," + _)} ")
        }
      }
    }
    listener ! Broadcast(Reader.Terminated)
  }
}