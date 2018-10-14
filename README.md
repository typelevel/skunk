# Skunk

Skunk is an **experimental** Postgres client for Scala.

- Skunk is non-blocking.
- Skunk is functional. It is written with cats-effect, scodec, and fs2.
- Skunk speaks the wire protocol. It does not use JDBC.
- Skunk is written in tagless style.

## Quick Start

There is no quick start.

- You must build from source.
- Password negotiation and SSL are not implemented yet, so you must be able to connect without a password; i.e., `psql -U <user> -d <database>` should connect you without a password prompt.

Import stuff. You will probably needs bits and pieces of cats and fs2 as well. I assume you know what you need.

```scala
import skunk._, skunk.implicits._
```

## Connecting

