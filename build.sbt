ThisBuild / tlBaseVersion := "1.0"

// Our Scala versions.
lazy val `scala-2.13` = "2.13.10"
lazy val `scala-3`  = "3.2.2"

ThisBuild / scalaVersion       := `scala-2.13`
ThisBuild / crossScalaVersions := Seq(`scala-2.13`, `scala-3`)

ThisBuild / organization := "org.tpolecat"
ThisBuild / licenses     := Seq(License.MIT)
ThisBuild / developers   := List(
  Developer("tpolecat", "Rob Norris", "rob_norris@mac.com", url("http://www.tpolecat.org"))
)

ThisBuild / tlCiReleaseBranches += "series/0.6.x"
ThisBuild / tlSitePublishBranch := Some("series/0.6.x")
ThisBuild / tlSonatypeUseLegacyHost := false
ThisBuild / githubWorkflowOSes := Seq("ubuntu-latest")
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))
ThisBuild / tlJdkRelease := Some(8)

ThisBuild / githubWorkflowBuildPreamble ++= nativeBrewInstallWorkflowSteps.value
ThisBuild / nativeBrewInstallCond := Some("matrix.project == 'rootNative'")

lazy val setupCertAndDocker = Seq(
  WorkflowStep.Run(
    commands = List("export SERVER_KEY=$(cat world/server.key)", "export SERVER_CERT=$(cat world/server.crt)", "docker-compose up -d"),
    name = Some("Start up Postgres"),
  )
)

ThisBuild / githubWorkflowJobSetup ++= setupCertAndDocker
ThisBuild / tlCiHeaderCheck := true

ThisBuild / githubWorkflowAddedJobs +=
  WorkflowJob(
    id = "coverage",
    name = s"Generate coverage report (${`scala-2.13`} JVM only)",
    scalas = List(`scala-2.13`),
    steps = githubWorkflowJobSetup.value.toList ++
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

import com.typesafe.tools.mima.core._
ThisBuild / mimaBinaryIssueFilters ++= List(
  ProblemFilters.exclude[DirectMissingMethodProblem]("skunk.net.BitVectorSocket.fromSocket")
)

// This is used in a couple places
lazy val fs2Version = "3.7.0"
lazy val openTelemetryVersion = "1.28.0"
lazy val otel4sVersion = "0.2.1"

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

  // Coverage Exclusions
  coverageExcludedPackages := "ffstest.*;tests.*;example.*",

  // uncomment in case of emergency
  // scalacOptions ++= { if (scalaVersion.value.startsWith("3.")) Seq("-source:3.0-migration") else Nil },
)

lazy val skunk = tlCrossRootProject
  .settings(name := "skunk")
  .aggregate(core, tests, circe, refined, example, unidocs)
  .settings(commonSettings)

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("modules/core"))
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-core",
    description := "Tagless, non-blocking data access library for Postgres.",
    scalacOptions ~= (_.filterNot(_ == "-source:3.0-migration")),
    libraryDependencies ++= Seq(
      "org.typelevel"          %%% "cats-core"               % "2.9.0",
      "org.typelevel"          %%% "cats-effect"             % "3.5.0",
      "co.fs2"                 %%% "fs2-core"                % fs2Version,
      "co.fs2"                 %%% "fs2-io"                  % fs2Version,
      "org.scodec"             %%% "scodec-bits"             % "1.1.37",
      "org.scodec"             %%% "scodec-core"             % (if (tlIsScala3.value) "2.2.1" else "1.11.10"),
      "org.scodec"             %%% "scodec-cats"             % "1.2.0",
      "org.typelevel"          %%% "otel4s-core-trace"       % otel4sVersion,
      "org.tpolecat"           %%% "sourcepos"               % "1.1.0",
      "org.typelevel"          %%% "twiddles-core"           % "0.6.0",
    ) ++ Seq(
      "com.beachape"  %%% "enumeratum"   % "1.7.2",
    ).filterNot(_ => tlIsScala3.value)
  ).jvmSettings(
    libraryDependencies += "com.ongres.scram" % "client" % "2.1",
  ).platformsSettings(JSPlatform, NativePlatform)(
    libraryDependencies ++= Seq(
      "com.armanbilge" %%% "saslprep" % "0.1.1",
      "io.github.cquiroz" %%% "scala-java-time" % "2.5.0",
      "io.github.cquiroz" %%% "locales-minimal-en_us-db" % "1.5.1"
    ),
  )

