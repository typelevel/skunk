```scala mdoc:invisible
import cats.effect._, skunk._, skunk.net.message.StartupMessage
implicit def dummyContextTrace: ContextShift[IO] = ???
implicit def dummyTrace: natchez.Trace[IO] = ???
implicit def dummyTimer: Timer[IO] = ???
```

# Sessions

`Session` represents a connection to a Postgres database.

## Obtaining a Session

TBD - talk about pooling, session lifecycle, cleanup, etc.

## Authentication

Skunk currently supports the **trust** (no password necessary) and **password** (`md5` and `scram-sha-256` only) authentication methods.

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

