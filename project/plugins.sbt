// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.typelevel"      % "sbt-typelevel"                               % "0.8.4")
addSbtPlugin("org.typelevel"      % "sbt-typelevel-site"                          % "0.8.4")
addSbtPlugin("com.timushev.sbt"   % "sbt-updates"                                 % "0.6.4")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                                     % "0.4.7")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"                               % "2.4.4")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                                 % "1.20.2")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"                            % "0.4.17")
addSbtPlugin("com.armanbilge"     % "sbt-scala-native-config-brew-github-actions" % "0.4.0")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1") // TODO: Remove once this is merged and released: https://github.com/typelevel/sbt-typelevel/pull/862
