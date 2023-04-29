```scala mdoc:nest:invisible
import cats.syntax.all._
import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._
```

# Fragments

`Fragment[A]` encapsulates a SQL statement, together with an `Encoder` for a statement parameter of
type `A`. Fragments are normally constructed via the `sql` interpolator, which constructs a fragment of a type determined by interpolated parameter encoders, if any.

```scala mdoc
// A fragment with no interpolated encoders.
val f1 = sql"SELECT 42"

// A fragment with an interpolated encoder.
val f2 = sql"SELECT foo FROM bar WHERE baz = $int8"
```

The following sections provide more details on constructing fragments.

## Interpolating Parameter Encoders

Interpolated encoders are replaced with statement parameters (see the first element shown in the
`Fragment` string representation below).

```scala mdoc
val f3 = sql"foo $int4 bar $varchar bar"
```

The resulting statement is prepared, and arguments (encoded) are passed separately as part of the
extended query protocol. **Skunk never interpolates statement arguments.**

Some encoder combinators have an effect on the SQL that is generated when they are interpolated. In
the examples below we will use `Fragment`'s `sql` member for clarity.

Interpolating an encoder **product** (as constructed with `*:` for instance) yields a
comma-separated sequence of parameters in the resulting SQL.

```scala mdoc
sql"foo ${int4 *: varchar} bar".sql
```

The `values` combinator wraps an encoder's SQL in parentheses.

```scala mdoc
sql"foo ${(int4 *: varchar).values} bar".sql
```

The `list` combinator yields a sequence of parameters, one per element (this is why we must know
the length when constructing the fragment).

```scala mdoc
sql"foo ${int4.list(4)} bar".sql
```

When used in combination these can be quite useful.

```scala mdoc
sql"INSERT ... VALUES ${(int4 *: varchar).values.list(3)}".sql
```


## Interpolating Literal Strings

Parameters can only appear in syntactic positions where values can appear (you cannot use a
parameter in place of a table name for instance). In these cases you can interpolate a literal
strings, by escaping it with `#$`.

@@@warning
Interpolating a literal string into a `Fragment` is a SQL injection risk. Never interpolate values
that have been supplied by the user.
@@@

Here is an example with an iterpolated literal string, as well as a normal parameter.

```scala mdoc:silent
val table = "my_table"
val frag  = sql"SELECT foo, bar FROM #$table where foo = $int4"
```

The resulting SQL will contain `table` verbatim.

```scala mdoc
frag.sql
```

## Composing Fragments

Fragment products operate like encoder products, appending the resulting SQL.

```scala mdoc
val f4 = sql"SELECT $int4, foo FROM blah WHERE "

val f5 = sql"bar = $varchar"

val f6 = f4 *: f5
```

Alternatively we can interpolate fragments inside one another.

```scala mdoc
val f7 = sql"bar = $varchar"

val f8 = sql"SELECT $int4, foo FROM blah WHERE $f7 AND x = $int2"
```

Note how the output type is computed, and parameters are renumbered as necessary.


## Contramapping Fragments

Fragments form a contravariant semigroupal functor, and this can be tupled (with `*:` as above) and
can be contramapped to change their input type.

```scala mdoc
case class Person(name: String, age: Int)

val f9 = sql"INSERT ... VALUES ($varchar, $int4)"

// note the return type
val f10 = f9.contramap[Person](p => (p.name, p.age))

// alternatively
val f11 = f9.as[Person]
```


## Applied Fragments

It is sometimes useful to bind a fragment to a set of arguments. The resulting `AppliedFragment`
forms a monoid, with an opaque argument of an existential argument type. Applied fragments can be
useful when constructing statements on the fly.

```scala mdoc
def countryQuery(name: Option[String], pop: Option[Int]): AppliedFragment = {

  // Our base query
  val base = sql"SELECT code FROM country"

  // Some filter conditions
  val nameLike       = sql"name LIKE $varchar"
  val popGreaterThan = sql"population > $int4"

  // Applied fragments for conditions, if any.
  val conds: List[AppliedFragment] =
    List(
      name.map(nameLike),
      pop .map(popGreaterThan),
    ).flatten

  // The composed filter.
  val filter =
    if (conds.isEmpty) AppliedFragment.empty
    else conds.foldSmash(void" WHERE ", void" AND ", AppliedFragment.empty)

  // Prepend the base query and we're done.
  base(Void) |+| filter

}

countryQuery(Some("Un%"), Some(99999)).fragment.sql

countryQuery(Some("Un%"), None).fragment.sql

countryQuery(None, None).fragment.sql
```

To prepare and execute some `af: AppliedFragment` you must extract its underlying `fragment: Query[af.A]` and `arg: af.A` members, as below.

```scala mdoc
def usage(s: Session[IO]) = {
  val f = countryQuery(Some("Un%"), None) // AppliedFragment
  val q = f.fragment.query(varchar)       // Query[f.A, String]
  s.prepare(q).flatMap(_.stream(f.argument, 64).compile.to(List))
}
```