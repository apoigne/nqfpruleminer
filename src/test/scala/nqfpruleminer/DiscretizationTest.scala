package nqfpruleminer

import nqfpruleminer.{Bin, Context, Distribution, Entropy, EqualFrequency, EqualWidth, Intervals}
import nqfpruleminer.Item.Position
import nqfpruleminer.Value.Label
import org.scalatest.funsuite.AnyFunSuite

class DiscretizationTest extends AnyFunSuite {

  implicit val ctx: Context = Context("examples/istAnalyse/configuration.conf")
  implicit val position: Position = 0

  private val l =
    List(
      0.8406203020861771, 0.9893101459583835, 0.4897963362477239,
      0.6445524097255106, 0.47714423601203115, 0.5188175861327828,
      0.268762117326681, 0.21608042531390348, 0.06330832071150128,
      0.8406203020861771, 0.9893101459583835, 0.4897963362477239,
      0.6445524097255106, 0.47714423601203115, 0.5188175861327828,
      0.8531897023472996, 0.7679048318901919, 0.21590352502954913,
      0.0022959119736535305, 0.6834104310431929, 0.33625348176589487,
      0.8531897023472996, 0.7679048318901919, 0.21590352502954913,
      0.0022959119736535305, 0.6834104310431929, 0.33625348176589487,
      0.7238944588177526, 0.07778839744307853, 0.7571600227798514,
      0.6445524097255106, 0.47714423601203115, 0.5188175861327828,
      0.8531897023472996, 0.7679048318901919, 0.21590352502954913,
      0.0022959119736535305, 0.6834104310431929, 0.33625348176589487,
      0.8531897023472996, 0.7679048318901919, 0.21590352502954913,
      0.0022959119736535305, 0.6834104310431929, 0.33625348176589487,
      0.7238944588177526, 0.07778839744307853, 0.7571600227798514,
      0.49474891130555054, 0.9626699296312952, Double.NaN
    )

  val labels: List[Label] =
    List(
      0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1,
      0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0,
      0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 1,
      1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0,
      1, 1, 1
    )

  val ll: List[(Double, Label)] = l.zip(labels)

//  val dm: Map[Double, Distribution] = ll.groupBy(_._1).mapValues(l => Distribution(l.map(_._2))(2))
  val ldm: Map[(Double, Label), Distribution] = ll.groupBy(identity).view.mapValues(l => Distribution(l.map(_._2))(2)).to(Map)

  test("Interval binning test") {
    val bins = Intervals(List(0.2, 0.5, 0.7)).genBins(ldm)
    assert(bins.lengthCompare(4) == 0)
    assert(bins.zip(bins).forall { case (b1, b2) => b1.lo == b2.lo && b1.hi == b2.hi })
    bins.zip(List((0.0022959119736535305, 0.2), (0.2, 0.5), (0.5, 0.7), (0.7, 0.9894101459583835)))
      .foreach { case (x, y) => (x.lo, x.hi) == y }
    val d2r = l.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    assert(d2r.groupBy(_._2).view.mapValues(_.length)(Some(Bin(0.5, 0.7))) == 10)
  }

  test("Equal width binning test") {
    val bins = EqualWidth(4).genBins(ldm)
    assert(bins.lengthCompare(4) == 0)
    val width = bins.head.hi - bins.head.lo
    bins.foreach(bin => assert((bin.hi - bin.lo).toFloat == width.toFloat))
  }

  test("Equal frequency binning test") {
    val bins = EqualFrequency(4).genBins(ldm)
    assert(bins.lengthCompare(4) == 0)
    val d2r: List[(Double, Option[Bin])] = l.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    val d2rMap = d2r.toMap
    assert(d2r.groupBy(_._2).view.mapValues(_.length)(d2rMap(0.33625348176589487)) == 12)
  }

  private  val l2 = List(0.0, 4.0, 12.0, 16.0, 16.0, 18.0, 24.0, 26.0, 28.0)
  private val labels2 = List(0, 1, 0, 1, 0, 1, 1, 0, 0)
  private val ll2: List[(Double, Label)] = l2.zip(labels2)

  private val ldm2 = ll2.groupBy(identity).view.mapValues(l => Distribution(l.map(_._2))(2)).to(Map)

  test("Equal width binning test 2") {
    val bins = EqualWidth(3).genBins(ldm2)
    val width = bins.head.hi - bins.head.lo
    bins.foreach(bin => assert((bin.hi - bin.lo).toFloat == width.toFloat))
  }

  test("Equal frequency binning test 2") {
    val bins = EqualFrequency(3).genBins(ldm2)
    val d2r = l2.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    val d2rMap = d2r.toMap
    assert(d2r.groupBy(_._2).view.mapValues(_.length)(d2rMap(12.0)) == 3)
  }

  private val l3 = List(4.0, 5.0, 8.0, 12.0, 15.0)
  private val labels3 = List(0, 1, 0, 1, 1)
  val ll3: List[(Double, Label)] = l3.zip(labels3)

//  val dm3: Map[Double, Distribution] = ll3.groupBy(_._1).mapValues(l => Distribution(l.map(_._2))(2))
  val ldm3: Map[(Double, Label), Distribution] = ll3.groupBy(identity).view.mapValues(l => Distribution(l.map(_._2))(2)).toMap

  test("Entropy binning test 2") {
    val bins = Entropy(2).genBins(ldm3)
    val d2r = l3.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    val d2rMap = d2r.toMap
    assert(d2r.groupBy(_._2).view.mapValues(_.length)(d2rMap(12.0)) == 2)
  }

}
