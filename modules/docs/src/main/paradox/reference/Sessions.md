```scala mdoc:invisible
import cats.effect._, skunk._, skunk.net.message.StartupMessage
implicit def dummyContextTrace: ContextShift[IO] = ???
implicit def dummyTrace: natchez.Trace[IO] = ???
```

# Sessions

`Session` represents a connection to a Postgres database.

## Obtaining a Session

TBD - talk about pooling, session lifecycle, cleanup, etc.

## Authentication

Skunk currently supports the **trust** (no password necessary) and **password** (`md5` only) authentication methods.

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


## Default Session Parameters

The following Postgres session parameters affect data serialization and are specified by Skunk during startup negotiation. Changing them via a `SET` command will result in undefined behavior.

| Parameter | Value |
|----------|-------|
```scala mdoc:passthrough
println(StartupMessage.ConnectionProperties.map { case (k, v) => s"| `$k` | `$v` |" } .mkString("\n"))
```

Future versions of Skunk may be more flexible in this regard, but for now your application needs to be ok with these defaults.