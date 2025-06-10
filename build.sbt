import com.typesafe.sbt.SbtNativePackager.autoImport.NativePackagerHelper.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.dockerBaseImage
import sbt.*
import sbt.Keys.{mappings, *}

lazy val commonSettings = Seq(
  version := "1.0",
  scalaVersion := "3.6.4",
  maintainer := "axel.poigne@icloud.com",
  libraryDependencies ++= Dependencies.allDependencies,
  scalacOptions := Seq(
    "-deprecation",
    "-unchecked",
    "-Xlint","-rewrite"
  ),
  javacOptions := Seq(
    "-Xlint:unchecked",
    "-Xlint:deprecation"
  )
)

lazy val ruleminer =
  (project in file("."))
    .enablePlugins(JavaAppPackaging, UniversalPlugin, DockerPlugin, WindowsPlugin)
    .settings(
      commonSettings,
      name := "nqfpruleminer",
      description := "Subgroup Mining with (not quite) FP-growth",
      version := "1.0",

      Universal / mappings += (Compile / packageBin).value -> "lib/nqfpruleminer.jar",
      Universal / mappings ++= directory("docs"),
      Universal / mappings ++= directory("examples"),
      Universal / mappings += file("Readme.md") -> "Readme.md",
      crossPaths := false,

      dockerBaseImage := "openjdk:latest",
      dockerUpdateLatest := true
)