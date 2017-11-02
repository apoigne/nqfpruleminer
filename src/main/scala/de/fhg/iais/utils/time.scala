package  de.fhg.iais.utils

object time {
  private var theTime = System.currentTimeMillis()

  def apply(text: String) : String = {
    def newTime = System.currentTimeMillis()
    val res = s"${(newTime - theTime).toDouble / 1000} $text"
    theTime = newTime
    res
  }
}

