```scala mdoc:invisible
import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop
import fs2.Stream
val s: Session[IO] = null
```
# Queries

This section explains how to construct and execute queries.

@@@ note { title=Definition }
A *query* is a SQL statement that can return rows.
@@@

## Single-Column Query

First let's look at a query that selects a single column and decodes rows as Scala strings.

```scala mdoc
val a: Query[Void, String] =
  sql"SELECT name FROM country".query(varchar)
```

Observe the following:

- We are using the @ref:[sql interpolator](../reference/Fragments.md) to construct a @scaladoc[Fragment](skunk.Fragment), which we then turn into a @scaladoc[Query](skunk.Query) by calling the `query` method (fragments are also used to construct @ref[Commands](Command.md)).
- The argument to `query` is a value called `varchar`, which has type `Decoder[String]` and defines the read relationship between the Postgres type `varchar` and the Scala type `String`. The relationship between Postgres types and Scala types is summarized in the reference section @ref:[Schema Types](../reference/SchemaTypes.md).
- The first type argument for our `Query` type is `Void`, which means this query has no parameters. The second type argument is `String`, which means we expect rows to be decoded as `String` values (via our `varchar` decoder).

@@@ note
Query and Command types are usually inferrable, but specifying a type ensures that the chosen encoders and decoders are consistent with the expected input and output Scala types. For this reason (and for clarity) we will always use explicit type annotations in the documentation.
@@@

The query above is a *simple query*.

@@@ note { title=Definition }
A *simple query* is a query with no parameters.
@@@

