
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
    """|Copyright (c) 2018 by Rob Norris
       |This software is licensed under the MIT License (MIT).
       |For more information see LICENSE or https://opensource.org/licenses/MIT
       |""".stripMargin
    )
  ),

  // Compilation
  scalaVersion       := "2.13.1",
  crossScalaVersions := Seq("2.12.10", scalaVersion.value),
  scalacOptions -= "-language:experimental.macros", // doesn't work cross-version
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/skunk/blob/v" + version.value + "€{FILE_PATH}.scala",
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),

)

lazy val skunk = project
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(publish / skip := true)
  .dependsOn(macros, core, tests, refined, example)
  .aggregate(macros, core, tests, refined, example)

lazy val macros = project
  .in(file("modules/macros"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-macros",
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-reflect" % scalaVersion.value
    )
  )

lazy val core = project
  .in(file("modules/core"))
  .dependsOn(macros)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-core",
    description := "Tagless, non-blocking data access library for Postgres.",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"    % "2.0.0",
      "org.typelevel" %% "cats-effect"  % "2.0.0",
      "co.fs2"        %% "fs2-core"     % "2.0.1",
      "co.fs2"        %% "fs2-io"       % "2.0.1",
      "org.scodec"    %% "scodec-core"  % "1.11.4",
      "org.scodec"    %% "scodec-cats"  % "1.0.0",
      "com.beachape"  %% "enumeratum"   % "1.5.13",
      "org.tpolecat"  %% "natchez-core" % "0.0.10",
    )
  )

lazy val refined = project
  .in(file("modules/refined"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies += "eu.timepit" %% "refined" % "0.9.10",
  )

lazy val tests = project
  .in(file("modules/tests"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.scala-sbt"      % "test-interface" % "1.0",
      "io.chrisdavenport" %% "cats-time"      % "0.2.0"
    ),
    testFrameworks += new TestFramework("ffstest.FFramework")
  )

lazy val example = project
  .in(file("modules/example"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.tpolecat"  %% "natchez-honeycomb" % "0.0.10",
      "org.tpolecat"  %% "natchez-jaeger"    % "0.0.10",
    )
  )

lazy val docs = project
  .in(file("modules/docs"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(GhpagesPlugin)
  .settings(commonSettings)
  .settings(
    git.remoteRepo     := "git@github.com:tpolecat/skunk.git",
    ghpagesNoJekyll    := true,
    publish / skip     := true,
    paradoxTheme       := Some(builtinParadoxTheme("generic")),
    paradoxProperties ++= Map(
      "org"                     -> organization.value,
      "core-name"               -> (core / name).value,
      "version"                 -> version.value,
      "scaladoc.skunk.base_url" -> s"https://static.javadoc.io/org.tpolecat/skunk-core_2.12/${version.value}"
    )
  )
