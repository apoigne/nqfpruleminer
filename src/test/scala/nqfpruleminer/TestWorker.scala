package nqfpruleminer

import nqfpruleminer.actors.Worker
import nqfpruleminer.io.{Provider, Reader}
import nqfpruleminer.{Context, Nominal, Valued}
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.routing.RoundRobinPool
import org.apache.pekko.testkit.{ImplicitSender, TestKitBase, TestProbe}
import org.apache.pekko.util.Timeout
import org.scalatest.*
import org.scalatest.funsuite.AnyFunSuite

import scala.concurrent.duration.*

class TestWorker extends AnyFunSuite with TestKitBase with ImplicitSender with BeforeAndAfterAll {

  implicit lazy val system: ActorSystem = ActorSystem("test")
  implicit val timeout: Timeout = 20.seconds

  implicit val ctx: Context = new Context("src/test/resources/testworker.conf")

  private val provider = Provider(ctx.providerData)
  private val p = TestProbe()
  private val workers = system.actorOf(RoundRobinPool(ctx.numberOfWorkers).props(Worker.props(p.ref)), name = "worker")
  private val reader = new Reader(provider, workers)

  override def afterAll(): Unit = {
    system.terminate()
  }

  test("frequencies of worker") {
    reader.run()
    val frequencies = p.expectMsgPF(5.seconds) { case Worker.Count(m, _) => m }
    val seqID = frequencies.toMap.get(Valued(Nominal("4712"), 1))
    assert(
      seqID match {
        case None => false
        case Some(distr) => distr(0) == 4 && distr(1) == 1

      }
    )

  }
}


