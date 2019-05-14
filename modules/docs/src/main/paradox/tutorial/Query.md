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

Postgres provides a [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#id-1.10.5.7.4) for execution of simple queries, returning all rows at once (Skunk returns them as a list). Such queries can be passed directly to @scaladoc[Session.execute](skunk.Session#execute).

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-a-exec }

## Multi-Column Query

Our next example selects two columns.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-b }

Observe that the argument to `query` is a pair of decoders conjoined with the `~` operator, yielding a return type of `String ~ Int`. See the section on @ref:[twiddle lists](../reference/TwiddleLists.md) for reference on this mechanism.

Decoding into a twiddle list isn't ideal, so let's define a `Country` data type. We can them call `map` on our query to adapt the row type.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-c }

So that is is one way to do it.

A more reusable way to do this is to define a `Decoder[Country]` based on the `varchar ~ int4` decoder. We can then decode directly into our `Country` data type.

@@snip [Query.scala](/modules/docs/src/main/scala/tutorial/Query.scala) { #query-d }

## Paramererized Query

