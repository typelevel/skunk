# Commands

This section explains how to construct and execute commands.

@@@ note { title=Definition }
A *command* is a SQL statement that does not return rows.
@@@

## Simple Command

Here we will issue a command that sets the session's random number seed.

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

In this case the result will be `Delete(0)` since there are no countries named `xyzzy`. But there should be.

## Parameterized Command

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-c }

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-d }

### Contramapping Commands

@@snip [Command.scala](/modules/docs/src/main/scala/tutorial/Command.scala) { #command-e }

### Contramapping Encoders

## Summary of Command Types

## Full Example

## Experiment

