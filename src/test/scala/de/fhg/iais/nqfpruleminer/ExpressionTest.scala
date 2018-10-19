package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Expression._
import org.scalatest.FunSuite

class ExpressionTest extends FunSuite {

  implicit private val ctx: Context = new Context("src/test/resources/expressiontest.conf")

  test("xy") {
    assert(Id("xy", -1) == parseLiteral("xy"))
  }

  test("3.0") {
    assert(Val(Numeric(3.0)) == parseLiteral("   3.0    "))
  }

  test("Literal a") {
    assert(Val(Nominal("a")) == parseLiteral("\"a\""))
  }

//  test("3.0xx") {
//    assert(Val(Numeric(3.0)) != parseLiteral("3.0xx"))
//  }

  test("x < 3.0") {
    assert(LT(Id("x", 0), Val(Numeric(3.0))) == parse("x < 3.0", "test").updatePosition(ctx.attributeToPosition))
  }

  test("'true") {
    assert(TRUE == parse("'true", "test").updatePosition(ctx.attributeToPosition))
  }

  test("x <= 3.0") {
    assert(LE(Id("x", 0), Val(Numeric(3.0))) == parse("x <= 3.0", "test").updatePosition(ctx.attributeToPosition))
  }

  test("y > 3.0 && x <= 3.0") {
    assert(
      parse("y > 3.0 && x <= 3.0", "test").updatePosition(ctx.attributeToPosition) ==
        AND(List(GT(Id("y", 1), Val(Numeric(3.0))), LE(Id("x", 0), Val(Numeric(3.0))))))
  }

  test("x > 3.0 && y != 3.0 && z <= 3.0") {
    assert(parse("x > 3.0 && y != 3.0 && z <= 3.0", "test").updatePosition(ctx.attributeToPosition) ==
      AND(List(GT(Id("x", 0), Val(Numeric(3.0))), NE(Id("y", 1), Val(Numeric(3.0))), LE(Id("z", 2), Val(Numeric(3.0)))))
    )
  }

  test("x > 3.0 && y != 3.0 || z <= 3.0") {
    assert(parse("x > 3.0 &&  y != 3.0 || z <= 3.0", "test").updatePosition(ctx.attributeToPosition) ==
      OR(List(AND(List(GT(Id("x", 0), Val(Numeric(3.0))), NE(Id("y", 1), Val(Numeric(3.0))))), LE(Id("z", 2), Val(Numeric(3.0))))))
  }
}
