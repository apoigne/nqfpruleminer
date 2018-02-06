package de.fhg.iais.nqfpruleminer

sealed trait Strategy
case object Prune extends Strategy
case class Pursue(quality: Double, generality: Double, estimate: Double) extends Strategy

sealed trait QualityMode {
  val eval: Distribution => Strategy
}

// TODO understand this function
case class Lift(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityMode {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")

  val eval: Distribution => Strategy =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      if (n == 0.0) {
        Prune
      } else {
        val g = n / n0
        val d = distr(1)
        val p = d / n
        if (g >= minG && p >= minP) {
          Pursue((d * n0) / (p0(1) * n), g, d)
        } else {
          Prune
        }
      }
    }
}

case class Piatetsky(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityMode {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")
  private val oeConst = (1.0 - p0(1)) / n0

  val eval: Distribution => Strategy =
    (distr: Distribution) => {
      val n = distr.sum
      if (n == 0) {
        Prune
      } else {
        val g = n / n0
        val p1 = distr.probability(1)
        if (g >= minG && p1 >= minP) {
          Pursue(g * (p1 - p0(1)), g, distr(1) * oeConst)
        } else {
          Prune
        }
      }
    }
}

case class Binomial(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityMode {
  assert(p0.lengthCompare(2) == 0, s"Length of p0 must be 2 ")
  private val oeConst = (1.0 - p0(1)) / n0

  val eval: Distribution => Strategy =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      if (n == 0.0) {
        Prune
      } else {
        val g = Math.sqrt(n / n0)
        val d = distr(1)
        val p = d / n
        if (g >= minG && p >= minP) {
          Pursue(g * (p - p0(1)), g, g * p * oeConst)
        } else {
          Prune
        }
      }
    }
}

case class Gini(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityMode {
  val eval: Distribution => Strategy =
    (distr: Distribution) => {
      val n = distr.sum
      if (n == 0) {
        Prune
      } else {
        val g = n / n0
        if (g >= minG) {
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
          Pursue(sum1, g, sum2)
        } else {
          Prune
        }
      }
    }
}

case class Split(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityMode {
  val eval: Distribution => Strategy =
    (distr: Distribution) => {
      val n = distr.sum
      if (n == 0) {
        Prune
      } else {
        val g = n / n0
        if (g >= minG) {
          var sum1 = 0.0
          var sum2 = 0.0
          val p = distr.probability
          for (i <- 1 to numberOfTargetGroups) {
            val pi = p(i)
            val p0i = p0(i)
            sum1 = sum1 + scala.math.pow(pi - p0i, 2.0)
            sum2 = sum2 + 1.0 * scala.math.max(pi * scala.math.pow(1.0 - p0i, 2.0), scala.math.pow(1.0 - pi, 2.0))
          }
          Pursue(g * sum1, g, g * sum2)
        } else {
          Prune
        }
      }
    }
}

case class Pearson(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityMode {
  val eval: Distribution => Strategy =
    (distr: Distribution) => {
      val n = distr.sum.toDouble
      if (n == 0.0) {
        Prune
      } else {
        val g = n / n0
        if (g >= minG) {
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
          Pursue(g * sum1, g, g * sum2)
        } else {
          Prune
        }
      }
    }
}