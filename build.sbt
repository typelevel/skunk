
// This is used in a couple places
lazy val fs2Version = "2.4.2"

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
  scalaVersion       := "2.13.3",
  crossScalaVersions := Seq("2.12.11", scalaVersion.value),
  scalacOptions -= "-language:experimental.macros", // doesn't work cross-version
  Compile / doc     / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc     / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (baseDirectory in LocalRootProject).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/skunk/blob/v" + version.value + "€{FILE_PATH}.scala",
  ),
  addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full),

  // Coverage Exclusions
  coverageExcludedPackages := "ffstest.*;tests.*;example.*;natchez.http4s.*",

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
    resolvers   +=  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core"    % "2.1.1",
      "org.typelevel" %% "cats-effect"  % "2.1.3",
      "co.fs2"        %% "fs2-core"     % fs2Version,
      "co.fs2"        %% "fs2-io"       % fs2Version,
      "org.scodec"    %% "scodec-core"  % "1.11.7",
      "org.scodec"    %% "scodec-cats"  % "1.0.0",
      "com.beachape"  %% "enumeratum"   % "1.6.1",
      "org.tpolecat"  %% "natchez-core" % "0.0.11",
    )
  )

lazy val refined = project
  .in(file("modules/refined"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    libraryDependencies += "eu.timepit" %% "refined" % "0.9.14",
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
    )
  )

lazy val tests = project
  .in(file("modules/tests"))
  .dependsOn(core, circe)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    publish / skip := true,
    test / parallelExecution := false, // why? fix this!
    libraryDependencies ++= Seq(
      "org.scala-sbt"      % "test-interface" % "1.0",
      "io.chrisdavenport" %% "cats-time"      % "0.3.0"
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
      "org.tpolecat"  %% "natchez-honeycomb"   % "0.0.11",
      "org.tpolecat"  %% "natchez-jaeger"      % "0.0.11",
      "org.http4s"    %% "http4s-dsl"          % "0.21.6",
      "org.http4s"    %% "http4s-blaze-server" % "0.21.6",
      "org.http4s"    %% "http4s-circe"        % "0.21.6",
      "io.circe"      %% "circe-generic"       % "0.13.0",
    )
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
    paradoxProperties in Compile ++= Map(
      "scala-versions"          -> (crossScalaVersions in core).value.map(CrossVersion.partialVersion).flatten.map(_._2).mkString("2.", "/", ""),
      "org"                     -> organization.value,
      "scala.binary.version"    -> s"2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "core-dep"                -> s"${(core / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "circe-dep"               -> s"${(circe / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "version"                 -> version.value,
      "scaladoc.skunk.base_url" -> s"https://static.javadoc.io/org.tpolecat/skunk-core_2.12/${version.value}",
      "scaladoc.fs2.io.base_url"-> s"https://static.javadoc.io/co.fs2/fs2-io_2.12/${fs2Version}",
      "github.base_url"         -> s"https://github.com/tpolecat/skunk/tree/v${version.value}"
    ),
    mdocIn := (baseDirectory.value) / "src" / "main" / "paradox",
    makeSite := makeSite.dependsOn(mdoc.toTask("")).value,
    mdocExtraArguments := Seq("--no-link-hygiene"), // paradox handles this
)
