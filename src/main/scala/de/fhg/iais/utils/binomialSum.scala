package de.fhg.iais.utils

object binomialSum {
  def binomialCoefficient(n: Long, k: Int): Long =
    (1 to k).foldLeft(1L)((x, i) => (x * (n + 1 - i)) / i)

  def apply(n: Long, k: Int): Long =
    if (k > 0) binomialCoefficient(n, k) + apply(n, k - 1) else 0
}
