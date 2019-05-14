# Schema Types

Skunk uses Postgres type names for codecs and in error messages. In some cases the relationship with ANSI SQL types isn't entirely obvious. Equivalences are specified below.

@@@ note
A future version may introduce refined types to distinguish among schema types that can vary in precision and/or scale.
@@@

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

- The precision and scale arguments on `numeric` are optional, and if unspecified indicate no limit (up to implementation constraints). It is also possible to specify only the precision, in which case the scale is zero; i.e., `numeric(3)` and `numeric(3,0)` are the same type.
- The pseudo-types `smallserial`, `serial` and `bigserial` have schema types `int2`, `int4` and `int8`, respectively.
- Postgres `float4` and Scala `Float` are not precisely the same. Need to investigate more.
- See [§8.1](https://www.postgresql.org/docs/current/datatype-numeric.html) in the Postgres documentation for more information on numeric data types.

## Character Types

| ANSI SQL Type                        | Postgres Type | Scala Type |
|--------------------------------------|---------------|------------|
| `character varying(n)`, `varchar(n)` | `varchar`     | `String`   |
| `character(n)`, `char(n)`            | `bpchar`      | `String`   |
| n/a                                  | `text`        | `String`   |

#### Notes

- See [§8.3](https://www.postgresql.org/docs/9.1/datatype-character.html) in the Postgres documentation for more information on character data types.

## Date/Time Types

| ANSI SQL Type                 | Postgres Type    | Scala Type       |
|-------------------------------|------------------|------------------|
| `date`                        | `date`           | `LocalDate`      |
| `time(s)`                     | `time(s)`        | `LocalTime`      |
| `time(s) with time zone`      | `timetz(s)`      | `OffsetTime`     |
| `timestamp(s)`                | `timestamp(s)`   | `LocalDateTime`  |
| `timestamp(s) with time zone` | `timestamptz(s)` | `OffsetDateTime` |

#### Notes

- The optional precision argument for data types that include a time component indicates the number of fractional digits that appear in the seconds position, which ranges from zero to six. If `s` (scale) is not specified the default is six, but note that `time` and `time(6)` are not the same type.
- See [§8.5](https://www.postgresql.org/docs/9.1/datatype-datetime.html) in the Postgres documentation for more information on date/time data types.