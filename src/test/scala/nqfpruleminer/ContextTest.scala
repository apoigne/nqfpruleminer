package nqfpruleminer

import nqfpruleminer.{Context, Date, Value}
import org.scalatest.funsuite.AnyFunSuite

class ContextTest extends AnyFunSuite {

  implicit private val ctx: Context = new Context("src/test/resources/filtered_table_ohne_weight.conf")

  test("2010-08-04 00:00:00") {
   assert(Value("2010-08-04 00:00:00") ==  Date(ctx.timeframe.get.parseDateTime("2010-08-04 00:00:00")))
  }
}
