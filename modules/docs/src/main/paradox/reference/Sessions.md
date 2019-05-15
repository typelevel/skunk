# Sessions


## Session Parameters

The following Postgres session parameters affect data serialization and are specified by Skunk during startup negotiation. Changing them via a `SET` command will result in undefined behavior.

@@snip [StartupMessage.scala](/modules/core/src/main/scala/net/message/StartupMessage.scala) { #config }

Future versions of Skunk may be more flexible in this regard, but for now your application needs to be ok with these defaults.