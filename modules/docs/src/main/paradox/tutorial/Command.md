# Commands

This section explains how to construct and execute commands.

@@@ note { title=Definition }
A *command* is a SQL statement that does not return rows.
@@@

## Simple Command

...

@@@ note { title=Definition }
A *simple command* is a command with no parameters.
@@@

The same [protocol](https://www.postgresql.org/docs/10/protocol-flow.html#id-1.10.5.7.4) that executes simple queries also executes of simple commands. Such commands can be passed directly to @scaladoc[Session.execute](skunk.Session#execute).

...

## Paramererized Command

