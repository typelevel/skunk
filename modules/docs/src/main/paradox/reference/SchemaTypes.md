# Schema Types

Skunk codecs have the same names as their corresponding Postgres data types. Defined mappings (including equivalent ANSI SQL types) are summarized below.

## Numeric Types

| ANSI SQL Type      | Size     | Postgres Type   | Scala Type   |
|--------------------|----------|-----------------|--------------|
| `smallint`         | 2 bytes  | `int2`          | `Short`      |
| `integer`          | 4 bytes  | `int4`          | `Int`        |
| `bigint`           | 8 bytes  | `int8`          | `Long`       |
| `decimal(p,s)`     | variable | `numeric(p,s)`  | `BigDecimal` |
| `numeric(p,s)`     | variable | `numeric(p,s)`  | `BigDecimal` |
| `real`             | 4 bytes  | `float4`        | `Float`      |
| `double precision` | 8 bytes  | `float8`        | `Double`     |

#### Notes

- These codecs are importable from `skunk.codec.numeric._` or `skunk.codec.all._`.
- The precision and scale arguments on `numeric` are optional, and if unspecified indicate no limit (up to implementation constraints). It is also possible to specify only the precision, in which case the scale is zero; i.e., `numeric(3)` and `numeric(3,0)` are the same type.
- The pseudo-types `smallserial`, `serial` and `bigserial` have schema types `int2`, `int4` and `int8`, respectively.
- Postgres `float4` and Scala `Float` are not precisely the same. Need to investigate more.
- See [§8.1](https://www.postgresql.org/docs/current/datatype-numeric.html) in the Postgres documentation for more information on numeric data types.

## Character Types

| ANSI SQL Type                        | Postgres Type | Scala Type |
|--------------------------------------|---------------|------------|
| `character varying(n)`, `varchar(n)` | `varchar(n)`  | `String`   |
| `character(n)`, `char(n)`            | `bpchar(n)`   | `String`   |
| n/a                                  | `text`        | `String`   |

#### Notes

- These codecs are importable from `skunk.codec.text._` or `skunk.codec.all._`.
- The length specifier is optional. `bpchar` has a length of one, and `varchar` is unbounded.
- See [§8.3](https://www.postgresql.org/docs/9.1/datatype-character.html) in the Postgres documentation for more information on character data types.

## Date/Time Types

| ANSI SQL Type                 | Postgres Type    | Scala Type                 |
|-------------------------------|------------------|----------------------------|
| `date`                        | `date`           | `java.time.LocalDate`      |
| `time(s)`                     | `time(s)`        | `java.time.LocalTime`      |
| `time(s) with time zone`      | `timetz(s)`      | `java.time.OffsetTime`     |
| `timestamp(s)`                | `timestamp(s)`   | `java.time.LocalDateTime`  |
| `timestamp(s) with time zone` | `timestamptz(s)` | `java.time.OffsetDateTime` |
| n/a                           | `interval`       | `java.time.Duration`       |

#### Notes

- These codecs are importable from `skunk.codec.temporal._` or `skunk.codec.all._`.
- The optional scale argument for data types with a time component indicates the number of fractional digits that appear in the seconds position, which ranges from zero to six (the default).
- See [§8.5](https://www.postgresql.org/docs/9.1/datatype-datetime.html) in the Postgres documentation for more information on date/time data types.


## Boolean Type

| ANSI SQL Type      | Size     | Postgres Type   | Scala Type   |
|--------------------|----------|-----------------|--------------|
| `boolean`          | 1 byte   | `bool`          | `Boolean`    |

#### Notes

- This codec is importable from `skunk.codec.boolean._` or `skunk.codec.all._`.
- See [§8.6](https://www.postgresql.org/docs/9.1/datatype-boolean.html) in the Postgres documentation for more information on the boolean data type.

## Binary Types

| ANSI SQL Type      | Postgres Type   | Scala Type              | Notes                   |
|--------------------|-----------------|-------------------------|-------------------------|
| `blob`             | `bytea`         | `Array[Byte]`           |                         |
| n/a                | `bit`           | `scodec.bits.BitVector` | Equivalent to `bit(1)`. |
| n/a                | `bit(n)`        | `scodec.bits.BitVector` | Exactly `n` bits.       |
| n/a                | `varbit(n)`     | `scodec.bits.BitVector` | At most `n` bits.       |
| n/a                | `varbit`        | `scodec.bits.BitVector` | Any size.               |

#### Notes
- Prefer `boolean` over `bit`/`bit(1)`.
- The `bytea` codec uses [Hex Format](https://www.postgresql.org/docs/11/datatype-binary.html#id-1.5.7.12.9). Bytea octets in PostgreSQL are output in hex format by default.
- See [§8.4](https://www.postgresql.org/docs/11/datatype-binary.html) in the Postgres documentation for more information on the `bytea` data type.
- See [§8.10](https://www.postgresql.org/docs/11/datatype-bit.html) in the Postgres documentation for more information on the `bit` and `varbit` data types.

## Enumerated Types

Enumerated types are user-defined and are mapped via the `enum` codec constructor, which declares the schema type and defines a mapping to and from string labels. Given the enum declaration:

```
CREATE TYPE myenum AS ENUM ('foo', 'bar')
```

an equivalent Scala data type and codec could be defined like this.

```scala
// An enumerated type
sealed abstract class MyEnum(val label: String)
object MyEnum {

  case object Foo extends MyEnum("foo")
  case object Bar extends MyEnum("bar")

  val values = List(Foo, Bar)

  def fromLabel(label: String): Option[MyEnum] =
    values.find(_.label == label)

}

// A codec that maps Postgres type `myenum` to Scala type `MyEnum`
val myenum = enum[MyEnum](_.label, MyEnum.fromLabel, Type("myenum"))
```

Alternatively you can use [Enumeratum](https://github.com/lloydmeta/enumeratum) to define your enumerated type.

```scala
// An enumerated type defined with Enumeratum, with lower-case labels
sealed trait MyEnum extends EnumEntry with Lowercase

object MyEnum extends Enum[MyEnum] {

  case object Foo extends MyEnum
  case object Bar extends MyEnum

  val values = findValues

}

// A codec that maps Postgres type `myenum` to Scala type `MyEnum`
val myenum = enum(MyEnum, Type("myenum"))
```

#### Notes

- This codec constructor is importable from `skunk.codec.enum._` or `skunk.codec.all._`.
- [Enumeratum](https://github.com/lloydmeta/enumeratum) is a transitive dependency of Skunk.
- See [§8.7](https://www.postgresql.org/docs/11/datatype-enum.html) in the Postgres documentation for more information on the enumerated data types.


## UUID Type

| ANSI SQL Type   | Postgres Type    | Scala Type       |
|-----------------|------------------|------------------|
| n/a             | `uuid`           | `java.util.UUID` |

#### Notes

- This codec constructor is importable from `skunk.codec.uuid._` or `skunk.codec.all._`.
- See [§8.12](https://www.postgresql.org/docs/11/datatype-uuid.html) in the Postgres documentation for more information on the enumerated data types.



## JSON Types

| ANSI SQL Type   | Postgres Type    | Scala Type      |
|-----------------|------------------|-----------------|
| n/a             | `json`           | `io.circe.Json` |
| n/a             | `jsonb`          | `io.circe.Json` |

JSON support is provided by the `skunk-circe` module which must be added as a dependency.

@@dependency[sbt,Maven,Gradle] {
  group="$org$"
  artifact="$circe-dep$"
  version="$version$"
}

The `json` and `jsonb` codecs map to the `io.circe.Json` data type, but you can map to any data type with `io.circe.Encoder` and `io.circe.Decoder` instances by passing a type argument to `json` or `jsonb`.

```scala
// Codec for Int ~ Json as (int4, jsonb)
val codec1: Codec[Int ~ Json] = int4 ~ jsonb

// Codec for Int ~ MyType as (int4, jsonb)
// Assuming we have io.circe.Encoder and io.circe.Decoder instances.
val codec2: Codec[Int ~ MyType] = int4 ~ jsonb[MyType]
```

## Arrays

Postgres arrays are either empty and zero-dimensional, or non-empty and rectangular (unlike Scala `Array`s, which are ragged) with a postive number of dimensions (all non-empty). This data type is represented by `skunk.data.Arr`.

@@@ warning
- Skunk does not yet support arrays containing nullable elements. Attempting to decode such a value will provoke a runtime failure.
- The API for `Arr` is not finalized and is likely to change in future versions.
@@@

#### Numeric Array Types

| Postgres Type   | Scala Type        | Notes |
|-----------------|-------------------|---|
| `_int2`         | `Arr[Short]`      |  |
| `_int4`         | `Arr[Int]`        |  |
| `_int8`         | `Arr[Long]`       | Precision argument not yet supported. |
| `_numeric`      | `Arr[BigDecimal]` |  |
| `_float4`       | `Arr[Float]`      |  |
| `_float8`       | `Arr[Double]`     |  |

#### Character Types

| Postgres Type   | Scala Type        | Notes |
|-----------------|-------------------|--------|
| `_varchar` | `Arr[String]` | Length argument not yet supported. |
| `_bpchar`  | `Arr[String]` | Length argument not yet supported |
| `_text`    | `Arr[String]` |  |

## ltree Types

| ANSI SQL Type      | Postgres Type   | Scala Type   |
|--------------------|-----------------|--------------|
| n/a                | `ltree`         | `LTree`      |

#### Notes

- See [§8.15](https://www.postgresql.org/docs/11/arrays.html) in the Postgres documentation for more information on JSON data types.

