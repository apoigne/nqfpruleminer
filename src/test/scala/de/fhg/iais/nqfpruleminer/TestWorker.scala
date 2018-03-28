package de.fhg.iais.nqfpruleminer

import akka.actor.ActorSystem
import akka.routing.RoundRobinPool
import akka.testkit.{ImplicitSender, TestKitBase, TestProbe}
import akka.util.Timeout
import de.fhg.iais.nqfpruleminer.actors.Worker
import de.fhg.iais.nqfpruleminer.io.Reader
import org.scalatest._

import scala.concurrent.duration._

class TestWorker extends FunSuite with TestKitBase with ImplicitSender with BeforeAndAfterAll {

  implicit lazy val system: ActorSystem = ActorSystem("test")
  implicit val timeout: Timeout = 20.seconds

  implicit val ctx: Context = new Context("src/test/resources/testworker.conf")

  private val provider = io.Provider(ctx.providerData)
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


