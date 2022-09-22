ThisBuild / tlBaseVersion := "0.3"

// Our Scala versions.
lazy val `scala-2.12` = "2.12.16"
lazy val `scala-2.13` = "2.13.8"
lazy val `scala-3.0`  = "3.1.3"

ThisBuild / scalaVersion       := `scala-2.13`
ThisBuild / crossScalaVersions :=
  Seq(`scala-2.12`, `scala-2.13`, `scala-3.0`)

ThisBuild / organization := "org.tpolecat"
ThisBuild / licenses     := Seq(License.MIT)
ThisBuild / developers   := List(
  Developer("tpolecat", "Rob Norris", "rob_norris@mac.com", url("http://www.tpolecat.org"))
)

ThisBuild / tlCiReleaseBranches := Seq("main") // publish snapshits on `main`
ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / tlJdkRelease := Some(8)

lazy val setupCertAndDocker = Seq(
  WorkflowStep.Run(
    commands = List("chmod 600 world/server.key", "sudo chown 999 world/server.key"),
    name = Some("Set up cert permissions"),
  ),
  WorkflowStep.Run(
    commands = List("docker-compose up -d"),
    name = Some("Start up Postgres"),
  )
)

ThisBuild / githubWorkflowBuildPreamble ++= setupCertAndDocker
ThisBuild / githubWorkflowBuild ~= { steps =>
  WorkflowStep.Sbt(
    commands = List("headerCheckAll"),
    name = Some("Check Headers"),
  ) +: steps
}
ThisBuild / githubWorkflowBuildPostamble ++= Seq(
  WorkflowStep.Sbt(
    commands = List("docs/makeSite"),
    name = Some(s"Check Doc Site (${`scala-2.13`} JVM only)"),
    cond = Some(s"matrix.scala == '${`scala-2.13`}' && matrix.project == 'rootJVM'"),
  )
)
ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    id = "coverage",
    name = s"Generate coverage report (${`scala-2.13`} JVM only)",
    scalas = List(`scala-2.13`),
    steps = List(WorkflowStep.CheckoutFull) ++
      WorkflowStep.SetupJava(githubWorkflowJavaVersions.value.toList) ++
      githubWorkflowGeneratedCacheSteps.value ++ 
      setupCertAndDocker ++
      List(
        WorkflowStep.Sbt(List("coverage", "rootJVM/test", "coverageReport")),
        WorkflowStep.Run(
          List("bash <(curl -s https://codecov.io/bash)"),
          name = Some("Upload code coverage data")
        )
      )
  )

// https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

// This is used in a couple places
lazy val fs2Version = "3.2.14"
lazy val natchezVersion = "0.1.6"

// Global Settings
lazy val commonSettings = Seq(

  // Resolvers
  resolvers ++= Resolver.sonatypeOssRepos("public"),
  resolvers ++= Resolver.sonatypeOssRepos("snapshots"),

  // Headers
  headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
  headerLicense  := Some(HeaderLicense.Custom(
    """|Copyright (c) 2018-2021 by Rob Norris
       |This software is licensed under the MIT License (MIT).
       |For more information see LICENSE or https://opensource.org/licenses/MIT
       |""".stripMargin
    )
  ),

  // Compilation
  scalacOptions -= "-language:experimental.macros", // doesn't work cross-version
  Compile / doc / scalacOptions --= Seq("-Xfatal-warnings"),
  Compile / doc / scalacOptions ++= Seq(
    "-groups",
    "-sourcepath", (LocalRootProject / baseDirectory).value.getAbsolutePath,
    "-doc-source-url", "https://github.com/tpolecat/skunk/blob/v" + version.value + "€{FILE_PATH}.scala",
  ),
  libraryDependencies ++= Seq(
    compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.2" cross CrossVersion.full),
  ).filterNot(_ => tlIsScala3.value),

  // Coverage Exclusions
  coverageExcludedPackages := "ffstest.*;tests.*;example.*;natchez.http4s.*",

  // uncomment in case of emergency
  // scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-source:3.0-migration") else Nil },

  // Add some more source directories
  Compile / unmanagedSourceDirectories ++= {
    val sourceDir = (Compile / sourceDirectory).value
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((3, _))  => Seq(sourceDir / "scala-2.13+", file(sourceDir.getPath.replaceFirst("jvm", "shared").replaceFirst("js", "shared")) / "scala-2.13+")
      case Some((2, 12)) => Seq()
      case Some((2, _))  => Seq(sourceDir / "scala-2.13+", file(sourceDir.getPath.replaceFirst("jvm", "shared").replaceFirst("js", "shared")) / "scala-2.13+")
      case _             => Seq()
    }
  },


  // dottydoc really doesn't work at all right now
  Compile / doc / sources := {
    val old = (Compile / doc / sources).value
    if (scalaVersion.value.startsWith("3."))
      Seq()
    else
      old
  },

)

lazy val skunk = tlCrossRootProject
  .settings(name := "skunk")
  .aggregate(core, tests, circe, refined, example)
  .settings(commonSettings)

