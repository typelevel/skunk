# Schema Types

Skunk uses Postgres type names for codecs and in error messages. In some cases the relationship with ANSI SQL types isn't entirely obvious. Equivalences are specified below.

## Numeric Types

| ANSI Type          | Size     | Postgres Type   | Scala Type   |
|--------------------|----------|-----------------|--------------|
| `smallint`         | 2 bytes  | `int2`          | `Short`      |
| `integer`          | 4 bytes  | `int4`          | `Int`        |
| `bigint`           | 8 bytes  | `int8`          | `Long`       |
| `decimal(n, m)`    | variable | `numeric`       | `BigDecimal` |
| `numeric(n, m)`    | variable | `numeric`       | `BigDecimal` |
| `real`             | 4 bytes  | `float4`        | `Float`      |
| `double precision` | 8 bytes  | `float8`        | `Double`     |

#### Notes
- The pseudo-types `serial` and `bigserial` have schema types `int4` and `int8`, respectively.
- Variable precision and scale for `numeric(n, m)` are not (yet) represented in the Scala type.
- `float4` and `Float` are not precisely the same. Need to investigate more.

## Character Types

| ANSI Type                            | Postgres Type | Scala Type |
|--------------------------------------|---------------|------------|
| `character varying(n)`, `varchar(n)` | `varchar`     | `String`   |
| `character(n)`, `char(n)`            | `bpchar`      | `String`   |
| n/a                                  | `text`        | `String`   |

#### Notes

- Length limits are not (yet) represented in the Scala type.

## Date/Time Types

| ANSI Type                     | Postgres Type    | Scala Type       |
|-------------------------------|------------------|------------------|
| `date`                        | `date`           | `LocalDate`      |
| `time(p)`                     | `time(p)`        | `LocalTime`      |
| `time(p) with time zone`      | `timetz(p)`      | `OffsetTime`     |
| `timestamp(p)`                | `timestamp(p)`   | `LocalDateTime`  |
| `timestamp(p) with time zone` | `timestamptz(p)` | `OffsetDateTime` |

#### Notes

- The optional precision argument for data types that include a time component indicates the number of fractional digits that appear in the seconds position, which ranges from zero to six. If `p` is not specified the default is six.

