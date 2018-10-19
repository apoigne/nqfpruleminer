import com.typesafe.sbt.packager.MappingsHelper.directory
import sbt.Keys.mappings

lazy val ruleminer =
  (project in file("."))
    .settings(
      name := "ruleminer",
      description := "Subgroup Mining with (not quite) FP-growth",
      mainClass := Some("ruleminer"),
      version := "0.4",

      organization := "de.fraunhofer.iais.kd",
      organizationName := "Fraunhofer IAIS, Knowledge Discovery",
      organizationHomepage := Some(url("http://www.iais.fraunhofer.de")),
      scalaVersion := "2.12.5",
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
      mappings in Universal in packageBin += file("configuration.pdf") -> "configuration.pdf",
      mappings in Universal in packageBin += file("example/configuration.conf") -> "example/configuration.conf",
      mappings in Universal in packageBin += file("example/data.csv") -> "example/data.csv",
      crossPaths := false

    ).enablePlugins(JavaAppPackaging, UniversalDeployPlugin)

