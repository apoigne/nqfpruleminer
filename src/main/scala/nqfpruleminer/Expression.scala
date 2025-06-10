package nqfpruleminer

import nqfpruleminer.Item.Position
import nqfpruleminer.utils.fail

object Expression {
  import fastparse.*
  import MultiLineWhitespace.*

  trait Literal {
    def attributes: Set[String]
    def eval(implicit env: Vector[Value]): Value
    def updatePosition(implicit posMap: Map[String, Position]): Literal
  }

  case class Id(name: String, position: Position = -1) extends Literal {
    def attributes: Set[String] = Set(name)
    def eval(implicit env: Vector[Value]): Value = env(position)
    def updatePosition(implicit posMap: Map[String, Position]): Literal = Id(name, posMap.getOrElse(name, -1))
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
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval.compare(arg2.eval) == 0
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = EQ(arg1.updatePosition, arg2.updatePosition)
  }
  case class NE(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval.compare(arg2.eval) != 0
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = NE(arg1.updatePosition, arg2.updatePosition)
  }
  case class GT(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval.compare(arg2.eval) > 0
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = GT(arg1.updatePosition, arg2.updatePosition)
  }
  case class GE(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval.compare(arg2.eval) >= 0
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = GE(arg1.updatePosition, arg2.updatePosition)
  }
  case class LT(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval.compare(arg2.eval) < 0
    def updatePosition(implicit posMap: Map[String, Position]): BoolExpr = LT(arg1.updatePosition, arg2.updatePosition)
  }
  case class LE(arg1: Literal, arg2: Literal) extends BoolExpr {
    def attributes: Set[String] = arg1.attributes ++ arg2.attributes
    def eval(implicit env: Vector[Value]): Boolean = arg1.eval.compare(arg2.eval) <= 0
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

//  private val White = NoWhitespace.Wrapper {
//    NoTrace(" ".rep)
//  }

  private def space[$: P] = P(CharsWhileIn(" \r\n").?)
  private def digits[$: P] = P(CharsWhileIn("0-9"))
  private def exponent[$: P] = P(CharIn("eE") ~ CharIn("+\\-").? ~ digits)
  private def fractional[$: P] = P("." ~ digits)
  private def integral[$: P] = P("0" | CharIn("1-9") ~ digits.?)
//  private def number[$: P] = P(CharIn("+-").? ~ integral ~ fractional.? ~ exponent.? ~ !CharIn("a to z")).!.map(x => Val(Numeric(x.toDouble)))

  private def number[$: P]: P[Expression.Val] = P(CharIn("+\\-").? ~ integral ~ fractional.? ~ exponent.?).!.map(x => Val(Numeric(x.toDouble)))
  private def id[$: P]: P[Literal] = P(CharIn("a-zA-Z") ~ CharIn("a-zA-Z1-9.", "_").rep.?).!.map(Id(_))

  private def strChars[$: P] = P(CharsWhile(c => !"\"\\".contains(c)))
  def string[$: P]: P[Literal] = P(space ~ "\"" ~/ strChars.rep.! ~ "\"").map(s => Val(Nominal(s)))

  private def literal[$: P]: P[Literal] = id | number | string

  private def lessThen[$: P](left: Literal): P[BoolExpr] = P(literal).map(LT(left, _))
  private def lessEqual[$: P](left: Literal): P[BoolExpr] = P("=" ~ literal).map(LE(left, _))
  private def less[$: P](left: Literal): P[BoolExpr] = P("<" ~ (!"=" ~ lessThen(left) | lessEqual(left)))
  private def greaterThen[$: P](left: Literal): P[BoolExpr] = P(literal).map(GT(left, _))
  private def greaterEqual[$: P](left: Literal): P[BoolExpr] = P("=" ~ literal).map(GE(left, _))
  private def greater[$: P](left: Literal): P[BoolExpr] = P(">" ~ (!"=" ~ greaterThen(left) | greaterEqual(left)))
  private def notEqual[$: P](left: Literal): P[BoolExpr] = P("==" ~ literal).map(EQ(left, _))
  private def equal[$: P](left: Literal): P[BoolExpr] = P("!=" ~ literal).map(NE(left, _))
  private def comparator[$: P](left: Literal): P[BoolExpr] = space ~ (equal(left) | notEqual(left) | less(left) | greater(left))
  private def atom[$: P]: P[BoolExpr] = P(literal).flatMap(lit => comparator(lit))

  private def `true`[$: P]: P[BoolExpr] = P("'true").map(_ => TRUE)
  private def `false`[$: P]: P[BoolExpr] = P("'true").map(_ => FALSE)
  private def parens[$: P]: P[BoolExpr] = P("(" ~/ or ~ ")")
  private def factor[$: P]: P[BoolExpr] = P(`true` | `false` | atom | parens)
  private def and[$: P]: P[BoolExpr] = P(factor ~ ("&&" ~/ factor).rep).map { case (x, l) => if (l.isEmpty) x else AND(x +: l) }
  private def or[$: P]: P[BoolExpr] = P(and ~ ("||" ~/ and).rep).map { case (x, l) => if (l.isEmpty) x else OR(x +: l) }
  private def expr[$: P]: P[BoolExpr] = P(or)

  def parseLiteral(s: String): Literal =
    parse(s, literal(using _)) match {
      case Parsed.Success(v, _) => v
      case Parsed.Failure(expected, failIndex, _) =>
        fail(s"Failed to apply the rule '$expected' at index $failIndex of string $s.")
        null
    }

  def parseExpression(s: String, property: String): BoolExpr =
    parse(s, expr(using _)) match {
      case Parsed.Success(v, _) => v
      case Parsed.Failure(expected, failIndex, _) =>
        fail(s"\nReference: $property\n\nParser failed to apply the rule '$expected' at index $failIndex of $s.")
        null
    }
}