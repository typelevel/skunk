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

# Commands

This section explains how to construct and execute commands.

@@@ note { title=Definition }
A *command* is a SQL statement that does not return rows.
@@@

## Simple Command

First let's look at a command that sets the session's random number seed.

```scala mdoc
val a: Command[Void] =
  sql"SET SEED TO 0.123".command
```

Observe the following:

- We are using the @ref:[sql interpolator](../reference/Fragments.md) to construct a @scaladoc[Fragment](skunk.Fragment), which we then turn into a @scaladoc[Command](skunk.Command) by calling the `command` method.
- `Command` is parameterized by its input type. Because this command has no parameters the input type is `Void`.

The command above is a *simple command*.

@@@ note { title=Definition }
A *simple command* is a command with no parameters.
@@@

The same [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#id-1.10.5.7.4) that executes simple queries also executes simple commands. Such commands can be passed directly to @scaladoc[Session.execute](skunk.Session#execute).


```scala mdoc:compile-only
// assume s: Session[IO]
s.execute(a) // IO[Completion]
```

On success a command will yield a @scaladoc[Completion](skunk.data.Completion), which is an ADT that encodes responses from various commands. In this case our completion is simply the value `Completion.Set`.

## Parameterized Command

Now let's add a parameter to the command.

```scala mdoc
val c: Command[String] =
  sql"DELETE FROM country WHERE name = $varchar".command
```

Observe that we have interpolated a value called `varchar`, which has type `Encoder[String]`. This works the same way as with queries. See the previous chapter for more information about statement parameters.

The command above is an *extended command*.

@@@ note { title=Definition }
An *extended command* is a command with parameters, or a simple command that is executed via the extended query protocol.
@@@

The same protocol Postgres provides for executing extended queries is also used for extended commands, but because the return value is always a single `Completion` the end-user API is more limited.

Here we use the extended protocol to attempt some deletions.

```scala mdoc:compile-only
// assume s: Session[IO]
s.prepare(c).use { pc =>
  pc.execute("xyzzy") *>
  pc.execute("fnord") *>
  pc.execute("blech")
} // IO[Completion]
```

If we're slighly more clever we can do this with `traverse` and return a list of `Completion`.

```scala mdoc:compile-only
// assume s: Session[IO]
s.prepare(c).use { pc =>
  List("xyzzy", "fnord", "blech").traverse(s => pc.execute(s))
} // IO[List[Completion]]
```

And if we're yet more clever we can turn `pc` into an fs2 `Pipe`.

```scala mdoc:compile-only
// assume s: Session[IO]
Stream.resource(s.prepare(c)).flatMap { pc =>
  Stream("xyzzy", "fnord", "blech").through(pc.pipe)
} // Stream[IO, Completion]
```

### Contramapping Commands

Similar to `map`ping the _output_ of a Query, we can `contramap` the _input_ to a command or query. Here we provide a function that turns an `Info` into a `String ~ String`, yielding a `Command[Info]`.

```scala mdoc
case class Info(code: String, hos: String)

val update2: Command[Info] =
  sql"""
    UPDATE country
    SET    headofstate = $varchar
    WHERE  code = ${bpchar(3)}
  """.command                                          // Command[String ~ String]
     .contramap { case Info(code, hos) => code ~ hos } // Command[Info]
```

However in this case the mapping is entirely mechanical. Similar to `gmap` on query results, we can skip the boilerplate and `gcontramap` directly to an isomosphic case class.

```scala mdoc
val update3: Command[Info] =
  sql"""
    UPDATE country
    SET    headofstate = $varchar
    WHERE  code = ${bpchar(3)}
  """.command          // Command[String ~ String]
     .gcontramap[Info] // Command[Info]
```

## List Parameters

Sometimes we want to repeat a parameter, for instance if we're using an `IN` clause. Here is a command that takes a `List[String]` as an argument and turns it into a list of `varchar`. We must specify the length when constructing the statement.

```scala mdoc
def deleteMany(n: Int): Command[List[String]] =
  sql"DELETE FROM country WHERE name IN (${varchar.list(n)})".command

val delete3 = deleteMany(3) // takes a list of size 3
```

Sometimes we want to repeat a *group* of parameters, for instance if we're doing a bulk `INSERT`. To do this we take advantage of two combinators, first `.values` which takes an encoder and returns a new encoder that wraps its generated SQL in parens, and then `.list` as above.

```scala mdoc
def insertMany(n: Int): Command[List[(String, Short)]] = {
  val enc = (varchar ~ int2).values.list(n)
  sql"INSERT INTO pets VALUES $enc".command
}

val insert3 = insertMany(3)
```

You have no doubt noticed that there is a lack of safety with list parameters because the required length of the list is not represented in the type. In practice this is usually unavoidable because the length of the list is typically not known statically. However it is *also* typically the case that the command will be prepared with a specific list in mind, and in this case we can improve safety by passing the list itself (i.e., not just its length) to `.list`, and we get back an encoder that only works with that specific list.

```scala mdoc
def insertExactly(ps: List[(String, Short)]): Command[ps.type] = {
  val enc = (varchar ~ int2).values.list(ps)
  sql"INSERT INTO pets VALUES $enc".command
}

val pairs = List[(String, Short)](("Bob", 3), ("Alice", 6))

// Note the type!
val insertPairs = insertExactly(pairs)
```

We can pass `pairs` to `execute`.

```scala mdoc:compile-only
// assume s: Session[IO]
s.prepare(insertPairs).use { pc => pc.execute(pairs) }
```

However attempting to pass anything *other than* `pairs` is a type error.

```scala mdoc:fail
// assume s: Session[IO]
s.prepare(insertPairs).use { pc => pc.execute(pairs.drop(1)) }
```

See the full example below for a demonstration of these techniques.

## Summary of Command Types

The *simple command protocol* (i.e., `Session#execute`) is slightly more efficient in terms of message exchange, so use it if:

- Your command has no parameters; and
- you will be using the query only once per session.

The *extend command protocol* (i.e., `Session#prepare`) is more powerful and more general, but requires additional network exchanges. Use it if:

- Your command has parameters; and/or
- you will be using the command more than once per session.

## Full Example

Here is a complete program listing that demonstrates our knowledge thus far, using the service pattern introduced earlier.

```scala mdoc:reset
import cats.effect._
import cats.implicits._
import natchez.Trace.Implicits.noop
import skunk._
import skunk.codec.all._
import skunk.implicits._

// a data type
case class Pet(name: String, age: Short)

// a service interface
trait PetService[F[_]] {
  def insert(pet: Pet): F[Unit]
  def insert(ps: List[Pet]): F[Unit]
  def selectAll: F[List[Pet]]
}

// a companion with a constructor
object PetService {

  // command to insert a pet
  private val insertOne: Command[Pet] =
    sql"INSERT INTO pets VALUES ($varchar, $int2)"
      .command
      .gcontramap[Pet]

  // command to insert a specific list of pets
  private def insertMany(ps: List[Pet]): Command[ps.type] = {
    val enc = (varchar ~ int2).gcontramap[Pet].values.list(ps)
    sql"INSERT INTO pets VALUES $enc".command
  }

  // query to select all pets
  private val all: Query[Void, Pet] =
    sql"SELECT name, age FROM pets"
      .query(varchar ~ int2)
      .gmap[Pet]

  // construct a PetService
  def fromSession[F[_]](s: Session[F])(
    implicit ev: MonadCancel[F, Throwable]
  ): PetService[F] =
    new PetService[F] {
      def insert(pet: Pet): F[Unit] = s.prepare(insertOne).use(_.execute(pet)).void
      def insert(ps: List[Pet]): F[Unit] = s.prepare(insertMany(ps)).use(_.execute(ps)).void
      def selectAll: F[List[Pet]] = s.execute(all)
    }

}

object CommandExample extends IOApp {

  // a source of sessions
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  // a resource that creates and drops a temporary table
  def withPetsTable(s: Session[IO]): Resource[IO, Unit] = {
    val alloc = s.execute(sql"CREATE TEMP TABLE pets (name varchar, age int2)".command).void
    val free  = s.execute(sql"DROP TABLE pets".command).void
    Resource.make(alloc)(_ => free)
  }

  // some sample data
  val bob     = Pet("Bob", 12)
  val beagles = List(Pet("John", 2), Pet("George", 3), Pet("Paul", 6), Pet("Ringo", 3))

  // our entry point
  def run(args: List[String]): IO[ExitCode] =
    session.flatTap(withPetsTable).map(PetService.fromSession(_)).use { s =>
      for {
        _  <- s.insert(bob)
        _  <- s.insert(beagles)
        ps <- s.selectAll
        _  <- ps.traverse(p => IO.println(p))
      } yield ExitCode.Success
    }

}
```

Running this program yields the following.

```scala mdoc:passthrough
println("```")
import skunk.mdoc._
CommandExample.run(Nil).unsafeRunSyncWithRedirect()
println("```")
```

## Experiment

- Change `insertMany` to pass an `Int` to `.list` and then pass a size other than the length of `beagles` and observe the error.
- Add a unique constraint on `name` in the DDL and then violate it by inserting two pets with the same name. Follow the hint in the error message to add an handler that recovers gracefully.
- Change the service constructor to prepare the statements once on construction, rather than each time `insert` is called.