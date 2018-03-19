package de.fhg.iais.nqfpruleminer

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKitBase, TestProbe}
import akka.util.Timeout
import de.fhg.iais.nqfpruleminer.actors.Aggregator
import de.fhg.iais.nqfpruleminer.io.Reader
import org.scalatest._

import scala.concurrent.duration._

class AggregatorTest extends FunSuite with TestKitBase with ImplicitSender with BeforeAndAfterAll {

  implicit lazy val system: ActorSystem = ActorSystem("test")
  implicit val timeout: Timeout = 20.seconds

  implicit val ctx: Context = new Context("src/test/resources/historytest1.conf")

  private val provider = io.Provider(ctx.providerData)
  val p = TestProbe()
  private val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator")
  val reader = new Reader(provider, aggregator)

  override def afterAll(): Unit = {
    system.terminate()
  }

  val item4 = GroupBy(Some(Valued(Nominal("4711"), 0)), Counted(Group(List(Valued(Numeric(1.0), 2), Valued(Nominal("a"), 1))), None, 1, 3), 1, 3)
  val item5 = GroupBy(Some(Valued(Nominal("4711"), 0)), Aggregated(AggregationOp.Sum, 2, Some(1.0), 1, 4), 1, 4)
  val item6 = GroupBy(Some(Valued(Nominal("4721"), 0)), Counted(Group(List(Valued(Numeric(1.0), 2), Valued(Nominal("a"), 1))), None, 1, 3), 1, 3)
  val item7 = GroupBy(Some(Valued(Nominal("4721"), 0)), Aggregated(AggregationOp.Sum, 2, Some(1.0), 1, 4), 1, 4)
  val item8 = GroupBy(Some(Valued(Nominal("4722"), 0)), Counted(Group(List(Valued(Numeric(1.0), 2), Valued(Nominal("a"), 1))), None, 1, 3), 1, 3)
  val item9 = GroupBy(Some(Valued(Nominal("4722"), 0)), Aggregated(AggregationOp.Sum, 2, Some(1.0), 1, 4), 1, 4)
  val item10 = GroupBy(Some(Valued(Nominal("4711"), 0)), Aggregated(AggregationOp.Sum, 2, Some(2.0), 1, 4), 1, 4)

  test("Aggregates") {
    reader.run()

    {
      println("number 0")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item5))
    }
    {
      println("number 1")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item5))
    }
    {
      println("number 2")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item5))
    }
    {
      println("number 3")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item5))
    }
    {
      println("number 4")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item10))
    }
    {
      println("number 5")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item10))
    }
    {
      println("number 6")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item10, item6, item7))
    }
    {
      println("number 7")
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set(item4, item10, item6, item7, item8, item9))
    }
  }
}
