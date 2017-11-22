package de.fhg.iais.nqfpruleminer

import org.scalatest.FunSuite

class TestDiscretisation extends FunSuite {

  val l =
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

  val labels: List[Int] =
    List(
      0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1, 1,
      0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 1, 0,
      0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 1,
      1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 0, 0,
      1, 1, 1
    )
  val ll: List[(Double, Int)] = l.zip(labels)

  val dm1: List[(Double, Int)] = l.map(x => x -> 1)
  val dm: List[(Double, Int)] = l.groupBy(identity).mapValues(_.length).toList

  val ldm1: List[((Double, Int), Int)] = ll.map(x => x -> 1)
  val ldm: List[((Double, Int), Int)] = ll.groupBy(identity).mapValues(_.length).toList

  test("Interval binning test") {
    val bins1 = Intervals(List(0.2, 0.5, 0.7)).genBins(dm1)
    val bins = Intervals(List(0.2, 0.5, 0.7)).genBins(dm)
    assert(bins1 == bins)
    val d2r = l.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    println(d2r.groupBy(_._2).mapValues(_.length))
  }

  test("Equal width binning test") {
    val bins1 = EqualWidth("xxx", 4).genBins(dm1)
    val bins = EqualWidth("xxx",4).genBins(dm)
    assert(bins1 == bins)
    println(bins1)
    val d2r = l.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    println(d2r.groupBy(_._2).mapValues(_.length))

  }

  test("Equal frequency binning test") {
    //    val bins1 = EqualFrequency(4).genDelimiters(dm1)
    val bins = EqualFrequency("xxx",4).genBins(dm)
//    assert(bins1 == bins)
    println(bins)
    val d2r = l.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    println(d2r.groupBy(_._2).mapValues(_.length))
  }

  test("Entropy binning test") {
    val bins1 = Entropy("xxx",4, 2).genBins(ldm1)
    val bins = Entropy("xxx",4, 2).genBins(ldm)
    assert(bins1 == bins)
    println(bins1)
    val d2r = l.map(x => x -> bins.find(range => range.lo <= x && x < range.hi))
    println(d2r.groupBy(_._2).mapValues(_.length))
  }
}