lazy val refined = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/refined"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-refined",
    libraryDependencies ++= Seq(
      "eu.timepit" %%% "refined" % "0.10.3",
    )
  )

lazy val circe = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/circe"))
  .dependsOn(core)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(commonSettings)
  .settings(
    name := "skunk-circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core"   % "0.14.5",
      "io.circe" %%% "circe-parser" % "0.14.5"
    )
  )

lazy val tests = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("modules/tests"))
  .dependsOn(core, circe)
  .enablePlugins(AutomateHeaderPlugin, NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    scalacOptions  -= "-Xfatal-warnings",
    libraryDependencies ++= Seq(
      "org.scalameta"     %%% "munit"                   % "1.0.0-M7",
      "org.scalameta"     % "junit-interface"           % "1.0.0-M7",
      "org.typelevel"     %%% "scalacheck-effect-munit" % "2.0.0-M2",
      "org.typelevel"     %%% "munit-cats-effect"       % "2.0.0-M3",
      "org.typelevel"     %%% "cats-free"               % "2.9.0",
      "org.typelevel"     %%% "cats-laws"               % "2.9.0",
      "org.typelevel"     %%% "discipline-munit"        % "2.0.0-M3",
      "org.typelevel"     %%% "cats-time"               % "0.5.1",
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    testOptions += {
      if(System.getProperty("os.arch").startsWith("aarch64")) {
        Tests.Argument(TestFrameworks.MUnit, "--exclude-tags=X86ArchOnly")
      } else Tests.Argument()
    }
  )
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withESFeatures(_.withESVersion(org.scalajs.linker.interface.ESVersion.ES2018)) },
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule)),
  )
  .nativeEnablePlugins(ScalaNativeBrewedConfigPlugin)
  .nativeSettings(
    libraryDependencies += "com.armanbilge" %%% "epollcat" % "0.1.4",
    Test / nativeBrewFormulas ++= Set("s2n", "utf8proc"),
    Test / envVars ++= Map("S2N_DONT_MLOCK" -> "1")
  )

lazy val example = project
  .in(file("modules/example"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin, NoPublishPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel"    %% "otel4s-java" % otel4sVersion,
      "io.opentelemetry" % "opentelemetry-exporter-otlp" % openTelemetryVersion % Runtime,
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % openTelemetryVersion % Runtime,
    ),
    run / fork := true,
    javaOptions += "-Dotel.java.global-autoconfigure.enabled=true"
    // ) ++ Seq(
    //   "org.http4s"    %%% "http4s-dsl"          % "0.21.22",
    //   "org.http4s"    %%% "http4s-blaze-server" % "0.21.22",
    //   "org.http4s"    %%% "http4s-circe"        % "0.21.22",
    //   "io.circe"      %%% "circe-generic"       % "0.13.0",
    // ).filterNot(_ => scalaVersion.value.startsWith("3."))
  )

lazy val unidocs = project
  .in(file("unidocs"))
  .enablePlugins(TypelevelUnidocPlugin)
  .settings(
    name := "skunk-docs",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(core.jvm, refined.jvm, circe.jvm)
  )
  .settings(commonSettings)

lazy val docs = project
  .in(file("modules/docs"))
  .dependsOn(core.jvm)
  .enablePlugins(AutomateHeaderPlugin)
  .enablePlugins(TypelevelSitePlugin)
  .settings(commonSettings)
  .settings(
    mdocIn := (Compile / sourceDirectory).value / "laika",
    laikaConfig := {
      import laika.rewrite.link._

      laikaConfig.value.withRawContent
        .withConfigValue("version", mdocVariables.value("VERSION"))
        .withConfigValue(
          LinkConfig(apiLinks =
            List(
              ApiLinks(
                baseUri = s"https://www.javadoc.io/doc/org.tpolecat/skunk-docs_${scalaBinaryVersion.value}/${mdocVariables.value("VERSION")}/",
                packagePrefix = "skunk"
              ),
              ApiLinks(
                baseUri = s"https://www.javadoc.io/doc/co.fs2/fs2-docs_${scalaBinaryVersion.value}/$fs2Version/",
                packagePrefix = "fs2"
              ),
            )
          )
        )
    }
  )

// ci

