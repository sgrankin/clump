resolvers += Classpaths.sbtPluginReleases
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("org.scoverage"           % "sbt-scoverage"     % "1.0.4")
addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.5.0")
addSbtPlugin("com.github.gseitz"       % "sbt-release"       % "0.8.5")
addSbtPlugin("org.xerial.sbt"          % "sbt-sonatype"      % "0.5.1")
addSbtPlugin("com.jsuereth"            % "sbt-pgp"           % "1.0.0")
