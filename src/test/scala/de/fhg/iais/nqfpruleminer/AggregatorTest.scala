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

  implicit val ctx: Context = new Context("src/test/resources/historytest1.conf", 0, 0)

  private val provider = io.Provider(ctx.providerData)
  val p = TestProbe()
  private val aggregator = system.actorOf(Aggregator.props(p.ref), name = "aggregator")
  val reader = new Reader(provider, aggregator)

  override def afterAll(): Unit = {
    system.terminate()
  }

  test("Aggregates") {
    var counter = 1
    reader.run()
    val msg1 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg1 + "\n");
    counter += 1
    //    assert(msg1 == DataFrame(0, List(Nominal("4711", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg2 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg2 + "\n");
    counter += 1
    //    assert(msg2 == DataFrame(0, List(Nominal("4712", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg3 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg3 + "\n");
    counter += 1
    //    assert(msg3 == DataFrame(0, List(Nominal("4721", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg4 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg4 + "\n");
    counter += 1
    //    assert(msg4 == DataFrame(0, List(Nominal("4722", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg5 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg5 + "\n");
    counter += 1
    //    assert(msg5 == DataFrame(0, List(Nominal("4711", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=true, 3))))
    val msg6 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg6 + "\n");
    counter += 1
    //    assert(msg6 == DataFrame(0, List(Nominal("4712", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=true, 3))))
    val msg7 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg7 + "\n");
    counter += 1
    //    assert(msg7 == DataFrame(0, List(Nominal("4721", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=true, 3))))
    val msg8 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg8 + "\n");
    counter += 1
    //    assert(msg8 == DataFrame(0, List(Nominal("4722", 0), Nominal("a", 1), Numeric(1.0, 2), Logical(value=true, 3))))
    val msg9 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg9 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg10 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg10 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg11 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg11 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg12 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg12 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))}
    val msg13 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg13 + "\n");
    counter += 1
    //    assert(msg14 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg14 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg14 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg15 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg15 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg16 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg16 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg17 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg17 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg18 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg18 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg19 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg19 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg20 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg20 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg21 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg21 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg22 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg22 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg23 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg23 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg24 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg24 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg25 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg25 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg26 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg26 + "\n");
    counter += 1
//    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg27 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg27 + "\n");
    counter += 1
//    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg28 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg28 + "\n");
    counter += 1
    //    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg29 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg29 + "\n");
    counter += 1
//    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
    val msg30 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg30 + "\n");
    counter += 1
//    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
val msg31 = p.expectMsgPF(5.seconds) { case x => x }
    println("\n" + counter.toString + " " + msg31 + "\n");
    counter += 1
//    assert(msg9 == DataFrame(0, List(Nominal("4711", 0), Nominal("b", 1), Numeric(1.0, 2), Logical(value=false, 3))))
  }
}
