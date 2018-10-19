package de.fhg.iais.nqfpruleminer

case class Quality(quality: Double, generality: Double, estimate: Double)

sealed trait QualityFunction {
  val eval: Distribution => Quality
}

// TODO understand this function
case class Lift(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityFunction {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")
  private val oeConst = 1.0 - p0(1)

  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      if (n == 0.0) {
        Quality(0.0, 0.0, 0.0)
      } else {
        val g = n / n0
        val d = distr(1)
        Quality((d * n0) / (p0(1) * n), g, d * oeConst)
      }
    }
}

case class Piatetsky(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityFunction {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")

  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      if (n == 0.0) {
        Quality(0.0, 0.0, 0.0)
      } else {
        val d = distr(1)
        val p1 = d / n
        val g = n / n0
        Quality(g * (p1 - p0(1)), g, d * (1.0 - p0(1)))
      }
    }
}

case class Binomial(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityFunction {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")
  private val oeConst = 1.0 - p0(1)

  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      if (n == 0.0) {
        Quality(0.0, 0.0, 0.0)
      } else {
        val d = distr(1)
        val g = Math.sqrt(n / n0)
        val p1 = d / n
        Quality(g * (p1 - p0(1)), g, Math.sqrt(d) * oeConst)
      }
    }
}

case class Gini(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityFunction {
  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      val g = n / n0
      if (n == 0.0 || g >= minG) {
        Quality(0.0, 0.0, 0.0)
      } else {
        val g = n / n0
        var sum1 = 0.0
        var sum2 = 0.0
        val p = distr
        for (i <- 1 to numberOfTargetGroups) {
          val pi = p(i) / n.toDouble
          val xi = n * (1.0 - pi)
          val em = (xi / (n0 - n * xi)) * scala.math.pow(pi, 2.0)
          val yi = n * pi
          val ep = (yi / (n0 - n * yi)) * scala.math.pow(1.0 - pi, 2.0)
          sum1 = sum1 + Math.pow(pi - n0, 2.0)
          sum2 = sum2 + Math.max(Math.max(em, sum1), ep)
        }
        Quality(sum1, g, sum2)
      }
    }
}

case class Split(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityFunction {
  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      val g = n / n0
      if (n == 0.0 || g >= minG) {
        Quality(0.0, 0.0, 0.0)
      } else {
        var sum1 = 0.0
        var sum2 = 0.0
        for (i <- 1 to numberOfTargetGroups) {
          val pi = distr(i) / n
          val p0i = p0(i)
          sum1 = sum1 + scala.math.pow(pi - p0i, 2.0)
          sum2 = sum2 + 1.0 * scala.math.max(pi * scala.math.pow(1.0 - p0i, 2.0), scala.math.pow(1.0 - pi, 2.0))
        }
        Quality(g * sum1, g, g * sum2)
      }
    }
}

case class Pearson(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityFunction {
  val eval: Distribution => Quality =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      val g = n / n0
      if (n == 0.0 || g >= minG) {
        Quality(0.0, 0.0, 0.0)
      } else {
        var sum1 = 0.0
        var sum2 = 0.0
        for (i <- 1 to numberOfTargetGroups) {
          val pi = distr(i) / n
          val p0i = p0(i)
          sum1 = sum1 + scala.math.pow(pi - p0i, 2.0)
          sum2 = sum2 + p0(i) * scala.math.max(pi * scala.math.pow(1.0 - p0i, 2.0), scala.math.pow(1.0 - pi, 2.0))
        }
        Quality(g * sum1, g, g * sum2)
      }
    }
}