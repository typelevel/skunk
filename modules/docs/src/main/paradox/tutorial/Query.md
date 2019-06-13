# Queries

This section explains how to construct and execute queries.

@@@ note { title=Definition }
A *query* is a SQL statement that can returns rows.
@@@

## Single-Column Query

First let's look at a query that selects a single column and decodes rows as Scala strings. We will also include the imports we need for the examples in this section.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-a }

Observe the following:

- We are using the @ref:[sql interpolator](../reference/Fragments.md) to construct a @scaladoc[Fragment](skunk.Fragment), which we then turn into a @scaladoc[Query](skunk.Query) by calling the `query` method (fragments are also used to construct @ref[Commands](Command.md)).
- The argument to `query` is a value called `varchar`, which has type `Decoder[String]` and defines the read relationship between the Postgres type `varchar` and the Scala type `String`. The relationship between Postgres types and Scala types is summarized in the reference section @ref:[Schema Types](../reference/SchemaTypes.md).
- The first type argument for our `Query` type is `Void`, which means this query has no parameters. The second type argument is `String`, which means we expect rows to be decoded as `String` values (via our `varchar` decoder).

@@@ note
Queries and Command types are usually inferrable, but specifying a type ensures that the chosen encoders and decoders are consistent with the expected input and output Scala types. For this reason (and for clarity) we will always use explicit type annotations in the documentation.
@@@

The query above is a *simple query*.

@@@ note { title=Definition }
A *simple query* is a query with no parameters.
@@@

Postgres provides a [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#id-1.10.5.7.4) for execution of simple queries, returning all rows at once (Skunk returns them as a list). Such queries can be passed directly to @scaladoc[Session#execute](skunk.Session#execute).

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-a-exec }

@scaladoc[Session](skunk.Session) provides the following methods for direct execution of simple queries. See the Scaladoc for more information.

| Method    | Return Type    | Notes                                             |
|-----------|----------------|---------------------------------------------------|
| `execute` | `F[List[A]]`   | All results, as a list.                           |
| `option`  | `F[Option[A]]` | Zero or one result, otherwise an error is raised. |
| `unique`  | `F[A]`         | Exactly one result, otherwise an error is raised. |

## Multi-Column Query

Our next example selects two columns.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-b }

Observe that the argument to `query` is a pair of decoders conjoined with the `~` operator, yielding a return type of `String ~ Int`, which is an alias for `(String, Int)`. See the section on @ref:[twiddle lists](../reference/TwiddleLists.md) for more information on this mechanism.

### Mapping Query Results

Decoding into a twiddle list isn't ideal, so let's define a `Country` data type. We can them call `map` on our query to adapt the row type.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-c }

Observe the following:

- At ① we request that rows be decoded by `varchar ~ int4` into Scala type `String ~ Int`.
- At ② we `map` to our `Country` data type, yielding a `Query[Void, Country]`.

So that is is one way to do it.

### Mapping Decoder Results

A more reusable way to do this is to define a `Decoder[Country]` based on the `varchar ~ varchar` decoder. We can then decode directly into our `Country` data type.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-d }

Observe the following:

- At ① we map the `varchar ~ int4` decoder directly to Scala type `Country`, yielding a `Decoder[Country]`.
- At ② we use our `country` decoder directly, yielding a `Query[Void, Country]`.

And again we can pass the query to @scaladoc[Session#execute](skunk.Session#execute).

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-d-exec }

## Parameterized Query

Now let's add a parameter to the query. We'll also reformat the query to be more readable.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-e }

Observe that we have interpolated a value called `varchar`, which has type `Encoder[String]`.

This means that Postgres will expect an argument of type `varchar`, which will have Scala type `String`. The relationship between Postgres types and Scala types is summarized in the reference section @ref:[Schema Types](../reference/SchemaTypes.md).

@@@ note
We have already seen `varchar` used as a row *decoder* for `String` and now we're using it as an *encoder* for `String`. We can do this because `encoder` actually has type `Codec[String]`, which extends both `Encoder[String]` and `Decoder[String]`. All type mappings provided by Skunk are codecs and thus can be used in both positions.
@@@

The query above is an *extended query*.

@@@ note { title=Definition }
An *extended query* is a query with parameters, or a simple query that is executed via the extended query protocol.
@@@

