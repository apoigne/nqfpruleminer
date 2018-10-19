package de.fhg.iais.nqfpruleminer

import de.fhg.iais.nqfpruleminer.Item.Position
import de.fhg.iais.utils.fail
import fastparse.WhitespaceApi

object Expression {
  import fastparse.noApi._

  trait Literal {
    def attributes: Set[String]
    def eval(implicit env: Vector[Value]): Value
    def updatePosition(implicit posMap: Map[String, Position]): Literal
  }

  case class Id(name: String, position: Position = -1) extends Literal {
    def attributes: Set[String] = Set(name)
    def eval(implicit env: Vector[Value]): Value = env(position)
    def updatePosition(implicit posMap: Map[String, Position]): Literal = Id(name, posMap(name))
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

  case class OR(args: Seq[BoolExpr]) extends BoolExpr {
    def attributes: Set[String] = args.map(_.attributes).reduce(_ ++ _)
    def eval(implicit env: Vector[Value]): Boolean = args.map(_.eval).reduce(_ || _)
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = OR(args.map(_.updatePosition))
  }

  case class AND(args: Seq[BoolExpr]) extends BoolExpr {
    def attributes: Set[String] = args.map(_.attributes).reduce(_ ++ _)
    def eval(implicit env: Vector[Value]): Boolean = args.map(_.eval).reduce(_ && _)
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = AND(args.map(_.updatePosition))
  }

  private val White = WhitespaceApi.Wrapper {
    import fastparse.all._
    NoTrace(" ".rep)
  }

  import White._

  private val space = P(CharsWhileIn(" \r\n").?)
  private val digits = P(CharsWhileIn("0123456789"))
  private val exponent = P(CharIn("eE") ~ CharIn("+-").? ~ digits)
  private val fractional = P("." ~ digits)
  private val integral = P("0" | CharIn('1' to '9') ~ digits.?)
  private val number = P(CharIn("+-").? ~ integral ~ fractional.? ~ exponent.? ~ !CharIn('a' to 'z')).!.map(x => Val(Numeric(x.toDouble)))

  private val id: P[Literal] = P(CharIn('a' to 'z', 'A' to 'Z') ~ CharIn('a' to 'z', 'A' to 'Z', '0' to '9', "_").rep.?).!.map(Id(_))

  private val strChars = P( ElemsWhile(c => !"\"\\".contains(c)) )
  val string: P[Literal] = P(space ~ "\"" ~/ strChars.rep.! ~ "\"").map(s => Val(Nominal(s)))

  private val literal: P[Literal] = id | number | string

  private def lessThen(left: Literal): P[BoolExpr] = P(literal).map(LT(left, _))
  private def lessEqual(left: Literal): P[BoolExpr] = P("=" ~ literal).map(LE(left, _))
  private def less(left: Literal): P[BoolExpr] = P("<" ~ (!"=" ~ lessThen(left) | lessEqual(left)))
  private def greaterThen(left: Literal): P[BoolExpr] = P(literal).map(GT(left, _))
  private def greaterEqual(left: Literal): P[BoolExpr] = P("=" ~ literal).map(GE(left, _))
  private def greater(left: Literal): P[BoolExpr] = P(">" ~ (!"=" ~ greaterThen(left) | greaterEqual(left)))
  private def notEqual(left: Literal): P[BoolExpr] = P("==" ~ literal).map(EQ(left, _))
  private def equal(left: Literal): P[BoolExpr] = P("!=" ~ literal).map(NE(left, _))
  private def comparator(left: Literal): P[BoolExpr] = space ~ (equal(left) | notEqual(left) | less(left) | greater(left))
  private val atom: P[BoolExpr] = P(literal).flatMap(comparator)

  private val `true`: P[BoolExpr] = P("'true").map(_ => TRUE)
  private val `false`: P[BoolExpr] = P("'true").map(_ => FALSE)
  private val parens: P[BoolExpr] = P("(" ~/ or ~ ")")
  private val factor: P[BoolExpr] = P(`true` | `false` | atom | parens)
  private val and: P[BoolExpr] = P(factor ~ ("&&" ~/ factor).rep).map { case (x, l) => if (l.isEmpty) x else AND(x +: l) }
  private lazy val or: P[BoolExpr] = P(and ~ ("||" ~/ and).rep).map { case (x, l) => if (l.isEmpty) x else OR(x +: l) }
  private val expr: P[BoolExpr] = P(or)

  def parseLiteral(s: String): Expression.Literal =
    literal.parse(s) match {
      case Parsed.Success(v, _) => v
      case Parsed.Failure(expected, failIndex, _) =>
        fail(s"Failed to apply the rule '$expected' at index $failIndex of string $s.")
        null
    }

  def parse(s: String, property: String): Expression.BoolExpr =
    expr.parse(s) match {
      case Parsed.Success(v, _) => v
      case Parsed.Failure(expected, failIndex, _) =>
        fail(s"\nReference: $property\n\nParser failed to apply the rule '$expected' at index $failIndex of $s.")
        null
    }

}