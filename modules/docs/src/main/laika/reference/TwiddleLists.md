# Twiddle Lists

Twiddle lists are tuples that are built incrementally, element by element, via the `*:` operation. Skunk uses the [Typelevel Twiddles](https://github.com/typelevel/twiddles) library for parameter encoders and row decoders, and via this mechanism twiddle list types appear as type arguments for `Query` and `Command`.

```scala
import org.typelevel.twiddles._ // required for Scala 2

val q: Query[Short *: String *: String *: Int *: EmptyTuple] =
  sql"""
    SELECT name, age
    FROM   person
    WHERE  age < $int2
    AND    status = $
  """.query(varchar *: int4)
```

On Scala 3, `*:` and `EmptyTuple` come from the standard library and are exactly the same as a n-argument tuple. For example, we could equivalently write the type of `q` as `Query[(Short, String, String, Int)]` (though not on Scala 2, more on that in a moment). Similarly, we can construct a 4-tuple using `*:` and `EmptyTuple`.

```scala
val t1: (Short, String, String, Int) = (42.toShort, "Edgar", "Dijkstra", 100)
val t2: (Short, String, String, Int) = 42.toShort *: "Edgar" *: "Dijkstra" *: 100 *: EmptyTuple
val t3: Short *: String *: String *: Int *: EmptyTuple = 42.toShort *: "Edgar" *: "Dijkstra" *: 100 *: EmptyTuple
val t4: Short *: String *: String *: Int *: EmptyTuple = (42.toShort, "Edgar", "Dijkstra", 100)
```

On Scala 2, the Twiddles library defines `*:` and `EmptyTuple` as aliases for Shapeless `HList`s. Those polyfills allow the previous examples to compile on Scala 2 (including `t1` through `t4`). Twiddles includes implicit conversions between twiddle lists (i.e., `HList`s) and regular tuples.

## Isomorphism with Case Classes

A twiddle list of type `(A, B, C)` has the same structure as a case class with fields of type `A`, `B`, and `C`, and we can trivially map back and forth.

```scala
case class Person(name: String, age: Int, active: Boolean)

def fromTwiddle(t: String *: Int *: Boolean *: EmptyTuple): Person =
  t match {
    case s *: n *: b *: EmptyTuple => Person(s, n, v)
  }

def toTwiddle(p: Person): String *: Int *: Boolean *: EmptyTuple =
  p.name *: p.age *: p.active *: EmptyTuple
```

Because this mapping is useful and entirely mechanical, the Twiddles library provides a typeclass that does it for you.

```scala
@ val bob = Person("Bob", 42, true)
bob: Person = Person("Bob", 42, true)

@ val iso = Iso.product[Person]
iso: org.typelevel.twiddles.Iso[Person,String *: Int *: Boolean *: org.typelevel.twiddles.EmptyTuple] = org.typelevel.twiddles.Iso$$anon$1@84d9646

@ val twiddle = iso.to(bob)
twiddle: String *: Int *: Boolean *: org.typelevel.twiddles.EmptyTuple = Bob :: 42 :: true :: HNil

@ val bob2 = iso.from(twiddle)
bob2: Person = Person("Bob", 42, true)
```

`Decoder`s, `Encoder`s, and `Codec`s use this facility to provide `to`, which allows quick adaptation of a twiddle-list `Codec` (for instance) to one that maps to/from a case class.

```scala
@ val codec = varchar *: int4 *: bool
codec: Codec[String *: Int *: Boolean *: EmptyTuple] = Codec(varchar, int4, bool)

// Since `Person` has the same structure we can use `to` to create a `Codec[Person]`
@ val personCode = codec.to[Person]
personCodec: Codec[Person] = Codec(varchar, int4, bool)
```

## Legacy Twiddle Lists

Prior to Skunk 0.6, a twiddle list was defined as a left-associated chain of pairs.

```scala
val x: (((Int, String), Boolean), Double) =
  (((42, "hi"), true), 1.23)
```

Twiddle lists were built using the `~` operator.

```scala
type ~[+A, +B] = (A, B)

final class IdOps[A](a: A) {
  def ~[B](b: B): A ~ B = (a, b)
}

// ~ is left-associative so this is exactly the same as `x` above.
val x: Int ~ String ~ Boolean ~ Double =
  42 ~ "hi" ~ true ~ 1.23
```

And thanks to the following definition, we could also pattern-match.

```scala
object ~ {
  def unapply[A, B](t: A ~ B): Some[A ~ B] = Some(t)
}

// Deconstruct `x`
x match {
  case n ~ s ~ b ~ d => ...
}
```

Skunk's `Codec`, `Decoder`, and `Encoder` types provided special methods for converting twiddle lists to case classes (`gimap`, `gmap`, `gcontramap` respectively). The `to` operation replaces all of these twiddle specific conversions.

### Legacy Command Syntax

Prior to Skunk 0.6, parameterized commands used (left-nested) twiddle lists. Upon upgrading to Skunk 0.6, such commands use the new style twiddle lists. To ease migration, you can add an import to restore the old behavior until it's convenient to upgrade to new style twiddles.

```scala
import skunk.feature.legacyCommandSyntax

val insertCityLegacy: Command[City] =
  sql"""
      INSERT INTO city
      VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
    """.command.contramap {
          c => c.id ~ c.name ~ c.code ~ c.district ~ c.pop
        }
```

The `skunk.feature.legacyCommandSyntax` import ensures the command returned by the `sql` interpolator uses legacy twiddles. Without the import, the type of the command is `Command[Int *: String *: String *: String *: Int *: EmptyTuple]` and the function passed to `contramap` would need to be adjusted to account for the new style twiddle.

```scala
val insertCity2: Command[City] =
  sql"""
      INSERT INTO city
      VALUES ($int4, $varchar, ${bpchar(3)}, $varchar, $int4)
    """.command.contramap {
          c => (c.id, c.name, c.code, c.district, c.pop)
        }
```
