val specs2Version  = "3.9.5"
val twitterVersion = "7.1.0"

val commonSettings = Seq(
  organization := "io.getclump",
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  libraryDependencies ++= Seq(
    "org.specs2"  %% "specs2-core"  % specs2Version  % "test",
    "org.specs2"  %% "specs2-junit" % specs2Version  % "test",
    "org.specs2"  %% "specs2-mock"  % specs2Version  % "test",
  ),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:reflectiveCalls"
  ),
  fork in Test := true, // avoid mockito+crossbuild strangeness
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  coverageMinimum := 100,
  coverageFailOnMinimum := false,
  sonatypeProfileName := "io.getclump",
  pomExtra :=
    <url>http://github.com/getclump/clump</url>
          <licenses>
            <license>
              <name>Apache</name>
              <url>https://raw.githubusercontent.com/getclump/clump/master/LICENSE.txt</url>
              <distribution>repo</distribution>
            </license>
          </licenses>
          <scm>
            <url>git@github.com:getclump/clump.git</url>
            <connection>scm:git:git@github.com:getclump/clump.git</connection>
          </scm>
          <developers>
            <developer>
              <id>fwbrasil</id>
              <name>Flavio W. Brasil</name>
              <url>http://github.com/fwbrasil/</url>
            </developer>
            <developer>
              <id>williamboxhall</id>
              <name>William Boxhall</name>
              <url>http://github.com/williamboxhall/</url>
            </developer>
            <developer>
              <id>stevenheidel</id>
              <name>Steven Heidel</name>
              <url>http://github.com/stevenheidel/</url>
            </developer>
          </developers>
)

lazy val `clump-twitter` = (project in file("build/twitter"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies += "com.twitter" %% "util-core" % twitterVersion,
    sourceDirectory := (baseDirectory in ThisBuild).value / "clump-core" / "src",
    sources in Compile :=
      ((baseDirectory in ThisBuild).value / "clump-twitter" / "src" / "main" / "scala" / "io" / "getclump" / "package.scala")
        +: (sources in Compile).value.filter(
        _ != (baseDirectory in ThisBuild).value / "clump-core" / "src" / "main" / "scala" / "io" / "getclump" / "package.scala")
  )

lazy val `clump-scala` = (project in file("build/scala"))
  .settings(commonSettings: _*)
  .settings(sourceDirectory := (baseDirectory in ThisBuild).value / "clump-core" / "src")
