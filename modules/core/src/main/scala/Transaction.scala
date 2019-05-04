package skunk

import cats._
import cats.effect.Resource
import cats.effect.ExitCase
import cats.implicits._
import skunk.data.Identifier
import skunk.implicits._
import skunk.data.Completion
import skunk.data.TransactionStatus._
import skunk.util.Origin
import skunk.util.CallSite


// Need exceptions for TransctionAlreadyInProgress and NoCurrentTransaction

trait Transaction[F[_]] {

  type Savepoint

  def savepoint(name: Identifier)(implicit o: Origin): F[Savepoint]
  def rollback(savepoint: Savepoint)(implicit o: Origin): F[Completion]
  def rollback(implicit o: Origin): F[Completion]
  def commit(implicit o: Origin): F[Completion]

}

object Transaction {

  def fromSession[F[_]: MonadError[?[_], Throwable]](
    s: Session[F],
    // also need to take an isolation level
  ): Resource[F, Transaction[F]] = {

    def assertIdle(cs: CallSite): F[Unit] = ???

    def assertActive(cs: CallSite): F[Unit] = ???

    val acquire: F[Transaction[F]] =
      assertIdle(CallSite("Transaction.use", Origin.unknown)) *> // this is lame
      s.execute(sql"BEGIN".command).map { _ =>
        new Transaction[F] {
          type Savepoint = Identifier

          // TODO: check status for all of these
          def commit(implicit o: Origin): F[Completion] =
            assertActive(o.toCallSite("commit")) *> s.execute(sql"COMMIT".command)

          def rollback(implicit o: Origin): F[Completion] =
            assertActive(o.toCallSite("rollback")) *> s.execute(sql"ROLLBACK".command)

          def rollback(savepoint: Savepoint)(implicit o: Origin): F[Completion] =
            assertActive(o.toCallSite("savepoint")) *> s.execute(sql"ROLLBACK #${savepoint.value}".command)

          def savepoint(name: Identifier)(implicit o: Origin): F[Savepoint] =
            assertActive(o.toCallSite("savepoint")) *> s.execute(sql"SAVEPOINT #${name.value}".command).as(name)

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

