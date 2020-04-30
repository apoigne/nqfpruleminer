import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerBaseImage
import com.typesafe.sbt.packager.universal.UniversalDeployPlugin
import sbt.Keys.{mappings, organization, _}
import sbt._

lazy val commonSettings = Seq(
  version := "0.1",
  organization := "de.fraunhofer.iais.kd",
  organizationName := "Fraunhofer IAIS, Knowledge Discovery",
  organizationHomepage := Some(url("http://www.iais.fraunhofer.de")),
  scalaVersion := "2.13.1",
  maintainer := "axel.poigne@iais.fraunhofer.de",
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
  )
)

lazy val ruleminer =
  (project in file("."))
    .enablePlugins(JavaAppPackaging, UniversalDeployPlugin, DockerPlugin, WindowsPlugin)
    .settings(
      commonSettings,
      name := "ruleminer",
      description := "Subgroup Mining with (not quite) FP-growth",
      mainClass := Some("ruleminer"),
      version := "0.6",

      mappings in Universal += {packageBin in Compile map { p => p -> "lib/ruleminer.jar" }}.value,
      mappings in Universal ++= directory("docs"),
      mappings in Universal ++= directory("examples"),
      mappings in Universal += file("Readme.md") -> "Readme.md",
      crossPaths := false,

      dockerBaseImage := "openjdk:latest",
      dockerUpdateLatest := true
)