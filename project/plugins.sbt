resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("com.github.gseitz" % "sbt-release"   % "1.0.6")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"       % "1.1.0")
addSbtPlugin("org.scoverage"     % "sbt-scoverage" % "1.5.1")
addSbtPlugin("org.xerial.sbt"    % "sbt-sonatype"  % "2.0")
