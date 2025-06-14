package nqfpruleminer

import nqfpruleminer.actors.Aggregator
import nqfpruleminer.io.{Provider, Reader}
import nqfpruleminer.{Aggregated, AggregationOperator, Compound, Context, Counted, DataFrame, GroupBy, Nominal, Valued, io}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.testkit.{ImplicitSender, TestKitBase, TestProbe}
import org.apache.pekko.util.Timeout
import org.scalatest.*
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.*

class AggregatorTest extends AnyFunSuite with TestKitBase with ImplicitSender with BeforeAndAfterAll {

  implicit lazy val system: ActorSystem = ActorSystem("test")
  implicit val timeout: Timeout = 20.seconds

  override def afterAll(): Unit = {
    system.terminate()
  }

  def count(id: String, s: String, n: Int, v: Double = 1.0)(implicit ctx: Context): GroupBy =
    GroupBy(Some(Valued(Nominal(id), 0)), Counted(Compound(List(Valued(nqfpruleminer.Numeric(v), 2), Valued(Nominal(s), 1))), n, 3), 3)

  def aggr(id: String, v: Double)(implicit ctx: Context): GroupBy =
    GroupBy(Some(Valued(Nominal(id), 0)), Aggregated(AggregationOperator.sum, 2, v, 4), 4)

  test("Aggregates 1s") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortest1.conf")

    val provider = Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator1")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 6
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 7
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 8
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 9
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )

    }
    { // 10
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 11
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 12
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "b", 1), count("4711", "b", 1), aggr("4711", 1.0),
          count("4712", "b", 1, 2.0), aggr("4712", 2.0),
          count("4721", "b", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 13
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "b", 1), count("4711", "b", 1), aggr("4711", 1.0),
          count("4712", "b", 1, 2.0), aggr("4712", 2.0),
          count("4721", "b", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 14
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "b", 1), count("4711", "b", 1), aggr("4711", 1.0),
          count("4712", "b", 1, 2.0), aggr("4712", 2.0),
          count("4721", "b", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 15
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "b", 1), count("4711", "b", 1), aggr("4711", 1.0),
          count("4712", "b", 1, 2.0), aggr("4712", 2.0),
          count("4721", "b", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 16
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 17
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 18
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
  }

  test("Aggregates 1s with condition") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortest3.conf")

    val provider = io.Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator1+")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==  Set())
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 6
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 7
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 8
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 9
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )

    }
    { // 10
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 11
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 12
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(aggr("4711", 1.0),
          aggr("4712", 2.0),
          aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 13
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(aggr("4711", 1.0),
          aggr("4712", 2.0),
          aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 14
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(aggr("4711", 1.0),
          aggr("4712", 2.0),
          aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 15
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(aggr("4711", 1.0),
          aggr("4712", 2.0),
          aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 16
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==  Set())
    }
    { // 17
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==    Set())
    }
    { // 18
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==    Set())
    }
  }

  test("Aggregates 2s") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortest2.conf")

    val provider = io.Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator3+")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==  Set())
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==   Set())
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==  Set())
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==   Set())
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 6
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)))
    }
    { // 7
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0))
      )
    }
    { // 8
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 2), aggr("4711", 2.0),
          count("4712", "a", 2), aggr("4712", 2.0),
          count("4721", "a", 2), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 9
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 2), aggr("4711", 2.0),
          count("4712", "a", 2), aggr("4712", 2.0),
          count("4721", "a", 2), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )

    }
    { // 10
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 2), aggr("4711", 2.0),
          count("4712", "a", 2), aggr("4712", 2.0),
          count("4721", "a", 2), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 11
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 2), aggr("4711", 2.0),
          count("4712", "a", 2), aggr("4712", 2.0),
          count("4721", "a", 2), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 12
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), count("4711", "b", 1), aggr("4711", 2.0),
          count("4712", "a", 1), count("4712", "b", 1, 2.0), aggr("4712", 3.0),
          count("4721", "a", 1), count("4721", "b", 1), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 13
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), count("4711", "b", 1), aggr("4711", 2.0),
          count("4712", "a", 1), count("4712", "b", 1, 2.0), aggr("4712", 3.0),
          count("4721", "a", 1), count("4721", "b", 1), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 14
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), count("4711", "b", 1), aggr("4711", 2.0),
          count("4712", "a", 1), count("4712", "b", 1, 2.0), aggr("4712", 3.0),
          count("4721", "a", 1), count("4721", "b", 1), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 15
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), count("4711", "b", 1), aggr("4711", 2.0),
          count("4712", "a", 1), count("4712", "b", 1, 2.0), aggr("4712", 3.0),
          count("4721", "a", 1), count("4721", "b", 1), aggr("4721", 2.0),
          count("4722", "a", 2), aggr("4722", 2.0)
        )
      )
    }
    { // 16
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 17
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 18
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
  }



  test("Aggregates 1s offset 1s") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortestwithoffset.conf")

    val provider = io.Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregatorwithoffset")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 6
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 7
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 8
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 9
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )

    }
    { // 10
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 11
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 12
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 13
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 14
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 15
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 16
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 17
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 18
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
  }

  test("Aggregates 1n") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortest4.conf")

    val provider = io.Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator3n")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==  Set())
//        Set(count("4711", "a", 1), aggr("4711", 1.0)))
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0)
        )
      )
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0)
        )
      )
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0)
        )
      )
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "a", 1), aggr("4711", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 6
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0),
        )
      )
    }
    { // 7
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0)
        )
      )
    }
    { // 8
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          aggr("4712", 1.0), count("4712", "a", 1),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 9
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "b", 1), aggr("4711", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 10
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "b", 1), aggr("4711", 1.0),
          count("4712", "b", 1, 2.0), aggr("4712", 2.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }

  }

  test("Aggregates 1n+") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortest5.conf")

    val provider = io.Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator3n+")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet == Set())
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0)
        )
      )
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0)
        )
      )
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(

          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "a", 1), aggr("4711", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 6
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
        )
      )
    }
    { // 7
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 8
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 9
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 10
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
        )
      )
    }
    { // 11
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 12
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 13
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
    { // 14
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4721", "a", 1), aggr("4721", 1.0),
        )
      )
    }
    { // 15
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4721", "a", 1), aggr("4721", 1.0),
        )
      )
    }
    { // 16
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "a", 1), aggr("4711", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0),
        )
      )
    }
  }

  test("Aggregates 3n + offest 1n") {
    implicit val ctx: Context = Context("src/test/resources/aggregatortest6.conf")

    val provider = io.Provider(ctx.providerData)
    val p = TestProbe()
    val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator3n")
    val reader = new Reader(provider, aggregator)

    reader.run()

    { // 0
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==  Set())
    }
    { // 1
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==   Set())
    }
    { // 2
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(count("4711", "a", 1), aggr("4711", 1.0)
        )
      )
    }
    { // 3
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4711", "a", 1), aggr("4711", 1.0),
          count("4712", "a", 1), aggr("4712", 1.0)
        )
      )
    }
    { //4
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4712", "a", 1), aggr("4712", 1.0),
          count("4721", "a", 1), aggr("4721", 1.0)
        )
      )
    }
    { // 5
      val msg = p.expectMsgPF(5.seconds) { case x => x }
      assert(msg.asInstanceOf[DataFrame].derivedItems.toSet ==
        Set(
          count("4721", "a", 1), aggr("4721", 1.0),
          count("4722", "a", 1), aggr("4722", 1.0)
        )
      )
    }
  }
}