Postgres provides a [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#id-1.10.5.7.4) for execution of simple queries, returning all rows at once (Skunk returns them as a list). Such queries can be passed directly to @scaladoc[Session#execute](skunk.Session#execute).

```scala mdoc:compile-only
// assume s: Session[IO]
s.execute(a) // IO[List[String]]
```

@scaladoc[Session](skunk.Session) provides the following methods for direct execution of simple queries. See the Scaladoc for more information.

| Method    | Return Type    | Notes                                             |
|-----------|----------------|---------------------------------------------------|
| `execute` | `F[List[A]]`   | All results, as a list.                           |
| `option`  | `F[Option[A]]` | Zero or one result, otherwise an error is raised. |
| `unique`  | `F[A]`         | Exactly one result, otherwise an error is raised. |

## Multi-Column Query

Our next example selects two columns.

```scala mdoc
val b: Query[Void, String ~ Int] =
  sql"SELECT name, population FROM country".query(varchar ~ int4)
```

Observe that the argument to `query` is a pair of decoders conjoined with the `~` operator, yielding a `Decoder[String ~ Int]`. Executing this query will yield a `List[String ~ Int]`, which is an alias for `List[(String, Int)]`. See the section on @ref:[twiddle lists](../reference/TwiddleLists.md) for more information on this mechanism.

### Mapping Query Results

Decoding into a twiddle list (i.e., nested pairs) isn't ideal, so let's define a `Country` data type. We can then call `map` on our query to adapt the row type.

```scala mdoc
case class Country(name: String, population: Int)

val c: Query[Void, Country] =
  sql"SELECT name, population FROM country"
    .query(varchar ~ int4)                // (1)
    .map { case n ~ p => Country(n, p) }  // (2)
```

Observe the following:

- At ① we request that rows be decoded by `varchar ~ int4` into Scala type `String ~ Int`.
- At ② we `map` to our `Country` data type, yielding a `Query[Void, Country]`.

So that is one way to do it.

### Mapping Decoder Results

A more reusable way to do this is to define a `Decoder[Country]` based on the `varchar ~ int4` decoder. We can then decode directly into our `Country` data type.

```scala mdoc
val country: Decoder[Country] =
  (varchar ~ int4).map { case (n, p) => Country(n, p) }     // (1)

val d: Query[Void, Country] =
  sql"SELECT name, population FROM country".query(country)  // (2)
```

Observe the following:

- At ① we map the `varchar ~ int4` decoder directly to Scala type `Country`, yielding a `Decoder[Country]`.
- At ② we use our `country` decoder directly, yielding a `Query[Void, Country]`.

@@@ note { title=Tip }
Because decoders are structural (i.e., they rely only on the position of column values) it can become a maintenance issue when queries and their decoders become separated in code. Try to keep decoders close to the queries that use them.
@@@

### Mapping Decoder Results Generically

Because `Country` is a simple case class we can generate the mapping code mechanically. To do this, use `gmap` and specify the target data type.

```scala mdoc
val country2: Decoder[Country] =
  (varchar ~ int4).gmap[Country]
```

Even better, instead of constructing a named decoder you can `gmap` the `Query` itself.

```scala mdoc
val c2: Query[Void, Country] =
  sql"SELECT name, population FROM country"
    .query(varchar ~ int4)
    .gmap[Country]
```

## Parameterized Query

Now let's add a parameter to the query.

```scala mdoc
val e: Query[String, Country] =
  sql"""
    SELECT name, population
    FROM   country
    WHERE  name LIKE $varchar
  """.query(country)
```

Observe that we have interpolated a value called `varchar`, which has type `Encoder[String]`.

This means that Postgres will expect an argument of type `varchar`, which will have Scala type `String`. The relationship between Postgres types and Scala types is summarized in the reference section @ref:[Schema Types](../reference/SchemaTypes.md).

@@@ note
We have already seen `varchar` used as a row *decoder* for `String` and now we're using it as an *encoder* for `String`. We can do this because `varchar` actually has type `Codec[String]`, which extends both `Encoder[String]` and `Decoder[String]`. All type mappings provided by Skunk are codecs and can be used in both positions.
@@@

The query above is an *extended query*.

@@@ note { title=Definition }
An *extended query* is a query with parameters, or a simple query that is executed via the extended query protocol.
@@@

Postgres provides a [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#PROTOCOL-FLOW-EXT-QUERY) for executing extended queries which is more involved than simple query protocol. It provides for prepared statements that can be reused with different sets of arguments, and provides cursors which allow results to be paged and streamed.

Here we use the extended query protocol to stream directly to the console using constant space.

```scala mdoc:compile-only
// assume s: Session[IO]
s.prepare(e).use { ps =>
  ps.stream("U%", 64)
    .evalMap(c => IO(println(c)))
    .compile
    .drain
} // IO[Unit]
```

Observe that `prepare` returns a `Resource` that prepares the statement before use and then frees it on completion. Here we use @scaladoc[PreparedQuery#stream](skunk.PreparedQuery#stream) to pass our parameter `"U%"` and then create an [fs2](http://fs2.io) stream that fetches rows in blocks of 64 and prints them to the console.

Note that when using `Resource` and `Stream` together it is often convenient to express the entire program in terms of `Stream`.

```scala mdoc:compile-only
// assume s: Session[IO]
val stream: Stream[IO, Unit] =
  for {
    ps <- Stream.resource(s.prepare(e))
    c  <- ps.stream("U%", 64)
    _  <- Stream.eval(IO(println(c)))
  } yield ()

stream.compile.drain // IO[Unit]
```

This program does the same thing, but perhaps in a more convenient style.

@scaladoc[PreparedQuery[A, B]](skunk.PreparedQuery) provides the following methods for execution. See the Scaladoc for more information.

| Method    | Return Type                | Notes                                             |
|-----------|----------------------------|---------------------------------------------------|
| `stream`  | `Stream[F,B]`              | All results, as a stream.                         |
| `option`  | `F[Option[B]]`             | Zero or one result, otherwise an error is raised. |
| `unique`  | `F[B]`                     | Exactly one result, otherwise an error is raised. |
| `cursor`  | `Resource[F,Cursor[F,B]]`  | A cursor that returns pages of results.           |
| `pipe`    | `Pipe[F, A, B]`            | A pipe that executes the query for each input value, concatenating the results. |

## Multi-Parameter Query

Multiple parameters work analogously to multiple columns.

```scala mdoc
val f: Query[String ~ Int, Country] =
  sql"""
    SELECT name, population
    FROM   country
    WHERE  name LIKE $varchar
    AND    population < $int4
  """.query(country)
```

Observe that we have two parameter encoders `varchar` and `int4` (in that order), whose corresponding Scala input type is `String ~ Int`. See the section on @ref:[twiddle lists](../reference/TwiddleLists.md) for more information.

```scala mdoc:compile-only
// assume s: Session[IO]
s.prepare(f).use { ps =>
  ps.stream("U%" ~ 2000000, 64)
    .evalMap(c => IO(println(c)))
    .compile
    .drain
} // IO[Unit]
```

And we pass the value `"U%" ~ 2000000` as our statement argument.

## Summary of Query Types

The *simple query protocol* (i.e., `Session#execute`) is slightly more efficient in terms of message exchange, so use it if:

- Your query has no parameters; and

- you are querying for a small number of rows; and

- you will be using the query only once per session.


The *extend query protocol* (i.e., `Session#prepare`) is more powerful and more general, but requires additional network exchanges. Use it if:

- Your query has parameters; and/or

- you are querying for a large or unknown number of rows; and/or

- you intend to stream the results; and/or

- you will be using the query more than once per session.

## Full Example

Here is a complete program listing that demonstrates our knowledge thus far.

```scala mdoc:reset
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import java.time.OffsetDateTime
import natchez.Trace.Implicits.noop

object QueryExample extends IOApp {

  // a source of sessions
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )

  // a data model
  case class Country(name: String, code: String, population: Int)

  // a simple query
  val simple: Query[Void, OffsetDateTime] =
    sql"select current_timestamp".query(timestamptz)

  // an extended query
  val extended: Query[String, Country] =
    sql"""
      SELECT name, code, population
      FROM   country
      WHERE  name like $text
    """.query(varchar ~ bpchar(3) ~ int4)
       .gmap[Country]

  // run our simple query
  def doSimple(s: Session[IO]): IO[Unit] =
    for {
      ts <- s.unique(simple) // we expect exactly one row
      _  <- IO(println(s"timestamp is $ts"))
    } yield ()

  // run our extended query
  def doExtended(s: Session[IO]): IO[Unit] =
    s.prepare(extended).use { ps =>
      ps.stream("U%", 32)
        .evalMap(c => IO(println(c)))
        .compile
        .drain
    }

  // our entry point
  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        _ <- doSimple(s)
        _ <- doExtended(s)
      } yield ExitCode.Success
    }

}
```

Running this program yields the following.

```scala mdoc:passthrough
println("```")
QueryExample.main(Array.empty)
println("```")
```

## Service-Oriented Example

In real life a program like `QueryExample` above will grow complicated an hard to maintain because the database abstractions are out in the open. It's better to define an interface that *uses* a database session and write your program in terms of that interface. Here is a rewritten version of the program above that demonstrates this pattern.

```scala mdoc:reset
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import java.time.OffsetDateTime
import natchez.Trace.Implicits.noop
import fs2.Stream
import cats.Applicative

// a data model
case class Country(name: String, code: String, population: Int)

// A service interface.
trait Service[F[_]] {
  def currentTimestamp: F[OffsetDateTime]
  def countriesByName(pat: String): Stream[F, Country]
}

// A companion with a constructor.
object Service {

  private val timestamp: Query[Void, OffsetDateTime] =
    sql"select current_timestamp".query(timestamptz)

  private val countries: Query[String, Country] =
    sql"""
      SELECT name, code, population
      FROM   country
      WHERE  name like $text
    """.query(varchar ~ bpchar(3) ~ int4)
       .gmap[Country]

  def fromSession[F[_]: Applicative](s: Session[F]): Resource[F, Service[F]] =
    s.prepare(countries).map { pq =>

      // Our service implementation. Note that we are preparing the query on construction, so
      // our service can run it many times without paying the planning cost again.
      new Service[F] {
        def currentTimestamp: F[OffsetDateTime] = s.unique(timestamp)
        def countriesByName(pat: String): Stream[F,Country] = pq.stream(pat, 32)
      }

    }
}


object QueryExample2 extends IOApp {

  // a source of sessions
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )

  // A source of services
  val service: Resource[IO, Service[IO]] =
    session.flatMap(Service.fromSession(_))

  // our entry point ... there is no indication that we're using a database at all!
  def run(args: List[String]): IO[ExitCode] =
    service.use { s =>
      for {
        ts <- s.currentTimestamp
        _  <- IO(println(s"timestamp is $ts"))
        _  <- s.countriesByName("U%")
               .evalMap(c => IO(println(c)))
               .compile
               .drain
      } yield ExitCode.Success
    }

}
```

Running this program yields the same output as above.

```scala mdoc:passthrough
println("```")
QueryExample2.main(Array.empty)
println("```")
```

## Experiment

Here are some experiments you might want to try.

- Try to run the `extended` query via `Session#execute`, or the `simple` query via `Session#prepare`. Note that in the latter case you will need to pass the value `Void` as an argument.

- Add/remove/change encoders and decoders. Do various things to make the queries fail. Which kinds of errors are detected at compile-time vs. runtime?

- Add more fields to `Country` and more colums to the query; or add more parameters. You will need to consult the @ref:[Schema Types](../reference/SchemaTypes.md) reference to find the encoders/decoders you need.

- Experiment with the treatment of nullable columns. You need to add `.opt` to encoders/decoders (`int4.opt` for example) to indicate nullability. Keep in mind that for interpolated encoders you'll need to write `${int4.opt}`.

For reference, the `country` table looks like this.

|     Column     |   Postgres Type   | Modifiers |
|----------------|-------------------|-----------
| code           | character(3)      | not null  |
| name           | character varying | not null  |
| continent      | character varying | not null  |
| region         | character varying | not null  |
| surfacearea    | real              | not null  |
| indepyear      | smallint          |           |
| population     | integer           | not null  |
| lifeexpectancy | real              |           |
| gnp            | numeric(10,2)     |           |
| gnpold         | numeric(10,2)     |           |
| localname      | character varying | not null  |
| governmentform | character varying | not null  |
| headofstate    | character varying |           |
| capital        | integer           |           |
| code2          | character(2)      | not null  |

