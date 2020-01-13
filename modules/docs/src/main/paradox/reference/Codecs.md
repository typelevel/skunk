# Codecs

## Encoders and decoders

Encoders and decoders describe relationships between Scala types and Postgres types. Encoders translate Scala values into strings
that are recognized by Postgres. Decoders parse strings returned by Postgres into Scala values.

## Encoders

### Creating custom encoders

Altough you can directly instantiate an `Encoder` trait, a simpler way is to take
an existing encoder and then using the `contramap` on it (the function name comes from `Encoder` being
a @link:[contravariant functor](https://typelevel.org/cats/typeclasses/contravariant.html)).

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-a }

There is quite a bit of boilerplate in the second encoder, so Skunk provides
a handy method called `gcontramap` to make working with case classes easier. 

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-b }

### Encoders for literal values

You may want to use literal values in your SQL queries, e.g. for an `IN` or `VALUES` statement.
`Encoder` exposes two useful methods for this - `list` and `values`.

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-g }

## Decoders

### Creating custom decoders

Similarly to `Encoder`, you can create a custom `Decoder` by mapping over an existing one.
Decoders are @link:[functors](https://typelevel.org/cats/typeclasses/functor.html), so we can
use the `map` function.

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-c }

Custom encoders are often used for case classes. Instead of manually creating the instances
inside the `map`, you can use a helper function called `gmap`.

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-d }

## Codecs

If you look at above examples you might notice that `text` is used as a base
when creating both custom `Encoders` and custom `Decoders`. It's possible because
`text` is of type `Codec[String]`.  A `Codec` is defined as follows:

```scala
trait Codec[A] extends Encoder[A] with Decoder[A]
```

This means that `Codec` is a subtype of both `Encoder` and `Decoder`, so it can be used in places
where Skunk expects either one. 

### Creating a custom codec from an existing one

Because of the inheritance, you can still use methods like `map` or `gcontramap` to obtain a custom
`Decoder` or `Encoder`. However, you can also use more general methods to transform the whole
`Codec` at once. One of them is `imap`, which allows modifying both the encoding and decoding part.

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-e }

Similar to `gmap` and `gcontramap`, there is also a helper method for dealing with case classes.

@@snip [Codecs.scala](/modules/docs/src/main/scala/reference/Codecs.scala) { #codecs-f }