lazy val core = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-core",
    description := "Tagless, non-blocking data access library for Postgres.",
    scalacOptions ~= (_.filterNot(_ == "-source:3.0-migration")),
    libraryDependencies ++= Seq(
      "org.typelevel"          %%% "cats-core"               % "2.8.0",
      "org.typelevel"          %%% "cats-effect"             % "3.3.14",
      "co.fs2"                 %%% "fs2-core"                % fs2Version,
      "co.fs2"                 %%% "fs2-io"                  % fs2Version,
      "org.scodec"             %%% "scodec-bits"             % "1.1.34",
      "org.scodec"             %%% "scodec-core"             % (if (tlIsScala3.value) "2.2.0" else "1.11.10"),
      "org.scodec"             %%% "scodec-cats"             % "1.2.0",
      "org.tpolecat"           %%% "natchez-core"            % natchezVersion,
      "org.tpolecat"           %%% "sourcepos"               % "1.0.1",
      "org.scala-lang.modules" %%% "scala-collection-compat" % "2.8.1",
    ) ++ Seq(
      "com.beachape"  %%% "enumeratum"   % "1.6.1",
    ).filterNot(_ => tlIsScala3.value)
  ).jvmSettings(
    libraryDependencies += "com.ongres.scram" % "client" % "2.1",
  ).jsSettings(
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "saslprep" % "0.1.1",
      "io.github.cquiroz" %%% "scala-java-time" % "2.4.0",
      "io.github.cquiroz" %%% "locales-minimal-en_us-db" % "1.4.1"
    ),
  )

lazy val refined = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/refined"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "eu.timepit" %%% "refined" % "0.9.29",
    )
  )

lazy val circe = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/circe"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"   % "0.14.2",
      "io.circe" %%% "circe-parser" % "0.14.2"
    )
  )

lazy val tests = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Full)
  .in(file("modules/tests"))
  .dependsOn(core, circe)
  .enablePlugins(AutomateHeaderPlugin, NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    scalacOptions  -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "org.scalameta"     %%% "munit"                   % "0.7.29",
      "org.scalameta"     % "junit-interface"           % "0.7.29",
      "org.typelevel"     %%% "scalacheck-effect-munit" % "1.0.4",
      "org.typelevel"     %%% "munit-cats-effect-3"     % "1.0.7",
      "org.typelevel"     %%% "cats-free"               % "2.8.0",
      "org.typelevel"     %%% "cats-laws"               % "2.8.0",
      "org.typelevel"     %%% "discipline-munit"        % "1.0.9",
    ) ++ Seq(
      "io.chrisdavenport" %%% "cats-time"               % "0.3.4",
    ).filterNot(_ => scalaVersion.value.startsWith("3.")),
    testFrameworks += new TestFramework("munit.Framework")
  )
  .jsSettings(
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )

lazy val example = project
  .in(file("modules/example"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin, NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat"  %%% "natchez-honeycomb"   % natchezVersion,
      "org.tpolecat"  %%% "natchez-jaeger"      % natchezVersion,
    )
    // ) ++ Seq(
    //   "org.http4s"    %%% "http4s-dsl"          % "0.21.22",
    //   "org.http4s"    %%% "http4s-blaze-server" % "0.21.22",
    //   "org.http4s"    %%% "http4s-circe"        % "0.21.22",
    //   "io.circe"      %%% "circe-generic"       % "0.13.0",
    // ).filterNot(_ => scalaVersion.value.startsWith("3."))
  )

lazy val docs = project
  .in(file("modules/docs"))
  .dependsOn(core.jvm)
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
    version            := version.value.takeWhile(_ != '-'), // strip off the -3-f22dca22+20191110-1520-SNAPSHOT business
    paradoxProperties ++= Map(
      "scala-versions"          -> (core.jvm / crossScalaVersions).value.map(CrossVersion.partialVersion).flatten.map { case (a, b) => s"$a.$b" } .mkString("/"),
      "org"                     -> organization.value,
      "scala.binary.version"    -> s"2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "core-dep"                -> s"${(core.jvm / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "circe-dep"               -> s"${(circe.jvm / name).value}_2.${CrossVersion.partialVersion(scalaVersion.value).get._2}",
      "version"                 -> version.value,
      "scaladoc.skunk.base_url" -> s"https://static.javadoc.io/org.tpolecat/skunk-core_2.12/${version.value}",
      "scaladoc.fs2.io.base_url"-> s"https://static.javadoc.io/co.fs2/fs2-io_2.12/${fs2Version}",
    ),
    mdocIn := (baseDirectory.value) / "src" / "main" / "paradox",
    Compile / paradox / sourceDirectory := mdocOut.value,
    makeSite := makeSite.dependsOn(mdoc.toTask("")).value,
    mdocExtraArguments := Seq("--no-link-hygiene"), // paradox handles this
    libraryDependencies ++= Seq(
      "org.tpolecat"  %%% "natchez-jaeger" % natchezVersion,
    )
)

// ci

