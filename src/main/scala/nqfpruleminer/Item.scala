package nqfpruleminer

case class Item(feature: String, value: String) {
  override def toString = s"$feature == $value"
}
