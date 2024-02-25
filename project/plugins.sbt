// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.typelevel"      % "sbt-typelevel"                               % "0.6.6")
addSbtPlugin("org.typelevel"      % "sbt-typelevel-site"                          % "0.6.6")
addSbtPlugin("com.timushev.sbt"   % "sbt-updates"                                 % "0.6.4")
addSbtPlugin("pl.project13.scala" % "sbt-jmh"                                     % "0.4.7")
addSbtPlugin("org.scoverage"      % "sbt-scoverage"                               % "2.0.11")
addSbtPlugin("org.scala-js"       % "sbt-scalajs"                                 % "1.15.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"                            % "0.4.17")
addSbtPlugin("com.armanbilge"     % "sbt-scala-native-config-brew-github-actions" % "0.2.0-RC1")
