package de.fhg.iais.nqfpruleminer

import com.typesafe.config.Config
import de.fhg.iais.nqfpruleminer.Item.Position
import de.fhg.iais.utils
import de.fhg.iais.utils.fail

import scala.util.{Failure, Success, Try}

object Expression {

  trait Literal {
    def attributes: Set[String]
    def eval(implicit env: Vector[Value]): Value
    def updatePosition(implicit posMap: Map[String, Position]): Literal
  }
  case class Attr(name: String, position: Position = -1) extends Literal {
    def attributes: Set[String] = Set(name)
    def eval(implicit env: Vector[Value]): Value = env(position)
    def updatePosition(implicit posMap: Map[String, Position]): Literal = Attr(name, posMap(name))
  }
  case class Val(value: Value) extends Literal {
    def attributes: Set[String] = Set()
    def eval(implicit env: Vector[Value]): Value = value
    def updatePosition(implicit posMap: Map[String, Position]): Literal = Val(value)
  }

  trait BoolExpr {
    def attributes: Set[String]
    def eval(implicit env: Vector[Value]): Boolean
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr
  }
  case object FALSE extends BoolExpr {
    def attributes: Set[String] = Set()
    def eval(implicit env: Vector[Value]): Boolean = false
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = this
  }
  case object TRUE extends BoolExpr {
    def attributes: Set[String] = Set()
    def eval(implicit env: Vector[Value]): Boolean = true
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = this
  }
  case class EQ(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval == arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = EQ(arg1.updatePosition, arg2.updatePosition)
  }
  case class NE(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval != arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = NE(arg1.updatePosition, arg2.updatePosition)
  }
  case class GT(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval > arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = GT(arg1.updatePosition, arg2.updatePosition)
  }
  case class GE(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval >= arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = GE(arg1.updatePosition, arg2.updatePosition)
  }
  case class LT(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval < arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = LT(arg1.updatePosition, arg2.updatePosition)
  }
  case class LE(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval <= arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = LE(arg1.updatePosition, arg2.updatePosition)
  }
  case class NOT(arg: BoolExpr) extends BoolExpr {
    def attributes: Set[String] = arg.attributes
    def eval(implicit env: Vector[Value]): Boolean = !arg.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = NOT(arg.updatePosition)
  }
  case class OR(arg1: BoolExpr, arg2: BoolExpr) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval || arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = OR(arg1.updatePosition, arg2.updatePosition)
  }
  case class AND(arg1: BoolExpr, arg2: BoolExpr) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval && arg2.eval
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = AND(arg1.updatePosition, arg2.updatePosition)
  }

  def  json2boolExpr (config: Config): BoolExpr = {

    def json2boolExpr(config: Config): BoolExpr =
      config getString ("op") match {
        case "eq" => EQ(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "ne" => NE(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "gt" => GT(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "ge" => GE(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "lt" => LT(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "le" => LE(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "and" => AND(json2boolExpr(config getConfig "arg1"), json2boolExpr(config getConfig "arg2"))
        case "or" => OR(json2boolExpr(config getConfig "arg1"), json2boolExpr(config getConfig "arg2"))
        case "not" => NOT(json2boolExpr(config getConfig "arg"))
      }

    def json2literal(config: Config): Literal =
      config getString ("op") match {
        case "attribute" => Attr(config getString "arg")
        case "double" => Val(Numeric(config getDouble "arg"))
      }

    Try (json2boolExpr(config)) match {
      case Success(bexp) => bexp
      case Failure(e)        => utils.fail(s"Illformed condition\n${config.toString}: ${e.getCause}"); null
    }

  }

//  implicit object ExprJsonFormat extends RootJsonFormat[Literal] {
//    def write(expr: Literal): JsValue =
//      expr match {
//        case Attr(name, _) =>
//          JsObject("kind" -> JsString("attribute"), "arg" -> JsString(name))
//        case Val(number) =>
//          number match {
//            case Nominal(v) =>
//              JsObject(
//                "kind" -> JsString("string"),
//                "arg" -> JsString(v)
//              )
//            case Numeric(v) =>
//              JsObject(
//                "kind" -> JsString("double"),
//                "arg" -> JsNumber(v)
//              )
//            case Logical(v) =>
//              JsObject(
//                "kind" -> JsString("boolean"),
//                "arg" -> JsBoolean(v)
//              )
////            case Date(v) =>
////              JsObject(
////                "kind" -> JsString("date"),
////                "arg" -> JsString(v.toString)
////              )
//          }
//      }
//    def read(jsValue: JsValue): Literal =
//      jsValue match {
//        case JsObject(fields) =>
//          (fields("kind"), fields("arg")) match {
//            case (JsString("attribute"), JsString(v)) => Attr(v)
//            case (JsString("boolean"), JsBoolean(v)) => Val(Logical(v))
//            case (JsString("integer"), JsNumber(v)) => Val(Numeric(v.toDouble))
//            case (JsString("double"), JsNumber(v)) => Val(Numeric(v.toDouble))
////            case (JsString("date"), JsString(v)) => Val(Date(ctx.parseDateTime(v)))
//          }
//      }
//  }
//
//  implicit object AttributeJsonFormat extends JsonFormat[Attr] {
//    def write(obj: Attr): JsValue = ExprJsonFormat.write(obj: Literal)
//    def read(jsValue: JsValue): Attr = BooleanExprJsonFormat.read(jsValue).asInstanceOf[Attr]
//  }
//
//  implicit object ValueJsonFormat extends JsonFormat[Val] {
//    def write(obj: Val): JsValue = ExprJsonFormat.write(obj: Literal)
//    def read(jsValue: JsValue): Val = BooleanExprJsonFormat.read(jsValue).asInstanceOf[Val]
//  }
//
//  private def toJsObject(op: String, arg1: Literal, arg2: Literal) =
//    JsObject(
//      "op" -> JsString(op),
//      "arg1" -> ExprJsonFormat.write(arg1),
//      "arg2" -> ExprJsonFormat.write(arg2)
//    )
//
//  private def toJsObject(op: String, arg1: BoolExpr, arg2: BoolExpr) =
//    JsObject(
//      "op" -> JsString(op),
//      "arg1" -> BooleanExprJsonFormat.write(arg1),
//      "arg2" -> BooleanExprJsonFormat.write(arg2)
//    )
//
//  private def toJsObject(op: String, args: Vector[BoolExpr]) =
//    JsObject(
//      "op" -> JsString(op),
//      "args" -> JsArray(args.map(arg => BooleanExprJsonFormat.write(arg)))
//    )
//
//  implicit object BooleanExprJsonFormat extends JsonFormat[BoolExpr] {
//    def write(obj: BoolExpr): JsValue =
//      obj match {
//        case EQ(arg1, arg2) => toJsObject("eq", arg1, arg2)
//        case NE(arg1, arg2) => toJsObject("ne", arg1, arg2)
//        case GT(arg1, arg2) => toJsObject("gt", arg1, arg2)
//        case GE(arg1, arg2) => toJsObject("ge", arg1, arg2)
//        case LT(arg1, arg2) => toJsObject("lt", arg1, arg2)
//        case LE(arg1, arg2) => toJsObject("le", arg1, arg2)
//        case FALSE => JsFalse
//        case TRUE => JsTrue
//        case NOT(arg) => JsObject("op" -> JsString("not"), "arg" -> BooleanExprJsonFormat.write(arg) )
//        case OR(arg1, arg2) => toJsObject("or", arg1, arg2)
//        case AND(arg1, arg2) => toJsObject("and", arg1, arg2)
//      }
//    def read(jsValue: JsValue): BoolExpr =
//      jsValue match {
//        case JsFalse => FALSE
//        case JsTrue => TRUE
//        case JsObject(fields) =>
//          fields("op") match {
//            case JsString("eq") =>
//              EQ(fields("arg1").convertTo[Literal], fields("arg2").convertTo[Literal])
//            case JsString("ne") =>
//              NE(fields("arg1").convertTo[Literal], fields("arg2").convertTo[Literal])
//            case JsString("gt") =>
//              GT(fields("arg1").convertTo[Literal], fields("arg2").convertTo[Literal])
//            case JsString("ge") =>
//              GE(fields("arg1").convertTo[Literal], fields("arg2").convertTo[Literal])
//            case JsString("lt") =>
//              LT(fields("arg1").convertTo[Literal], fields("arg2").convertTo[Literal])
//            case JsString("le") =>
//              LE(fields("arg1").convertTo[Literal], fields("arg2").convertTo[Literal])
//            case JsString("not") =>
//              NOT(fields("arg").convertTo[BoolExpr])
//            case JsString("or") =>
//              OR(fields("arg1").convertTo[BoolExpr], fields("arg2").convertTo[BoolExpr])
//            case JsString("and") =>
//              AND(fields("arg1").convertTo[BoolExpr], fields("arg2").convertTo[BoolExpr])
//
//          }
//      }
//  }
//
//  implicit object EQJsonFormat extends JsonFormat[EQ] {
//    def write(obj: EQ): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): EQ = BooleanExprJsonFormat.read(jsValue).asInstanceOf[EQ]
//  }
//  implicit object NEJsonFormat extends JsonFormat[NE] {
//    def write(obj: NE): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): NE = BooleanExprJsonFormat.read(jsValue).asInstanceOf[NE]
//  }
//  implicit object GTJsonFormat extends JsonFormat[GT] {
//    def write(obj: GT): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): GT = BooleanExprJsonFormat.read(jsValue).asInstanceOf[GT]
//  }
//  implicit object GEJsonFormat extends JsonFormat[GE] {
//    def write(obj: GE): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): GE = BooleanExprJsonFormat.read(jsValue).asInstanceOf[GE]
//  }
//  implicit object LTJsonFormat extends JsonFormat[LT] {
//    def write(obj: LT): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): LT = BooleanExprJsonFormat.read(jsValue).asInstanceOf[LT]
//  }
//  implicit object LEJsonFormat extends JsonFormat[LE] {
//    def write(obj: LE): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): LE = BooleanExprJsonFormat.read(jsValue).asInstanceOf[LE]
//  }
//  implicit object NotJsonFormat extends JsonFormat[NOT] {
//    def write(obj: NOT): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): NOT = BooleanExprJsonFormat.read(jsValue).asInstanceOf[NOT]
//  }
//  implicit object OrJsonFormat extends JsonFormat[OR] {
//    def write(obj: OR): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): OR = BooleanExprJsonFormat.read(jsValue).asInstanceOf[OR]
//  }
//  implicit object AndJsonFormat extends JsonFormat[AND] {
//    def write(obj: AND): JsValue = BooleanExprJsonFormat.write(obj: BoolExpr)
//    def read(jsValue: JsValue): AND = BooleanExprJsonFormat.read(jsValue).asInstanceOf[AND]
//  }
//
//  def parse(s: String): BoolExpr =
//    Try(s.parseJson.convertTo[BoolExpr]) match {
//      case Success(expr) => expr
//      case Failure(e)    => utils.fail(s"Parsing of the Boolean expression $s failed: ${e.getLocalizedMessage}"); null
//    }
}