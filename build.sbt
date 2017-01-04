import com.typesafe.sbt.SbtGit.git
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.{Versions, _}

enablePlugins(JavaAppPackaging)

name := "bunny"

scalaVersion := "2.12.1"

organization := "com.al333z"

licenses := Seq("Apache 2.0" -> url("http://www.opensource.org/licenses/Apache-2.0"))

crossScalaVersions := Seq("2.11.8", "2.12.1")

scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings", "-target:jvm-1.8")

libraryDependencies += "com.rabbitmq" % "amqp-client" % "4.0.0"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test"
libraryDependencies += "com.typesafe" % "config" % "1.3.1" % "test"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ ⇒ false }

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

pomExtra :=
    <scm>
      <url>git@github.com:AL333Z/bunny.git</url>
      <connection>scm:git:git@github.com:AL333Z/bunny.git</connection>
    </scm>
    <developers>
      <developer>
        <id>al333z</id>
        <name>Alessandro Zoffoli</name>
        <url>https://twitter.com/al333z</url>
      </developer>
    </developers>

val VersionRegex = "v([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

def setReleaseVersionCustom(): ReleaseStep = {
  def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)
    st.log.info("Setting version to '%s'." format selected)
    val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)
    val versionStr = (if (useGlobal) globalVersionString else versionString) format selected

    reapply(Seq(
      if (useGlobal) version in ThisBuild := selected
      else version := selected
    ), st)
  }

  setVersionOnly(_._1)
}

git.useGitDescribe := true
git.baseVersion := "0.0.0"
git.gitTagToVersionNumber := {
  case VersionRegex(v, "") => Some(v)
  case VersionRegex(v, s) => Some(s"$v-$s")
  case _ => None
}

releaseVersion <<= releaseVersionBump(bumper => {
  ver =>
    Version(ver)
      .map(_.withoutQualifier)
      .map(_.bump(bumper).string).getOrElse(versionFormatError)
})

releaseProcess := Seq(
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersionCustom(),
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _)),
  ReleaseStep(action = Command.process("sonatypeRelease", _)),
  pushChanges
)
