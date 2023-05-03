```scala mdoc:nest:invisible
import skunk._
import skunk.implicits._
import skunk.codec.all._
```
# Encoders

An encoder is needed any time you want to send a value to Postgres; i.e., any time a statement has
parameters. Although it is possible to implement the `Encoder` interface directly, this requires
knowledge of Postgres data formats and is not something you typically do as an end user. Instead
you will use one or more existing encoders (see @ref:[Schema Types](../reference/SchemaTypes.md))
composed or transformed as desired.

## Base Encoders

Base encoders are provided for many Postgres types (see @ref:[Schema Types](../reference/SchemaTypes.md)).
These encoders translate to a single placeholders in your SQL statement.

@@@ note { title=Definition }
A **base encoder** maps a Scala type to a *single* Postgres schema type.
@@@

Here is a statement with an interpolated base encoder.

```scala mdoc:nest
sql"SELECT name FROM country WHERE code = $varchar"
```

If there is more than one interpolated encoder (or an encoder composed of multiple smaller encoders,
as we will see below) there will be multiple placeholders in the SQL statement, numbered
sequentially.

```scala mdoc:nest
sql"SELECT name FROM country WHERE code = $varchar AND population < $int8"
```

## Composite Encoders

Given two encoders `a: Encoder[A]` and `b: Encoder[B]` we can create a composite encoder `a ~ b` of
type `Encoder[(A, B)]`. Such an encoder expands to a sequence of placholders separated by commas.

@@@ note { title=Definition }
A **composite encoder** maps a Scala type to a *sequence* of Postgres schema types.
@@@

Here is a statement with a composite encoder constructed from two base encoders.

```scala mdoc:nest
sql"INSERT INTO person (name, age) VALUES (${varchar ~ int4})"
```

Here is a statement with a base encoder and a composite encoder constructed from three base encoders.
Note that the composite structure is "flattened" in the resulting SQL statement; it is purely for
convenience on the Scala side.

```scala mdoc:nest
val enc = varchar ~ int4 ~ float4

sql"INSERT INTO person (comment, name, age, weight, comment) VALUES ($text, $enc)"
```

## Combinators

The `values` combinator adds parentheses around an encoder's generated placeholders.

```scala mdoc:nest
val enc = (varchar ~ int4).values

sql"INSERT INTO person (name, age) VALUES $enc"
```

This can be very useful in combination with the `list` combinator, which creates an encoder for a
list of values of a given length.

```scala mdoc:nest
val enc = (varchar ~ int4).values

sql"INSERT INTO person (name, age) VALUES ${enc.list(3)}"
```

Similarly, the `values` and `list` combinators can be used to encode an `IN` clause for queries.

```scala mdoc:nest
case class Person(name: String, age: Int)

val names = List("Ron", "Leslie", "Andy")
val namesEnc = varchar.values.list(names.length)

sql"SELECT * FROM person WHERE name IN $namesEnc"
```

## Transforming the Input Type

An `Encoder[A]` consumes a value of type `A` and encodes it for transmission Postgres, so it can
therefore also consume anything that we can _turn into an `A`_. We do this with `contramap`.

```scala mdoc:nest
case class Person(name: String, age: Int)

val person = (varchar *: int4).values.contramap((p: Person) => (p.name, p.age))

sql"INSERT INTO person (name, age) VALUES $person"
```

Because contramapping from case classes is so common, Skunk provides `to` which adapts
an encoder to a case class of the same structure.

```scala mdoc:invisible:reset
// do this at the top level.
import skunk._
import skunk.implicits._
import skunk.codec.all._
```

```scala mdoc
case class Person(name: String, age: Int)

val person = (varchar *: int4).values.to[Person]

sql"INSERT INTO person (name, age) VALUES $person"
```

