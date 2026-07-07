```scala mdoc:invisible
import skunk.data.Identifier
import skunk.implicits._
```
# Identifiers

`skunk.data.Identifier` represents a Postgres name, such as the name of a table, column, schema, or channel. It keeps the name separate from its SQL representation so that Skunk can validate and escape it correctly.

## Constructing an Identifier

Use the `ident"..."` interpolator for names known at compile time. It validates the literal, preserves it verbatim, and rejects invalid identifiers during compilation.

```scala mdoc:compile-only
val table: Identifier   = ident"country"
val column: Identifier  = ident"Country.Code"
val keyword: Identifier = ident"SELECT"
```

Use `Identifier.fromValue` when the name is available only at run time.

```scala mdoc:compile-only
def identifier(name: String): Either[String, Identifier] =
  Identifier.fromValue(name)
```

An identifier must be non-empty, contain no NUL byte, and occupy at most 63 bytes when encoded as UTF-8. The byte limit matters for names containing multibyte characters.

## SQL Rendering

Postgres accepts simple lower-case names as bare SQL identifiers. Other names, including mixed-case names, reserved words, and names containing punctuation, must be enclosed in double quotes. `Identifier` handles this distinction automatically.

The `sql` member returns the SQL-ready representation. It leaves a name bare when that is safe, otherwise it adds double quotes and escapes any double quotes contained in the name. `toString` returns the same representation, while `value` returns the original, unescaped name.

```scala mdoc:compile-only
val plain = ident"my_table"
val plainValue = plain.value // "my_table"
val plainSql = plain.sql     // "my_table"

val mixedCase = ident"My.Channel"
val mixedCaseValue = mixedCase.value // "My.Channel"
val mixedCaseSql = mixedCase.sql     // "\"My.Channel\""

val embeddedQuote = ident"""say"hello"""
val embeddedQuoteSql = embeddedQuote.sql // "\"say\"\"hello\""
```

`Channel` uses `sql` internally when issuing `LISTEN`/`UNLISTEN`/`NOTIFY`, so quoted channel names round-trip correctly.

## Legacy Construction

The deprecated `Identifier.fromString` method accepts only names matching `[A-Za-z_][A-Za-z_0-9$]*`, rejects keywords, and folds accepted names to lower case. The deprecated `id"..."` interpolator performs the same operation at compile time. New code should use `Identifier.fromValue` or `ident"..."`, which preserve the supplied name. To retain the legacy case-folding behavior, pass an already lower-case value.