Postgres provides a [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#PROTOCOL-FLOW-EXT-QUERY) for executing extended queries which is more involved than simple query protocol. It provides for prepared statements that can be reused with different sets of arguments, and provides cursors which allow results to be paged and streamed in constant space.

Here we use the extended query protocol to stream directly to the console using constant space.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-e-exec-a }

Observe that `prepare` returns a `Resource` that prepares the statement before use and then frees it on completion. Here we use @scaladoc[PreparedQuery#stream](skunk.PreparedQuery#stream) to pass our parameter `"U%"` and then create a stream that fetches rows in blocks of 64 and prints them to the console.

Note that when using `Resource` and `Stream` together it is often convenient to express the entire program in terms of `Stream`.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-e-exec-b }

This program does the same thing, but perhaps in a more convenient style.

@scaladoc[PreparedQuery](skunk.PreparedQuery) provides the following methods for execution. See the Scaladoc for more information.

| Method    | Return Type                | Notes                                             |
|-----------|----------------------------|---------------------------------------------------|
| `stream`  | `Stream[F,A]`              | All results, as a stream.                         |
| `option`  | `F[Option[A]]`             | Zero or one result, otherwise an error is raised. |
| `unique`  | `F[A]`                     | Exactly one result, otherwise an error is raised. |
| `cursor`  | `Resource[F,Cursor[F,A]]`  | A cursor that returns pages of results.           |

## Multi-Parameter Query

Multiple parameters work analogously to multiple columns.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-f }

Observe that we have two parameter encoders `varchar` and `int4` (in that order), whose corresponding Scala input type is `String ~ Int`. See the section on @ref:[twiddle lists](../reference/TwiddleLists.md) for more information.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-f-exec }

And we pass the value `"U%" ~ 2000000` as our statement argument.

## Summary of Query Types

The *simple query protocol* (i.e., `Session#execute`) is slightly more efficient in terms of message exchange, so use it if:

- Your query has no parameters; and
- you are querying for a small number of rows; and
- you will be using the query only once per session.


The *extend query protocol* (i.e., `Session#prepare`) is more powerful and more general, but requires additional network exchanges. Use it if:

- Your query has parameters; and/or
- you are querying for a large or unknown number of rows; and/or
- you intend to stream the results; and/or
- you will be using the query more than once per session.

## Full Example

Here is a complete program listing that demonstrates our knowledge thus far.

@@snip [Query2.scala](/modules/docs/src/main/scala/tutorial/Query2.scala) { #full-example }

Running this program yields output like the following.

```
timestamp is 2019-05-14T16:35:19.920737-07:00
Country(United Arab Emirates,ARE,2441000)
Country(United Kingdom,GBR,59623400)
Country(Uganda,UGA,21778000)
Country(Ukraine,UKR,50456000)
Country(Uruguay,URY,3337000)
Country(Uzbekistan,UZB,24318000)
Country(United States,USA,278357000)
Country(United States Minor Outlying Islands,UMI,0)
```

## Experiment

Here are some experiments you might want to try.

- Try to run the `extended` query via `Session#execute`, or the `simple` query via `Session#prepare`. Note that in the latter case you will need to pass the value `Void` as an argument.

- Add/remove/change encoders and decoders. Do various things to make the queries fail. Which kinds of errors are detected at compile-time vs. runtime?

- Add more fields to `Country` and more colums to the query; or add more parameters. You will need to consult the @ref:[Schema Types](../reference/SchemaTypes.md) reference to find the encoders/decoders you need.

- Experiment with the treatment of nullable columns. You need to add `.opt` to encoders/decoders (`int4.opt` for example) to indicate nullability. Keep in mind that for interpolated encoders you'll need to write `${int4.opt}`.

For reference, the `country` table looks like this.

|     Column     |   Postgres Type   | Modifiers |
|----------------|-------------------|-----------
| code           | character(3)      | not null  |
| name           | character varying | not null  |
| continent      | character varying | not null  |
| region         | character varying | not null  |
| surfacearea    | real              | not null  |
| indepyear      | smallint          |           |
| population     | integer           | not null  |
| lifeexpectancy | real              |           |
| gnp            | numeric(10,2)     |           |
| gnpold         | numeric(10,2)     |           |
| localname      | character varying | not null  |
| governmentform | character varying | not null  |
| headofstate    | character varying |           |
| capital        | integer           |           |
| code2          | character(2)      | not null  |

