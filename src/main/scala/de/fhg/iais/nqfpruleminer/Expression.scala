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

  def json2boolExpr(config: Config)(implicit ctx: Context): BoolExpr = {

    def json2boolExpr(config: Config): BoolExpr =
      config getString "op" match {
        case "eq" => EQ(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "ne" => NE(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "gt" => GT(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "ge" => GE(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "lt" => LT(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "le" => LE(json2literal(config getConfig "arg1"), json2literal(config getConfig "arg2"))
        case "and" => AND(json2boolExpr(config getConfig "arg1"), json2boolExpr(config getConfig "arg2"))
        case "or" => OR(json2boolExpr(config getConfig "arg1"), json2boolExpr(config getConfig "arg2"))
        case "not" => NOT(json2boolExpr(config getConfig "arg"))
        case "false" => FALSE
        case "true" => TRUE
        case op => fail(s"Operator $op not supported."); null
      }

    def json2literal(config: Config): Literal =
      config getString "op" match {
        case "attribute" =>
          val name = config getString "arg"
          if (name == "self") Attr(name) else Attr(name, ctx.attributeToPosition(name))
        case "integer" => Val(Numeric(config getDouble "arg"))
        case "double" => Val(Numeric(config getDouble "arg"))
        case "date" => Val(Value(config getString "arg"))
        case typ => fail(s"Typ $typ not supported."); null
      }

    Try(json2boolExpr(config)) match {
      case Success(bexp) => bexp
      case Failure(e) => utils.fail(s"Illformed condition\n${config.toString}: ${e.getMessage}"); null
    }

  }
}