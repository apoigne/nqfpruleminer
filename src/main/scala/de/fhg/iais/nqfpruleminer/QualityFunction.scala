package de.fhg.iais.nqfpruleminer

case class Quality(quality: Double, generality: Double, estimate: Double)

sealed trait QualityFunction {
  val eval: Distribution => Quality
}

// TODO understand this function
case class Lift(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityFunction {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")

  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      val g = n / n0
      val d = distr(1)
      Quality((d * n0) / (p0(1) * n), g, d)
    }
}

case class Piatetsky(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityFunction {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")
  private val oeConst = (1.0 - p0(1)) / n0

  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum
      val g = n / n0
//      val p1 = distr.probability(1)
      Quality(distr(1) * (1.0 - p0(1)), g, distr(1) * oeConst)
    }
}

case class Binomial(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityFunction {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")
  private val oeConst = (1.0 - p0(1)) / n0

  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      val g = Math.sqrt(n / n0)
      val p1 = distr.probability(1)
      Quality(g * (p1 - p0(1)), g, g * p1 * oeConst)
    }
}

case class Gini(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityFunction {
  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum
      val g = n / n0
      var sum1 = 0.0
      var sum2 = 0.0
      val p = distr.probability
      for (i <- 1 to numberOfTargetGroups) {
        val pi = p(i)
        val xi = n * (1.0 - pi)
        val em = (xi / (n0 - n * xi)) * scala.math.pow(pi, 2.0)
        val yi = n * pi
        val ep = (yi / (n0 - n * yi)) * scala.math.pow(1.0 - pi, 2.0)
        sum1 = sum1 + scala.math.pow(pi - n0, 2.0)
        sum2 = sum2 + scala.math.max(scala.math.max(em, sum1), ep)
      }
      Quality(sum1, g, sum2)
    }
}

case class Split(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityFunction {
  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum
      val g = n / n0
      var sum1 = 0.0
      var sum2 = 0.0
      val p = distr.probability
      for (i <- 1 to numberOfTargetGroups) {
        val pi = p(i)
        val p0i = p0(i)
        sum1 = sum1 + scala.math.pow(pi - p0i, 2.0)
        sum2 = sum2 + 1.0 * scala.math.max(pi * scala.math.pow(1.0 - p0i, 2.0), scala.math.pow(1.0 - pi, 2.0))
      }
      Quality(g * sum1, g, g * sum2)
    }
}

case class Pearson(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityFunction {
  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      val g = n / n0
      var sum1 = 0.0
      var sum2 = 0.0
      val p = distr.probability
      for (i <- 1 to numberOfTargetGroups) {
        val pi = p(i)
        val p0i = p0(i)
        sum1 = sum1 + scala.math.pow(pi - p0i, 2.0)
        sum2 = sum2 + p0(i) *
          scala.math.max(pi * scala.math.pow(1.0 - p0i, 2.0),
            scala.math.pow(1.0 - pi, 2.0))
      }
      Quality(g * sum1, g, g * sum2)
    }
}