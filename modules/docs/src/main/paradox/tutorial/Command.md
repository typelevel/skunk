# Commands

This section explains how to construct and execute commands.

@@@ note { title=Definition }
A *command* is a SQL statement that does not return rows.
@@@

## Simple Command

First let's look at a command that sets the session's random number seed. We will also include the imports we need for the examples in this section.

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-a }

Observe the following:

- We are using the @ref:[sql interpolator](../reference/Fragments.md) to construct a @scaladoc[Fragment](skunk.Fragment), which we then turn into a @scaladoc[Command](skunk.Command) by calling the `command` method.
- `Command` is parameterized by its input type. Because this command has no parameters the input type is `Void`.

The command above is a *simple command*.

@@@ note { title=Definition }
A *simple command* is a command with no parameters.
@@@

The same [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#id-1.10.5.7.4) that executes simple queries also executes of simple commands. Such commands can be passed directly to @scaladoc[Session.execute](skunk.Session#execute).

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-a-exec }

On success a command will yield a @scaladoc[Completion](skunk.data.Completion), which is an ADT that encodes responses from various commands. In this case our completion is simply the value `Completion.Set`.

Let's try a simple `DELETE` command.

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-b }

In this case the result will be `Delete(0)` since there are no countries named `xyzzy`.

## Parameterized Command

Now let's add a parameter to the command.

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-c }

Observe that we have interpolated a value called `varchar`, which has type `Encoder[String]`. This works the same way as with queries. See the previous chapter for more information about statement parameters.

The command above is an *extended command*.

@@@ note { title=Definition }
An *extended command* is a command with parameters, or a simple command that is executed via the extended query protocol.
@@@

Here is a command with two parameters.

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-d }

Note that `varchar` and `bpchar(3)` both encode Scala values of type `String`.

### Contramapping Commands

Similar to mapping the _output_ of a Query, we can `contramap` the _input_ to a command or query. Here we provide a function that turns an `Info` into a `String ~ String`.

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-e }

However in this case the mapping is entirely mechanical. Similar to `gmap` on query results, we can skip the boilerplate and `gcontramap` directly to an isomosphic case class.

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-f }



## Summary of Command Types

## Full Example

## Experiment

