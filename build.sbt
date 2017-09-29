import com.typesafe.sbt.packager.MappingsHelper
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import sbt.Keys.mappings

lazy val nqfpgrowth =
  (project in file("."))
    .settings(
      name := "NqFPGrowth",
      description := "Subgroup Mining with (not quite) FP-growth",
      mainClass := Some("ruleminer"),
      version := "0.1",

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
      mappings in Universal += {packageBin in Compile map { p => p -> "lib/nquitefpgrowth.jar" }}.value,
      mappings in Universal ++= MappingsHelper.directory("resources"),
      mappings in Universal ++= MappingsHelper.contentOf("src/main/resources").toMap.mapValues("resources/" + _),
      crossPaths := false
    ).enablePlugins(JavaAppPackaging, UniversalDeployPlugin)

