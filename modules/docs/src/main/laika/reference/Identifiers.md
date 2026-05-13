```scala mdoc:invisible
import skunk.data.Identifier
import skunk.implicits._
```
# Identifiers

`skunk.data.Identifier` represents a Postgres SQL identifier — the name of a table, column, schema, channel, etc. Skunk validates identifiers up front so they can be safely spliced into SQL without risking injection.

Postgres recognises two flavours of identifier, and `Identifier` supports both.

## Unquoted identifiers

An *unquoted* identifier matches `[A-Za-z_][A-Za-z_0-9$]*`, is at most 63 characters, and is not a reserved keyword. Postgres folds unquoted identifiers to lower case, so `FOO`, `Foo`, and `foo` all refer to the same object.

Construct one with `Identifier.fromString` or the `id"…"` interpolator:

```scala mdoc:compile-only
val a: Either[String, Identifier] = Identifier.fromString("my_table")
val b: Identifier                 = id"my_table"
```

The `id"…"` form validates at compile time and fails the build for malformed input.

## Quoted (delimited) identifiers

A *quoted* (delimited) identifier is any non-empty character sequence that does not contain the NUL byte. Quoting preserves case and lets you use characters or reserved words that an unquoted identifier cannot.

Construct one with `Identifier.fromStringQuoted` or the `qid"…"` interpolator:

```scala mdoc:compile-only
val a: Either[String, Identifier] = Identifier.fromStringQuoted("MyTable")  // case preserved
val b: Identifier                 = qid"q_my_queue.INSERT"                  // keywords allowed
```

Like `id"…"`, the `qid"…"` form validates at compile time and fails the build for malformed input (empty string, embedded space, or > 63 bytes).

Length is checked in **bytes** (Postgres' `NAMEDATALEN-1` is byte-counted), so multibyte characters are accounted for correctly.

## Rendering as SQL

`Identifier#asSql` returns the SQL-ready form: the bare value for unquoted identifiers, or the value wrapped in double quotes (with any embedded `"` doubled) for quoted ones. `toString` returns `asSql`, so logged identifiers show their SQL-correct form. `value` always returns the bare, unescaped name.

```scala mdoc:compile-only
val unq = id"my_table"
val unqRendered = unq.asSql       // "my_table"

val q = qid"My.Channel"
val qBare = q.value               // "My.Channel"
val qRendered = q.asSql           // "\"My.Channel\""
```

`Channel` uses `asSql` internally when issuing `LISTEN`/`UNLISTEN`/`NOTIFY`, so quoted channel names round-trip correctly.
