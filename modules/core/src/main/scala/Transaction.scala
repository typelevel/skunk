package skunk

import cats._
import cats.effect.Resource
import cats.effect.ExitCase
import cats.implicits._
import skunk.data.Identifier
import skunk.implicits._
import skunk.data.Completion

trait Transaction[F[_]] {

  type Savepoint

  def savepoint(name: Identifier): F[Savepoint]
  def rollback(savepoint: Savepoint): F[Completion]
  def rollback: F[Completion]
  def commit: F[Completion]

}

object Transaction {

  def fromSession[F[_]: MonadError[?[_], Throwable]](
    s: Session[F],
    // also need to take an isolation level
  ): Resource[F, Transaction[F]] = {

    val acquire: F[Transaction[F]] =
      // we're not checking transaction status here because PG errors should be sufficient but we'll
      // need to write some tests to be sure
      s.execute(sql"BEGIN".command).map { _ =>
        new Transaction[F] {
          type Savepoint = Identifier
          def commit: F[Completion] = s.execute(sql"COMMIT".command)
          def rollback: F[Completion] = s.execute(sql"ROLLBACK".command)
          def rollback(savepoint: Savepoint): F[Completion] = s.execute(sql"ROLLBACK #${savepoint.value}".command)
          def savepoint(name: Identifier): F[Savepoint] = s.execute(sql"SAVEPOINT #${name.value}".command).as(name)
        }
      }

    val release: (Transaction[F], ExitCase[Throwable]) => F[Unit] = {
      // similarly we're not checking transaction status here either. will need to write tests and
      // see how this works out.
      case (_, ExitCase.Canceled)  => s.execute(sql"ROLLBACK".command).void
      case (_, ExitCase.Completed) => s.execute(sql"COMMIT".command).void
      case (_, ExitCase.Error(t))  => s.execute(sql"ROLLBACK".command) *> t.raiseError[F, Unit]
    }

    Resource.makeCase(acquire)(release)

  }

}

