// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

addSbtPlugin("org.typelevel"             % "sbt-typelevel"      % "0.4.20")
addSbtPlugin("org.typelevel"             % "sbt-typelevel-site" % "0.4.20")
addSbtPlugin("com.lightbend.paradox"     % "sbt-paradox"        % "0.10.3")
addSbtPlugin("com.github.sbt"          % "sbt-site"           % "1.5.0")
addSbtPlugin("com.github.sbt"          % "sbt-ghpages"        % "0.7.0")
addSbtPlugin("com.timushev.sbt"          % "sbt-updates"        % "0.6.4")
addSbtPlugin("org.scalameta"             % "sbt-mdoc"           % "2.3.7")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"      % "2.0.7")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.13.1")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.12")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.1")
addSbtPlugin("com.armanbilge" % "sbt-scala-native-config-brew-github-actions" % "0.1.3")
