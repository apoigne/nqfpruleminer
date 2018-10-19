package de.fhg.iais.nqfpruleminer

import org.scalatest.FunSuite

class ContextTest extends FunSuite {

  implicit private val ctx: Context = new Context("src/test/resources/filtered_table_ohne_weight.conf")

  test("2010-08-04 00:00:00") {
   assert(Value("2010-08-04 00:00:00") ==  Date(ctx.timeframe.get.parseDateTime("2010-08-04 00:00:00")))
  }
}
