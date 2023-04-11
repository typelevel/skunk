# Twiddle Lists

A twiddle list is a left-associated chain of pairs. It can be arbitrarily long, and the type grows to track the elements.

```scala
val x: (((Int, String), Boolean), Double) =
  (((42, "hi"), true), 1.23)
```

The reason it is useful is that (when combined with a single value as the dengenerate case) it lets us represent non-empty heterogeneous lists. It is sometimes called the "poor-man's `HList`" and it is not unique to Skunk ([scodec](http://scodec.org/) also uses it).

By providing a bit of syntax we can write twiddle lists much more succinctly.

```scala
type ~[+A, +B] = (A, B)

final class IdOps[A](a: A) {
  def ~[B](b: B): A ~ B = (a, b)
}

// ~ is left-associative so this is exactly the same as `x` above.
val x: Int ~ String ~ Boolean ~ Double =
  42 ~ "hi" ~ true ~ 1.23
```

And with the following definition we can also pattern-match.

```scala
object ~ {
  def unapply[A, B](t: A ~ B): Some[A ~ B] = Some(t)
}

// Deconstruct `x`
x match {
  case n ~ s ~ b ~ d => ...
}
```

Skunk uses twiddle-lists for parameter encoders and row decoders, and via this mechanism twiddle list types appear as type arguments for `Query` and `Command`.

```scala
val q: Query[Short ~ String, String ~ Int] =
  sql"""
    SELECT name, age
    FROM   person
    WHERE  age < $int2
    AND    status = $
  """.query(varchar ~ int4)
```

## Isomorphism with Case Classes

A twiddle list of type `A ~ B ~ C` has the same structure as a case class with fields of type `A`, `B`, and `C`, and we can trivially map back and forth.

```scala
case class Person(name: String, age: Int, active: Boolean)

def fromTwiddle(t: String ~ Int ~ Boolean): Person =
  t match {
    case s ~ n ~ b => Person(s, n, v)
  }

def toTwiddle(p: Person): String ~ Int ~ Boolean =
  p.name ~ p.age ~ p.active
```

Because this mapping is useful and entirely mechanical Skunk provides a typeclass that does it for you.

```scala
@ val bob = Person("Bob", 42, true)
bob: Person = Person("Bob", 42, true)

@ val tw = skunk.util.Twiddler[Person]
tw: util.Twiddler.Aux[Person, ((String, Int), Boolean)] = skunk.util.Twiddler$$anon$3@41d1bcbd

@ tw.to(bob)
res5: ((String, Int), Boolean) = (("Bob", 42), true)

@ tw.from(res5)
res6: Person = Person("Bob", 42, true)
```

`Decoder`s, `Encoder`s, and `Codec`s use this facility to provide `gmap`, `gcontramap`, and `gimap`, respectively, which allow quick adaptation of a twiddle-list `Codec` (for instance) to one that maps to/from a case class. The `g` stands for "generic".

```scala
// Note that the ~ method on Codec is an alias for Apply.product,
// so Codec[A] ~ Codec[B] yields Codec[A ~ B].
@ val codec = varchar ~ int4 ~ bool
codec: Codec[((String, Int), Boolean)] = Codec(varchar, int4, bool)

// Since `Person` has the same structure we can gimap to it.
@ codec.gimap[Person]
res7: Codec[Person] = Codec(varchar, int4, bool)
```
