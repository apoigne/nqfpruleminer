package de.fhg.iais.nqfpruleminer

import org.scalatest.FunSuite
import spray.json._
import de.fhg.iais.nqfpruleminer._
import Expression._

//class ExpressionTest extends FunSuite {

//  test("test json") {
//
//    val attr = Attr("name")
//    assert(attr == attr.toJson.convertTo[Literal])
//
//    val v = Val(Numeric(0.0))
//    assert(v == v.toJson.convertTo[Literal])
//
//    val e = EQ(Attr("name"), Val(Numeric(0.0)))
//    assert(e == e.toJson.convertTo[EQ])
//
//    val a = AND(e,e)
//    assert(a == a.toJson.convertTo[AND])
//
//    val b = OR(e,e)
//    assert(b == b.toJson.convertTo[OR])
//  }
//}
