import de.fhg.iais.nqfpruleminer.{CompRule, Comparator}
import de.fhg.iais.nqfpruleminer.Numeric

val comp = CompRule(Comparator.GT, 0.0)

comp(Numeric(0.0,-1))

