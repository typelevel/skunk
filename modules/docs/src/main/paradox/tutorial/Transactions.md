# Transactions

Skunk has rich support for transactions (and error-handling, which are closely related).

See [§3.4](https://www.postgresql.org/docs/10/tutorial-transactions.html) in the PostgreSQL documentation for an introduction to transactions. The text that follows assumes you have a working knowledge of the information in that section.

@@@ warning
There is never more than one transaction in progress on a given session, and all operations on that session during the transaction's lifespan will take part in the transaction. It is recommended that sessions not be used concurrently in the presence of transactions.
@@@



## A Simple Transaction

... code example

### Transaction Finalization

If the `use` block exits normally there are three cases to consider, based on the session's transaction status on exit.

- **Active** means all went well and the transaction will be committed. This is the typical success case.
- **Error** means the user encountered and handled an error, but left the transaction in a failed state. In this case the transaction will be rolled back.
- **Idle** means the user terminated the transaction explicitly inside the block and there is nothing to be done.

If the block exits due to cancellation or an error and the session transaction status is **Active** or **Error** (the common failure case) then the transaction will be rolled back and any error will be re-raised.

Transaction finalization is summarized in the following matrix.

| Status | Normal Exit | Cancellation | Error Raised |
|--------------------|-------------|--------------|------|
| **Idle**   | — | — | re-raise |
| **Active** | commit | roll back | roll back, re-raise |
| **Error**  | roll back | roll back | roll back, re-raise |

## Savepoints

talk about savepoints

## Full Example

Here is a complete program listing that demonstrates our knowledge thus far.

```scala mdoc:reset
// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import cats.effect._
import cats.implicits._
import natchez.Trace.Implicits.noop
import skunk._
import skunk.codec.all._
import skunk.implicits._

object TransactionExample extends IOApp {

  // a source of sessions
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  // a resource that creates and drops a temporary table
  def withPetsTable(s: Session[IO]): Resource[IO, Unit] = {
    val alloc = s.execute(sql"CREATE TEMP TABLE pets (name varchar unique, age int2)".command).void
    val free  = s.execute(sql"DROP TABLE pets".command).void
    Resource.make(alloc)(_ => free)
  }

  // a data type
  case class Pet(name: String, age: Short)

  // command to insert a pet
  val insert: Command[Pet] =
    sql"INSERT INTO pets VALUES ($varchar, $int2)"
      .command
      .gcontramap[Pet]

  // query to select all pets
  def selectAll: Query[Void, Pet] =
    sql"SELECT name, age FROM pets"
      .query(varchar ~ int2)
      .gmap[Pet]

  // A resource that yields a session and prepared command
  def resource: Resource[IO, (Session[IO], PreparedCommand[IO, Pet])] =
    for {
      s  <- session
      _  <- withPetsTable(s)
      pc <- s.prepare(insert)
    } yield (s, pc)

  // a combinator to monitor transaction status while we run an action
  def monitorTransactionStatus[A](s: Session[IO])(action: IO[A]): IO[A] =
     s.transactionStatus
      .discrete
      .evalMap(s => IO(println(s"xa status: $s")))
      .compile
      .drain
      .start
      .flatMap(f => action.guarantee(f.cancel))

  def run(args: List[String]): IO[ExitCode] =
    resource.use { case (s, pc) =>
      monitorTransactionStatus(s) {
        for {
          _  <- pc.execute(Pet("Alice", 3))
          _  <- s.transaction.use { xa =>
            for {
              _  <- pc.execute(Pet("Bob", 42))
              sp <- xa.savepoint
              _  <- pc.execute(Pet("Bob", 21)).recoverWith {
                case SqlState.UniqueViolation(ex) =>
                  IO(println(s"Unique violation: ${ex.constraintName.getOrElse("<unknown>")}")) *>
                  xa.rollback(sp)
              }
              _  <- pc.execute(Pet("Steve", 9))
            } yield ()
          }
          ps <- s.execute(selectAll)
          _  <- ps.traverse(p => IO(println(p)))
        } yield ExitCode.Success
      }
    }

  }
```

Running this program yields the following.

```scala mdoc:passthrough
println("```")
TransactionExample.main(Array.empty)
println("```")
```

## Experiment

- PostgreSQL does not permit nested transactions. What happens if we try?