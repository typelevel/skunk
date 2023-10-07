// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.typelevel"             % "sbt-typelevel"      % "0.5.4")
addSbtPlugin("org.typelevel"             % "sbt-typelevel-site" % "0.5.4")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"        % "0.6.4")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"           % "2.3.7")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"      % "2.0.9")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.14.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.15")
addSbtPlugin("com.armanbilge" % "sbt-scala-native-config-brew-github-actions" % "0.2.0-RC1")
