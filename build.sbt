

// Our Scala versions.
lazy val `scala-3.0`  = "3.0.0-M1"
// lazy val `scala-2.12` = "2.12.12"
lazy val `scala-2.13` = "2.13.3"

// This is used in a couple places
lazy val fs2Version = "2.5.0-M1"

// Global Settings
lazy val commonSettings = Seq(

  // Resolvers
  resolvers += Resolver.sonatypeRepo("public"),
  resolvers += Resolver.sonatypeRepo("snapshots"),

  // Publishing
  organization := "org.tpolecat",
  licenses    ++= Seq(("MIT", url("http://opensource.org/licenses/MIT"))),
  homepage     := Some(url("https://github.com/tpolecat/skunk")),
  developers   := List(
    Developer("tpolecat", "Rob Norris", "rob_norris@mac.com", url("http://www.tpolecat.org"))
  ),

  // Headers
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := Some(HeaderLicense.Custom(
    """|Copyright (c) 2018-2020 by Rob Norris
       |This software is licensed under the MIT License (MIT).
       |For more information see LICENSE or https://opensource.org/licenses/MIT
       |""".stripMargin
    )
  ),

  // Compilation
  scalaVersion       := `scala-2.13`,
  crossScalaVersions := Seq(/*`scala-2.12`, */ `scala-2.13`, `scala-3.0`),
  scalacOptions -= "-language:experimental.macros", // doesn't work cross-version
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/skunk/blob/v" + version.value + "€{FILE_PATH}.scala",
  ),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),
  ).filterNot(_ => isDotty.value),
  scalacOptions ++= {
    if (isDotty.value) Seq(
      "-Ykind-projector",
      "-language:implicitConversions",
    ) else Seq()
  },

  // Coverage Exclusions
  coverageExcludedPackages := "ffstest.*;tests.*;example.*;natchez.http4s.*",

  // uncomment in case of emergency
  // scalacOptions ++= { if (isDotty.value) Seq("-source:3.0-migration") else Nil },

  // Add some more source directories
  unmanagedSourceDirectories in Compile ++= {
    val sourceDir = (sourceDirectory in Compile).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _))  => Seq(sourceDir / "scala-3")
      case Some((2, _))  => Seq(sourceDir / "scala-2")
      case _             => Seq()
    }
  },

  // Also for test
  unmanagedSourceDirectories in Test ++= {
    val sourceDir = (sourceDirectory in Test).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _))  => Seq(sourceDir / "scala-3")
      case Some((2, _))  => Seq(sourceDir / "scala-2")
      case _             => Seq()
    }
  },

)

lazy val skunk = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(publish / skip := true)
  .dependsOn(macros, core, tests, circe, refined, example)
  .aggregate(macros, core, tests, circe, refined, example)

lazy val macros = project
  .in(file("modules/macros"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    ).filterNot(_ => isDotty.value)
  )

lazy val core = project
  .in(file("modules/core"))
  .dependsOn(macros)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-core",
    description := "Tagless, non-blocking data access library for Postgres.",
    resolvers   +=  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "org.typelevel"    %% "cats-core"    % "2.3.0-M2",
      "org.typelevel"    %% "cats-effect"  % "2.3.0-M1",
      "co.fs2"           %% "fs2-core"     % fs2Version,
      "co.fs2"           %% "fs2-io"       % fs2Version,
      "org.scodec"       %% "scodec-core"  % (if (isDotty.value) "2.0.0-M1" else "1.11.7"),
      "org.scodec"       %% "scodec-cats"  % "1.1.0-M2",
      "org.tpolecat"     %% "natchez-core" % "0.0.14-M2",
      "com.ongres.scram"  % "client"       % "2.1",
    ) ++ Seq(
      "com.beachape"  %% "enumeratum"   % "1.6.1",
    ).map(_.withDottyCompat(scalaVersion.value))
  )

lazy val refined = project
  .in(file("modules/refined"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit" %% "refined" % "0.9.17",
    ).map(_.withDottyCompat(scalaVersion.value))
  )

lazy val circe = project
  .in(file("modules/circe"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-circe",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"   % "0.13.0",
      "io.circe" %% "circe-parser" % "0.13.0"
    ).filterNot(_ => isDotty.value)
  )

lazy val tests = project
  .in(file("modules/tests"))
  .dependsOn(core, circe)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.typelevel"     %% "scalacheck-effect-munit" % "0.5.0",
      "org.typelevel"     %% "munit-cats-effect-2"     % "0.9.0",
      "org.typelevel"     %% "cats-free"               % "2.3.0-M2",
    ) ++ Seq(
      "io.chrisdavenport" %% "cats-time"               % "0.3.4",
    ).filterNot(_ => isDotty.value),
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val example = project
  .in(file("modules/example"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.tpolecat"  %% "natchez-honeycomb"   % "0.0.12",
      "org.tpolecat"  %% "natchez-jaeger"      % "0.0.12",
      "org.http4s"    %% "http4s-dsl"          % "0.21.8",
      "org.http4s"    %% "http4s-blaze-server" % "0.21.8",
      "org.http4s"    %% "http4s-circe"        % "0.21.8",
      "io.circe"      %% "circe-generic"       % "0.13.0",
    ).map(_.withDottyCompat(scalaVersion.value))
  )

lazy val docs = project
  .in(file("modules/docs"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(GhpagesPlugin)
  .enablePlugins(MdocPlugin)
  .settings(commonSettings)
  .settings(
    scalacOptions      := Nil,
    git.remoteRepo     := "git@github.com:tpolecat/skunk.git",
    ghpagesNoJekyll    := true,
    publish / skip     := true,
    paradoxTheme       := Some(builtinParadoxTheme("generic")),
    version            := version.value.takeWhile(_ != '+'), // strip off the +3-f22dca22+20191110-1520-SNAPSHOT business
    paradoxProperties ++= Map(
      "scala-versions"          -> (crossScalaVersions in core).value.map(CrossVersion.partialVersion).flatten.map(_._2).mkString("2.", "/", ""),
      "org"                     -> organization.value,
      "scala.binary.version"    -> s"2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "core-dep"                -> s"${(core / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "circe-dep"               -> s"${(circe / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "version"                 -> version.value,
      "scaladoc.skunk.base_url" -> s"https://static.javadoc.io/org.tpolecat/skunk-core_2.12/${version.value}",
      "scaladoc.fs2.io.base_url"-> s"https://static.javadoc.io/co.fs2/fs2-io_2.12/${fs2Version}",
    ),
    mdocIn := (baseDirectory.value) / "src" / "main" / "paradox",
    Compile / paradox / sourceDirectory := mdocOut.value,
    makeSite := makeSite.dependsOn(mdoc.toTask("")).value,
    mdocExtraArguments := Seq("--no-link-hygiene"), // paradox handles this
    libraryDependencies ++= Seq(
      "org.tpolecat"  %% "natchez-jaeger" % "0.0.12",
    ).map(_.withDottyCompat(scalaVersion.value)),
)
