package de.fhg.iais.nqfpruleminer

sealed trait Strategy
case object Prune extends Strategy
case class Quality(quality: Double, gen: Double, estimate: Double) extends Strategy

sealed trait QualityMode {
  val eval: Distribution => Strategy
}

case class Piatetsky(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityMode {
  private val oeConst = (1.0 - p0(1)) / n0

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
          Quality(g * (p - p0(1)), g, d * oeConst)
        } else {
          Prune
        }
      }
    }
}

case class Binomial(minG: Double, minP: Double, n0: Double, p0: Array[Double]) extends QualityMode {
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
          Quality(g * (p - p0(1)), g, g * p * oeConst)
        } else {
          Prune
        }
      }
    }
}

case class Gini(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityMode {
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
          for (i <- 0 to numberOfTargetGroups) {
            val pi = distr(i).toDouble / n
            val x = n * (1.0 - pi)
            val em = (x / (n0 - n * x)) * scala.math.pow(pi, 2.0)
            val y = n * pi
            val ep = (y / (n0 - n * y)) * scala.math.pow(1.0 - pi, 2.0)
            sum1 = sum1 + scala.math.pow(pi - n0, 2.0)
            sum2 = sum2 + scala.math.max(scala.math.max(em, sum1), ep)
          }
          Quality(sum1, g, sum2)
        } else {
          Prune
        }
      }
    }
}

case class Split(minG: Double, numberOfTargetGroups: Int, n0: Double, p0: Array[Double]) extends QualityMode {
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
          for (i <- 0 until numberOfTargetGroups) {
            val pi = distr(i).toDouble / n
            sum1 = sum1 + scala.math.pow(pi - p0(i), 2.0)
            sum2 = sum2 + 1.0 *
              scala.math.max(pi * scala.math.pow(1.0 - p0(i), 2.0),
                scala.math.pow(1.0 - pi, 2.0))
          }
          Quality(g * sum1, g, g * sum2)
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
          for (i <- 0 until numberOfTargetGroups) {
            val pi = distr(i).toDouble / n
            sum1 = sum1 + scala.math.pow(pi - p0(i), 2.0)
            sum2 = sum2 + p0(i) *
              scala.math.max(pi * scala.math.pow(1.0 - p0(i), 2.0),
                scala.math.pow(1.0 - pi, 2.0))
          }
          Quality(g * sum1, g, g * sum2)
        } else {
          Prune
        }
      }
    }
}