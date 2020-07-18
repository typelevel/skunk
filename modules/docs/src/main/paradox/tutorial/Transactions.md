# Transactions

Skunk has pretty good support for transactions (and error-handling, which are closely related).

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
import cats.effect._
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
      .gcontramap[Pet]

  // query to select all pets
  private val all: Query[Void, Pet] =
    sql"SELECT name, age FROM pets"
      .query(varchar ~ int2)
      .gmap[Pet]

  // construct a PetService, preparing our statement once on construction
  def fromSession[F[_]: Sync](s: Session[F]): Resource[F, PetService[F]] =
    s.prepare(insertOne).map { pc =>
      new PetService[F] {

        // Attempt to insert each pet in turn, in a single transaction, rolling back to a savepoint
        // if a unique violation is encountered. Note that a bulk insert with an ON CONFLICT clause
        // would be much more efficient, this is just for demonstration.
        def tryInsertAll(pets: List[Pet]): F[Unit] =
          s.transaction.use { xa =>
            pets.traverse_ { p =>
              for {
                _  <- Sync[F].delay(println(s"Trying to insert $p"))
                sp <- xa.savepoint
                _  <- pc.execute(p).recoverWith {
                        case SqlState.UniqueViolation(ex) =>
                          Sync[F].delay(println(s"Unique violation: ${ex.constraintName.getOrElse("<unknown>")}, rolling back...")) *>
                          xa.rollback(sp)
                      }
              } yield ()
            }
          }

        def selectAll: F[List[Pet]] = s.execute(all)
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
  def transactionStatusLogger[A](ss: Session[IO]): Resource[IO, Unit] = {
    val alloc: IO[Fiber[IO, Unit]] =
      ss.transactionStatus
        .discrete
        .changes
        .evalMap(s => IO(println(s"xa status: $s")))
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
      _  <- transactionStatusLogger(s)
      ps <- PetService.fromSession(s)
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
        _   <- all.traverse_(p => IO(println(p)))
      } yield ExitCode.Success
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

- PostgreSQL does not permit nested transactions. What happens if you try?
- What happens if you remove the error handler in `tryInsertAll`?
