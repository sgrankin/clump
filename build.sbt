val commonSettings = Seq(
  organization := "io.getclump",
  scalaVersion := "2.11.11",
  crossScalaVersions := Seq("2.11.11", "2.12.3"),
  libraryDependencies ++= Seq(
    "org.specs2"  %% "specs2"      % "2.4.2" % "test",
    "org.mockito" % "mockito-core" % "1.9.5" % "test"
  ),
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:reflectiveCalls"
  )
) ++ releaseSettings ++ Seq(
  ReleaseKeys.crossBuild := true,
  ReleaseKeys.publishArtifactsAction := PgpKeys.publishSigned.value,
  publishMavenStyle := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  ScoverageSbtPlugin.ScoverageKeys.coverageMinimum := 100,
  ScoverageSbtPlugin.ScoverageKeys.coverageFailOnMinimum := false,
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

lazy val `clump-core` = (project in file("clump-core"))
  .settings(commonSettings: _*)

lazy val `clump-twitter` = (project in file("clump-twitter"))
  .dependsOn(`clump-core`)
  .settings(commonSettings: _*)
  .settings(libraryDependencies += "com.twitter" %% "util-core" % "7.1.0")
