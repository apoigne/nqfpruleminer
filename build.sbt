import com.typesafe.sbt.packager.MappingsHelper
import sbt.Keys.mappings

lazy val nqfpgrowth =
  (project in file("."))
    .settings(
      name := "NqFPGrowth",
      description := "Subgroup Mining with (not quite) FP-growth",
      mainClass := Some("ruleminer"),
      version := "0.2",

      organization := "de.fraunhofer.iais.kd",
      organizationName := "Fraunhofer IAIS, Knowledge Discovery",
      organizationHomepage := Some(url("http://www.iais.fraunhofer.de")),
      scalaVersion := "2.12.3",
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
      mappings in Universal += {packageBin in Compile map { p => p -> "lib/nqfpgrowth.jar" }}.value,
      mappings in Universal ++= MappingsHelper.directory("resources"),
      mappings in Universal ++= MappingsHelper.contentOf("src/main/resources").map(x => (x._1,"resources/" + x._2)),
      crossPaths := false

    ).enablePlugins(JavaAppPackaging, UniversalDeployPlugin)

