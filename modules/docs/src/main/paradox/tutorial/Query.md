# Queries

This section explains how to construct and execute queries.

@@@ note { title=Definition }
A *query* is a SQL statement that can returns rows.
@@@

## Single-Column Query

First let's look at a query that selects a single column and decodes rows as Scala strings. We will also include the imports we need for the examples in this section.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-a }

Observe the following:

- We are using the @ref:[sql interpolator](../reference/Fragments.md) to construct a @scaladoc[Fragment](skunk.Fragment), which we then turn into a @scaladoc[Query](skunk.Query) by calling the `query` method (fragments are also used to consruct @ref[Commands](Command.md)).
- The argument to `query` is a Decoder called `int4`, which defines the read relationship between the Postgres type `int4` and the Scala type `Int`. This is where the second type argument in `Query[Void, Int]` comes from.
- The first type argument is `Void`, which means this query has no parameters.

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

Observe that the argument to `query` is a pair of decoders conjoined with the `~` operator, yielding a return type of `String ~ Int`. See the section on @ref:[twiddle lists](../reference/TwiddleLists.md) for reference on this mechanism.

Decoding into a twiddle list isn't ideal, so let's define a `Country` data type. We can them call `map` on our query to adapt the row type.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-c }

So that is is one way to do it.

A more reusable way to do this is to define a `Decoder[Country]` based on the `varchar ~ int4` decoder. We can then decode directly into our `Country` data type.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-d }

And again we can pass the query to @scaladoc[Session#execute](skunk.Session#execute).

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-d-exec }


## Parameterized Query

Now let's add a parameter to the query. We'll also reformat the query to be more readable.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-e }

Observe that we have interpolated an Encoder called `varchar`. This means that Postgres will expect an argument of type `varchar`, which will have Scala type `String`. The relationships between Postgres types and Scala types is summarized in the reference section @ref:[Schema Types](../reference/SchemaTypes.md).

@@@ note { title=Definition }
An *extended query* is a query with parameters, or a simple query that is executed via the extended query protocol.
@@@

Postgres provides a [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#PROTOCOL-FLOW-EXT-QUERY) for executing extended queries which is more involved than simple query protocol. It provides for prepared statements that can be reused with different sets of arguments, and provides cursors which allow results to be paged and streamed in constant space.

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

