```scala mdoc:invisible
import cats.effect._, skunk._, skunk.net.message.StartupMessage
implicit def dummyTrace: natchez.Trace[IO] = ???
```

# Sessions

`Session` represents a connection to a Postgres database.

## Obtaining a Session

TBD - talk about pooling, session lifecycle, cleanup, etc.

## Authentication

Skunk currently supports the `trust` (no password necessary), `password`, `md5` and `scram-sha-256` authentication methods.

See [§20.3](https://www.postgresql.org/docs/current/auth-methods.html) in the Postgres documentation for more information on authentication methods.

## Encryption with SSL

To connect with SSL (disabled by default) provide the `ssl` named argument when constructing a `Session` resource.

```scala mdoc:compile-only
Session.single[IO](
  host     = "localhost",
  user     = "jimmy",
  database = "world",
  password = Some("banana"),
  debug    = true,
  ssl      = SSL.System, // Use SSL with the system default SSLContext.
)
```

Skunk supports all TLS modes provided by fs2.

| Mode                          | Comment                                          |
|-------------------------------|--------------------------------------------------|
| `SSL.None`                    | Skunk will not request SSL. **This is the default.** |
| `SSL.Trusted`                 | Connect via SSL and trust all certificates. Use this if you're running with a self-signed certificate, for instance. |
| `SSL.System`                  | Connect via SSL and use the system default `SSLContext` to verify certificates. Use this if you're running with a CA-signed certificate. |
| `SSL.fromSSLContext(…)`       | Connect via SSL using an existing `SSLContext`. |
| `SSL.fromKeyStoreFile(…)`     | Connect via SSL using a specified keystore file. |
| `SSL.fromKeyStoreResource(…)` | Connect via SSL using a specified keystore classpath resource. |
| `SSL.fromKeyStore(…)`         | Connect via SSL using an existing Keystore. |

In addition to these options, the `SSL` values themselves allow the following modifications:

| Property                | Default                 | Comment |
|-------------------------|-------------------------|---------|
| `.withFallback(…)`      | `false`                 | If true, allow fallback to a non-encrypted connection if SSL is unavailable. |
| `.withTLSParameters(…)` | `TLSParameters.Default` | Allows for custom @scaladoc[TLSParameters](fs2.io.tls.TLSParameters).


## Session Parameters

Session parameters affect data serialization and are specified by Skunk during startup negotiation. Changing them via a `SET` command will result in undefined behavior. The following session parameters are set by default:

| Parameter | Value |
|----------|-------|
```scala mdoc:passthrough
println(Session.DefaultConnectionParameters.map { case (k, v) => s"| `$k` | `$v` |" } .mkString("\n"))
```

It is possible to modify default session parameters via the parameters session property, which is unsupported in general but may be necessary when using nonstandard Postgres variants. Amazon Redshift, for example, does not support the `IntervalStyle` parameter, and this will cause startup negotiation to fail. A workaround is demonstrated below.

```scala mdoc:compile-only
Session.single[IO](
  host       = "localhost",
  user       = "jimmy",
  database   = "world",
  password   = Some("banana"),
  port       = 5439,
  parameters = Session.DefaultConnectionParameters - "IntervalStyle"
)
```

## Error Conditions

A `Session` is ultimately a TCP Socket, and as such a number of error conditions can arise. These conditions immediately invalidate the session and raise exceptions in your effect type `F`, with the expectation that the operation will fail, or will perhaps be retried with a new session.

| Condition | Exception | Meaning |
|-----------|----|---|
| Connect&nbsp;Timeout | TBD | TBD - The connect timeout expired before a connection can be established with the Postgres server. |
| Protocol&nbsp;Timeout | TBD | TBD - The protocol timeout expired before receiving an expected response from the Postgres server. |
| Disconnection | `EofException` | The underlying socket has been closed. |

Note that if you wish to **limit statement execution time**, it's best to use the `statement_timeout` session parameter (settable via SQL or via `parameters` above), which will raise a server-side exception on expiration and will _not_ invalidate the session.


## For beginners

This section aims to help a beginner (me), get and use skunks types in the right place by skteching out a very simplistic, strongly opinionated app design. There may well be many other, and better, ways of achieving the same. We're going to assume we're writing an http4s app backed by a postgres database, and leave a "type sketch" of the program.

Our starting point once configuration is loaded, is likely to be this `val dbAccess = Resource[IO, Resource[IO, Session[IO]]]`. 

Which is a program that when run, yields a `Resource[IO, Session[IO]]`. 

That is, in turn a program that when run, will yield a `Session[IO]`. We can use a `Session[IO]` to run a [query](../tutorial/Query.md). Our question, in which part of our app, does each of these types belong?

@@@ note { title=Pro tip }
My mental model, was greatly helped by defining and using my own type alias `type MySessionPool = Resource[IO, Session[IO]]`
@@@

server.scala
```scala
case class DBConfig(???)
val dbConfig: IO[DBConfig] = ???
val dbAccess: Resource[IO, MySessionPool] = for {
        c <- Resource.eval(dbConfig)
        s <- Session.pooled[IO](???)
      } yield {
        s
      }

val routesWithDb : Resource[cats.effect.IO, HttpRoutes[IO]] = dbAccess.flatMap { sessionPool => 
   val service : AnHttpServiceImpl[IO] = AnHttpServiceImpl(sessionPool) 
   val getStuffRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { 
      case req @ GET -> "stuff" =>
        service.getStuff()
    }
    getStuffRoute
}
```
And the `routesWithDb` can be given into the server as the HttpApp. We'll now want to look at the service implementation that sits underneath our routes. We've provided our service, with `MySessionPool`, which is a program that knows how to get a session.

AnHttpServiceImpl.scala
```scala
trait AnHttpService[IO] {
  def getStuff(): IO[List[Stuff]]
}
case class AnHttpServiceImpl(pool: MySessionPool) extends AnHttpService[IO] {

  lazy val db = StuffDb.fromSessionPool(pool)

  def getStuff(): IO[List[Stuff]] = 
    db.getStuffs().map(???)

}
```

Note that all we're really doing here, is drilling the session pool, straight through the service layer, and into what i think of as the DB layer. 

db.stuff.scala
```scala
trait StuffDb {
  def getStuffs: IO[List[Stuff]]
}

object StuffDb {

  private val stuffCodec : Codec[Stuff] = (text *: int4).pimap[Stuff]

  private lazy val allStuff = {
    sql" SELECT * from stuff_table".query[Stuff](stuffcodec)
  }

  def fromSessionPool(sp: MySessionPool): StuffDb = new StuffDb {
    def getStuffs(): IO[List[Stuff]] = sp.use{ s =>
      s.prepare(allStuff).flatMap{
        _.stream(Void, 128).compile.toList
      }
    }
  }

}
```
For me personally, this pattern appears to have been a reasonably maintainable starting point.
