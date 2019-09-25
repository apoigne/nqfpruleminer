import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import sbt.Keys.{mappings, organization, _}
import sbt._

lazy val ruleminer =
  (project in file("."))
    .settings(
      name := "ruleminer",
      description := "Subgroup Mining with (not quite) FP-growth",
      mainClass := Some("ruleminer"),
      version := "0.5",

      organization := "de.fraunhofer.iais.kd",
      organizationName := "Fraunhofer IAIS, Knowledge Discovery",
      organizationHomepage := Some(url("http://www.iais.fraunhofer.de")),
      scalaVersion := "2.12.8",
      resolvers ++= Dependencies.allResolvers,
      libraryDependencies ++= Dependencies.allDependencies,
      scalacOptions := Seq(
        "-deprecation",
        "-unchecked",
        "-language:_",
        "-Xlint"
      ),
      javacOptions := Seq(
        "-Xlint:unchecked",
        "-Xlint:deprecation"
      ),
      mappings in Universal += {packageBin in Compile map { p => p -> "lib/ruleminer.jar" }}.value,
      mappings in Universal in packageBin += file("Readme.md") -> "Readme.md",
      mappings in Universal in packageBin ++= directory("docs"),
      mappings in Universal in packageBin += file("examples/connect4/configuration.conf") -> "examples/connect4/configuration.conf",
      mappings in Universal in packageBin += file("examples/connect4/data.csv") -> "examples/connect4/data.csv",
      mappings in Universal in packageBin += file("examples/Kobi/flashing/configuration.conf") -> "examples/Kobi/flashing/configuration.conf",
      mappings in Universal in packageBin += file("examples/Kobi/flashing/data.csv") -> "examples/Kobi/flashing/data.csv",
      mappings in Universal in packageBin += file("examples/Kobi/trial/configuration.conf") -> "examples/Kobi/trial/configuration.conf",
      mappings in Universal in packageBin += file("examples/Kobi/trial/data.csv") -> "examples/Kobi/trial/data.csv",
      crossPaths := false

    ).enablePlugins(JavaAppPackaging, UniversalDeployPlugin)