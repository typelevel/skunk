```scala mdoc:invisible
import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop
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

### Contramapping Commands

Similar to `map`ping the _output_ of a Query, we can `contramap` the _input_ to a command or query. Here we provide a function that turns an `Info` into a `String ~ String`, yielding a `Command[Info]`.

```scala mdoc
case class Info(code: String, hos: String)

def update2: Command[Info] =
  sql"""
    UPDATE country
    SET    headofstate = $varchar
    WHERE  code = ${bpchar(3)}
  """.command                                          // Command[String ~ String]
     .contramap { case Info(code, hos) => code ~ hos } // Command[Info]
```

However in this case the mapping is entirely mechanical. Similar to `gmap` on query results, we can skip the boilerplate and `gcontramap` directly to an isomosphic case class.

```scala mdoc
def update3: Command[Info] =
  sql"""
    UPDATE country
    SET    headofstate = $varchar
    WHERE  code = ${bpchar(3)}
  """.command          // Command[String ~ String]
     .gcontramap[Info] // Command[Info]
```

## Encoding Values

(show how to do `(foo ~ bar).values`)

## Summary of Command Types

The *simple command protocol* (i.e., `Session#execute`) is slightly more efficient in terms of message exchange, so use it if:

- Your command has no parameters; and
- you will be using the query only once per session.

The *extend command protocol* (i.e., `Session#prepare`) is more powerful and more general, but requires additional network exchanges. Use it if:

- Your command has parameters; and/or
- you will be using the command more than once per session.

## Full Example

## Experiment

