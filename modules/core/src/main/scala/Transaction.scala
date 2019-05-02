package skunk

import cats._
import cats.effect.Resource
import cats.effect.ExitCase
import cats.implicits._
import skunk.data.Identifier
import skunk.implicits._
import skunk.data.Completion
import skunk.data.TransactionStatus._


// Need exceptions for TransctionAlreadyInProgress and NoCurrentTransaction

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

    def assertIdle: F[Unit] = ???
    def assertActive: F[Unit] = ???

    val acquire: F[Transaction[F]] =
      assertIdle *>
      s.execute(sql"BEGIN".command).map { _ =>
        new Transaction[F] {
          type Savepoint = Identifier

          // TODO: check status for all of these
          def commit: F[Completion] =
            assertActive *> s.execute(sql"COMMIT".command)

          def rollback: F[Completion] =
            assertActive *> s.execute(sql"ROLLBACK".command)

          def rollback(savepoint: Savepoint): F[Completion] =
            assertActive *> s.execute(sql"ROLLBACK #${savepoint.value}".command)

          def savepoint(name: Identifier): F[Savepoint] =
            assertActive *> s.execute(sql"SAVEPOINT #${name.value}".command).as(name)

        }
      }


    val release: (Transaction[F], ExitCase[Throwable]) => F[Unit] = (xa, ec) =>
      s.transactionStatus.get.flatMap {
        case Idle              => ().pure[F]
        case FailedTransaction => s.execute(sql"ROLLBACK".command).void
        case ActiveTransaction =>
          (xa, ec) match {
            case (_, ExitCase.Canceled)  => s.execute(sql"ROLLBACK".command).void
            case (_, ExitCase.Completed) => s.execute(sql"COMMIT".command).void
            case (_, ExitCase.Error(t))  => s.execute(sql"ROLLBACK".command) *> t.raiseError[F, Unit]
          }
    }

    Resource.makeCase(acquire)(release)

  }

}

