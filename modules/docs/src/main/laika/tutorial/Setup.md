# Setup

Welcome to the Wonderful World of Skunk! This section will help you get everything set up.

## Database Setup

In order to run the tutorial examples you will need a Postgres server with the `world` database loaded up, writable by the `jimmy` user, who must be able to log in with password `banana`. Our [Docker](http://docker.com) image does exactly that.

```
docker run -p5432:5432 -d tpolecat/skunk-world
```

If you wish to use your own Postgres server you can download `world/world.sql` from the Skunk repository and load it up yourself.

## Scala Setup

Create a new project with Skunk as a dependency.

```scala
libraryDependencies += "org.tpolecat" %% "skunk-core" % "@VERSION@"
```

## IDE Setup

For metals, no additional steps are needed.

For IntelliJ, add the [skunk-intellij plugin](https://github.com/trobert/skunk-intellij) to have correct code highlighting support for statements.

## Verify Your Setup

Try out this minimal [IOApp](https://typelevel.org/cats-effect/datatypes/ioapp.html) that connects to the database and selects the current date.

```scala mdoc
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.metrics.Meter

object Hello extends IOApp {

  implicit val tracer: Tracer[IO] = Tracer.noop              // (1)
  implicit val meter: Meter[IO] = Meter.noop

  val session: Resource[IO, Session[IO]] =
    Session.Builder[IO]                                      // (2)
      .withHost("localhost")
      .withPort(5432)
      .withUserAndPassword("jimmy", "banana")
      .withDatabase("world")
      .single

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>                                       // (3)
      for {
        d <- s.unique(sql"select current_date".query(date))  // (4)
        _ <- IO.println(s"The current date is $d.")
      } yield ExitCode.Success
    }

}
```

Let's examine the code above.

- At ① we define the no-op `Tracer` and `Meter`, which allows us to run Skunk programs with execution tracing and metrics disabled. We will revisit [Telemetry](Telemetry.md) in a later section.
- At ② we define a [Resource](https://typelevel.org/cats-effect/datatypes/resource.html) that yields un-pooled [Session](../reference/Sessions.md) values and ensures that they are closed after use. We specify the host, port, user, database, and password (note: we didn't actually need to specify the host and port as the default host is localhost and the default port is 5432 -- see [Session](../reference/Sessions.md) for information on ther connection options).
- At ③ we `use` the resource, specifying a block to execute during the `Session`'s lifetime. No matter how the block terminates (success, failure, cancellation) the `Session` will be closed properly.
- At ④ we use the [sql interpolator](../reference/Fragments.md) to construct a `Query` that selects a single column of schema type `date` (yielding `d`, a value of type `java.time.LocalDate`), then we ask the session to execute it, expecting a *unique* value back; i.e., exactly one row.

When we run the program we see the current date.

```scala mdoc:passthrough
println("```")
import skunk.mdocext._
Hello.run(Nil).unsafeRunSyncWithRedirect()
println("```")
```

## Experiment

Here are some modifications that will cause runtime failures. Give them a try and see how Skunk responds.

- Try running with an invalid user, password, or database name.
- Introduce a typo into the SQL string.
- Change the decoder from `date` to another type like `timestamp`.

We will see more examples later in the tutorial.

