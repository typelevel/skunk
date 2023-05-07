```scala mdoc:invisible
import cats.effect._
import cats.implicits._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop
import fs2.Stream
val s: Session[IO] = null
```
# Transactions

Skunk has pretty good support for transactions (and error-handling, which are closely related).

See [§3.4](https://www.postgresql.org/docs/10/tutorial-transactions.html) in the PostgreSQL documentation for an introduction to transactions. The text that follows assumes you have a working knowledge of the information in that section.

## Transaction Status

Postgres connections are always in one of three transaction states.

| Status | Comment |
|-------|---------|
| **Idle**    | There is no transaction in progress. |
| **Active**  | A transaction is in progress and can proceed. |
| **Error**   | An error has occurred. The transaction must be rolled back to a savepoint to continue; or must be rolled back entirely to terminate. |

Because transaction status is a property of the session itself, all operations on that session during a transaction's lifespan will take part in the transaction. For this reason it is recommended that sessions not be used concurrently in the presence of transactions. See the chapter on @ref:[Concurrency](../reference/Concurrency.md) for more details.

`Session`'s transaction status is available via its `transactionStatus` member (an fs2 `Signal`). The example below takes advantage of this facility.

## Basic Usage Pattern

Each session has a `transaction` resource that you can `use` to execute an action within a transaction.

```scala
// assume s:Session[IO]
s.transaction.use { xa =>
  // transactional action here
}
```

The basic usage pattern is as follows.

- Wrap an action with `transaction.use` to create a new action that runs transactionally.
- If the action completes normally, the transaction will be committed.
- If the action is cancelled (via `Fiber.cancel`) the transaction will be rolled back.
- If the action raises an exception, the transaction will be rolled back and the exception will be re-raised.

The `xa` parameter provided by `use` is a reference to the transaction itself, which can be ignored for the basic usage pattern.

@@@warning
If you perform non-database actions wihin a transaction (such as writing to a file or making an http post) these actions cannot be rolled back if the transaction fails. Best practice is to factor these things out and only perform them if the `use` block completes normally.
@@@

## Advanced Usage Pattern

The advanced pattern uses the transaction reference `xa`, which provides the following actions:

| Action | Meaning |
|---------|----|
| `xa.status` | Yields the session's current `TransactionStatus`. |
| `xa.commit` | Commits the transaction. |
| `xa.rollback` | Rolls back the transaction in its entirety. |
| `xa.savepoint` | Creates an `xa.Savepoint`. |
| `xa.rollback(sp)` | Rolls back to a previously created `xa.Savepoint`, allowing the transaction to continue following an error. |

Transaction finalization is more complex in the advanced case because you are able to commit and roll back explicitly. For this reason the finalizer consults the transaction status as well as the action's exit case to figure out what to do,

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

## Transaction Characteristics

Transaction characteristics can be changed by using the `transaction[A](isolationLevel: TransactionIsolationLevel, accessMode: TransactionAccessMode)` method to create the transaction resource.
More details about the available isolation levels and access modes can be found [here](https://www.postgresql.org/docs/10/transaction-iso.html) and [here](https://www.postgresql.org/docs/9.3/sql-set-transaction.html).

There is an alternative way to switch to a non-default isolation level and access mode by executing the [`SET TRANSACTION`](https://www.postgresql.org/docs/10/sql-set-transaction.html) command as the first operation inside your transaction.

## Full Example

Here is a complete program listing that demonstrates our knowledge thus far.

```scala mdoc:reset
// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

import cats.effect._
import cats.effect.std.Console
import cats.implicits._
import natchez.Trace.Implicits.noop
import skunk._
import skunk.codec.all._
import skunk.implicits._

// a data type
case class Pet(name: String, age: Short)

// a service interface
trait PetService[F[_]] {
  def tryInsertAll(pets: List[Pet]): F[Unit]
  def selectAll: F[List[Pet]]
}

// a companion with a constructor
object PetService {

  // command to insert a pet
  private val insertOne: Command[Pet] =
    sql"INSERT INTO pets VALUES ($varchar, $int2)"
      .command
      .to[Pet]

  // query to select all pets
  private val all: Query[Void, Pet] =
    sql"SELECT name, age FROM pets"
      .query(varchar *: int2)
      .to[Pet]

  // construct a PetService, preparing our statement once on construction
  def fromSession(s: Session[IO]): IO[PetService[IO]] =
    s.prepare(insertOne).map { pc =>
      new PetService[IO] {

        // Attempt to insert all pets, in a single transaction, handling each in turn and rolling
        // back to a savepoint if a unique violation is encountered. Note that a bulk insert with an
        // ON CONFLICT clause would be much more efficient, this is just for demonstration.
        def tryInsertAll(pets: List[Pet]): IO[Unit] =
          s.transaction.use { xa =>
            pets.traverse_ { p =>
              for {
                _  <- IO.println(s"Trying to insert $p")
                sp <- xa.savepoint
                _  <- pc.execute(p).recoverWith {
                        case SqlState.UniqueViolation(ex) =>
                         IO.println(s"Unique violation: ${ex.constraintName.getOrElse("<unknown>")}, rolling back...") *>
                          xa.rollback(sp)
                      }
              } yield ()
            }
          }

        def selectAll: IO[List[Pet]] = s.execute(all)
      }
    }

}

object TransactionExample extends IOApp {

  // a source of sessions
  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana")
    )

  // a resource that creates and drops a temporary table
  def withPetsTable(s: Session[IO]): Resource[IO, Unit] = {
    val alloc = s.execute(sql"CREATE TEMP TABLE pets (name varchar unique, age int2)".command).void
    val free  = s.execute(sql"DROP TABLE pets".command).void
    Resource.make(alloc)(_ => free)
  }

  // We can monitor the changing transaction status by tapping into the provided `fs2.Signal`
  def withTransactionStatusLogger[A](ss: Session[IO]): Resource[IO, Unit] = {
    val alloc: IO[Fiber[IO, Throwable, Unit]] =
      ss.transactionStatus
        .discrete
        .changes
        .evalMap(s => IO.println(s"xa status: $s"))
        .compile
        .drain
        .start
    Resource.make(alloc)(_.cancel).void
  }

  // A resource that puts it all together.
  val resource: Resource[IO, PetService[IO]] =
    for {
      s  <- session
      _  <- withPetsTable(s)
      _  <- withTransactionStatusLogger(s)
      ps <- Resource.eval(PetService.fromSession(s))
    } yield ps

  // Some test data
  val pets = List(
    Pet("Alice", 3),
    Pet("Bob",  42),
    Pet("Bob",  21),
    Pet("Steve", 9)
  )

  // Our entry point
  def run(args: List[String]): IO[ExitCode] =
    resource.use { ps =>
      for {
        _   <- ps.tryInsertAll(pets)
        all <- ps.selectAll
        _   <- all.traverse_(p => IO.println(p))
      } yield ExitCode.Success
    }

}
```

Running this program yields the following.

```scala mdoc:passthrough
println("```")
import skunk.mdoc._
TransactionExample.run(Nil).unsafeRunSyncWithRedirect()
println("```")
```

## Experiment

- PostgreSQL does not permit nested transactions. What happens if you try?
- What happens if you remove the error handler in `tryInsertAll`?
