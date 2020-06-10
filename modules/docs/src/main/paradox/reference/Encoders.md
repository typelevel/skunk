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

```scala
sql"SELECT name FROM country WHERE code = $varchar"
// Fragment[String]
// SELECT name FROM country WHERE code = $1
```

If there is more than one interpolated encoder (or an encoder composed of multiple smaller encoders,
as we will see below) there will be multiple placeholders in the SQL statement, numbered
sequentially.

```scala
sql"SELECT name FROM country WHERE code = $varchar AND population < $int8"
// Fragment[String ~ Long]
// SELECT name FROM country WHERE code = $1 AND population < $2
```

## Composite Encoders

Given two encoders `a: Encoder[A]` and `b: Encoder[B]` we can create a composite encoder `a ~ b` of
type `Encoder[(A, B)]`. Such an encoder expands to a sequence of placholders separated by commas.

@@@ note { title=Definition }
A **composite encoder** maps a Scala type to a *sequence* of Postgres schema types.
@@@

Here is a statement with a composite encoder constructed from two base encoders.

```scala
sql"INSERT INTO person (name, age) VALUES (${varchar ~ int4})"
// Fragment[String ~ Int]
// INSERT INTO person (name, age) VALUES ($1, $2)
```

Here is a statement with a base encoder and a composite encoder constructed from three base encoders.
Note that the composite structure is "flattened" in the resulting SQL statement; it is purely for
convenience on the Scala side.

```scala
val enc = varchar ~ int4 ~ float4 // Encoder[String ~ Int ~ Float]
sql"INSERT INTO person (comment, name, age, weight, comment) VALUES ($text, $enc)"
// Fragment[String ~ (String ~ Int ~ Float)]
// INSERT INTO person (name, age, weight, comment) VALUES ($1, $2, $3, $4)
```

## Combinators

The `values` combinator adds parentheses around an encoder's generated placeholders.

```scala
val enc = (varchar ~ int4).values // Encoder[String ~ Int]
sql"INSERT INTO person (name, age) VALUES $enc"
// Fragment[String ~ Int]
// INSERT INTO person (name, age) VALUES ($1, $2)
```

This can be very useful in combination with the `list` combinator, which creates an encoder for a
list of values of a given length.

```scala
val enc = (varchar ~ int4).values // Encoder[String ~ Int]
sql"INSERT INTO person (name, age) VALUES ${enc.list(3)}"
// Fragment[List[String ~ Int]]
// INSERT INTO person (name, age) VALUES ($1, $2), ($3, $4), ($5, $6)"
```

## Transforming the Input Type

An `Encoder[A]` consumes a value of type `A` and encodes it for transmission Postgres, so it can
therefore also consume anything that we can _turn into an `A`_. We do this with `contramap`.

```scala
case class Person(name: String, age: Int)
val person = (varchar ~ age).values.contramap((p: Person) => p.name ~ p.age)
sql"INSERT INTO person (name, age) VALUES $person"
// Fragment[Person]
// INSERT INTO person (name, age) VALUES ($1, $2)
```

Because contramapping from case classes is so common, Skunk provides `gcontramap` which adapts
an encoder to a case class of the same structure.

```scala
case class Person(name: String, age: Int)
val person = (varchar ~ age).values.gcontramap[Person]
sql"INSERT INTO person (name, age) VALUES $person"
// Fragment[Person]
// INSERT INTO person (name, age) VALUES ($1, $2)
```

