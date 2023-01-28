```scala mdoc:invisible
import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop
val s: Session[IO] = null
val ch: Channel[IO, String, String] = null
```
# Channels

Skunk provides high-level support for Postgres channels, exposing them as an fs2 `Pipe` / `Stream` pair.

See [`NOTIFY`](https://www.postgresql.org/docs/10/sql-notify.html) and [`LISTEN`](https://www.postgresql.org/docs/10/sql-listen.html) in the PostgreSQL documentation for an explanation of channel operations. The text that follows assumes you have a working knowledge of the information in those sections.

## Constructing a Channel

Use the `channel` method on `Session` to construct a channel.

```scala mdoc:compile-only
// assume s: Session[IO]
val ch = s.channel(id"my_channel") // Channel[IO, String, String]
```

Observe the following:

- The argument to `channel` is an `Identifier`. See @ref:[Identifiers](../reference/Identifiers.md) for more information.
- `ch` is a `Channel` which consumes `String`s and emits `Notification[String]`s. A notification is a structure that includes the process ID and channel identifier as well as the payload.
- `Channel` is a profunctor and thus can be contramapped to change the input type, and mapped to change the output type.

## Listening to a Channel

To listen on a channel, construct a stream via `.listen`.

```scala mdoc:compile-only
// assume ch: Channel[IO, String, String]
val nbs = ch.listen(1024) // Stream[IO, Notification[String]]
```

Observe the following:

- The argument to `listen` is the maximum number of messages that will be enqueued before blocking the underlying socket. If you run the resulting stream be sure to process notifications in a timely manner to avoid blocking concurrent session operations.
- When `nbs` begins execution it will first issue `LISTEN <channel>`.
- While `nbs` is executing it will emit any messages received from `<channel>`.
- When `nbs` terminates it will issue `UNLISTEN <channel>`.

It is perfectly fine to run such a stream concurrently while the underlying session is being used for other things (modulo transaction and fiber lifetime concerns; see @ref:[Transactions](Transactions.md) and @ref:[Concurrency.md](../reference/Concurrency.md) for more information).

If you wish to listen to all notifications on all subscribed channels, use the `notifications` method on `Session`.

## Notifying a Channel

Use `.notify` to send a message to a channel.

```scala mdoc:compile-only
// assume ch: Channel[IO, String, String]
ch.notify("hello") // IO[Unit]
```

Every `Channel` is also an fs2 `Pipe` that consumes messages.

```scala mdoc:compile-only
// assume s: Session[IO]
// select all the country names and stream them to the country_names channel.
s.prepare(sql"select name from country".query(varchar)).flatMap { ps =>
  ps.stream(Void, 512)
    .through(s.channel(id"country_names"))
    .compile
    .drain
}
```

Keep in mind (as detailed in the documentation for [`NOTIFY`](https://www.postgresql.org/docs/10/sql-notify.html)) that notifications performed inside a transaction are not delivered until the transaction commits. Notifications performed outside a transaction are delivered immediately.
