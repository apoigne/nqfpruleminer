package apps

import better.files._
import common.utils.fail
import org.backuity.clist._

object datapreparation extends CliMain[Unit](
  name = "datapreparation",
  description =
    """Generates the k most interesting subgroups using the "Not Quite FPGrowth algorithm.
      | Supported inputs are .csv files and.arff file.
      | Assumptions:
      |-.csv files: first line defines the attributes
      |-.arff files: data are in csv format """.stripMargin
) {

  var configFile: String = arg[String](description = "Configuration file")

  def run : Unit= {
    fail(configFile.toFile.exists(), s"Configuration file ${configFile.toFile.path} does not exist")
  }

}
