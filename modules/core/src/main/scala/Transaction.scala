// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import cats.effect.Resource
import cats.effect.ExitCase
import cats.effect.ExitCase._
import cats.implicits._
import skunk.implicits._
import skunk.data.Completion
import skunk.data.TransactionStatus._
import skunk.util.Origin
import skunk.util.CallSite
import skunk.exception.SkunkException
import skunk.util.Namer
import skunk.data.TransactionStatus
import skunk.data.TransactionIsolationLevel
import skunk.data.TransactionAccessMode

/**
 * Control methods for use within a `transaction` block. An instance is provided when you call
 * `Session.transaction(...).use`.
 * @see Session#transaction for information on default commit/rollback behavior
 *
 * @groupname XA Transaction Control
 */
trait Transaction[F[_]] { outer =>

  /** Existential type for savepoints within this transaction block. */
  type Savepoint

  /**
   * Current transaction status. It is not usually necessary to check because transactions will be
   * committed or rolled back automatically, but if you are committing manually and your logic is
   * sufficiently complex it may be helpful.
   * @group XA
   */
  def status: F[TransactionStatus]

  /**
   * Create a `Savepoint`, to which you can later roll back.
   * @group XA
   */
  def savepoint(implicit o: Origin): F[Savepoint]

  /**
   * Roll back to the specified `Savepoint`, leaving the transaction active at that point.
   * @group XA
   */
  def rollback(savepoint: Savepoint)(implicit o: Origin): F[Completion]

  /**
   * Terminate the transaction by rolling back. This is normally not necessary because a transaction
   * will be rolled back automatically when the block exits abnormally.
   * @see Session#transaction for information on default commit/rollback behavior
   * @group XA
   */
  def rollback(implicit o: Origin): F[Completion]

  /**
   * Terminate the transaction by committing early. This is normally not necessary because a
   * transaction will be committed automatically if the block exits successfully.
   * @see Session#transaction for information on default commit/rollback behavior
   * @group XA
   */
  def commit(implicit o: Origin): F[Completion]

  /**
   * Transform this `Transaction` by a given `FunctionK`.
   * @group Transformations
   */
  def mapK[G[_]](fk: F ~> G): Transaction[G] =
    new Transaction[G] {
      override type Savepoint = outer.Savepoint
      override def commit(implicit o: Origin): G[Completion] = fk(outer.commit)
      override def rollback(implicit o: Origin): G[Completion] = fk(outer.rollback)
      override def rollback(savepoint: Savepoint)(implicit o: Origin): G[Completion] = fk(outer.rollback(savepoint))
      override def savepoint(implicit o: Origin): G[Savepoint] = fk(outer.savepoint)
      override def status: G[TransactionStatus] = fk(outer.status)
    }

}

object Transaction {

  def fromSession[F[_]: MonadError[?[_], Throwable]](
    s: Session[F],
    n: Namer[F],
    // o: Origin // origin of the call to .begin
    i: TransactionIsolationLevel,
    a: TransactionAccessMode
  ): Resource[F, Transaction[F]] = {

    def assertIdle(cs: CallSite): F[Unit] =
      s.transactionStatus.get.flatMap {
        case Idle              => ().pure[F]
        case Active =>
          new SkunkException(
            sql      = None,
            message  = "Nested transactions are not allowed.",
            hint     = Some("You must roll back or commit the current transaction before starting a new one."),
            callSite = Some(cs)
          ).raiseError[F, Unit]
        case Failed =>
          new SkunkException(
            sql      = None,
            message  = "Nested transactions are not allowed.",
            hint     = Some("You must roll back the current (failed) transaction before starting a new one."),
            callSite = Some(cs)
          ).raiseError[F, Unit]
      }

    def assertActive(cs: CallSite): F[Unit] =
      s.transactionStatus.get.flatMap {
        case Idle   =>
          new SkunkException(
            sql      = None,
            message  = "No transaction.",
            hint     = Some("The transaction has already been committed or rolled back."),
            callSite = Some(cs)
          ).raiseError[F, Unit]
        case Active => ().pure[F]
        case Failed =>
          new SkunkException(
            sql      = None,
            message  = "Transaction has failed.",
            hint     = Some("""
              |The active transaction has failed and needs to be rolled back (either entirely or to
              |a prior savepoint) before you can continue. The most common explanation is that
              |Postgres raised an error earlier in the transaction and you handled it in your
              |application code, but you forgot to roll back.
            """.trim.stripMargin.replace('\n', ' ')),
            callSite = Some(cs)
          ).raiseError[F, Unit]
      }

    def assertActiveOrError(cs: CallSite): F[Unit] =
      cs.pure[F].void

    def doRollback: F[Completion] =
      s.execute(internal"ROLLBACK".command)

    def doCommit: F[Completion] =
      s.execute(internal"COMMIT".command)      

    val acquire: F[Transaction[F]] =
      assertIdle(CallSite("begin", Origin.unknown)) *>
      s.execute(
        internal"""BEGIN
                   ISOLATION LEVEL ${TransactionIsolationLevel.toLiteral(i)}
                    ${TransactionAccessMode.toLiteral(a)}""".command
      ).map { _ =>
        new Transaction[F] {

          override type Savepoint = String

          override def status: F[TransactionStatus] =
            s.transactionStatus.get

          override def commit(implicit o: Origin): F[Completion] =
            assertActive(o.toCallSite("commit")) *>
            doCommit

          override def rollback(implicit o: Origin): F[Completion] =
            assertActiveOrError(o.toCallSite("rollback")) *>
            doRollback

          override def rollback(savepoint: Savepoint)(implicit o: Origin): F[Completion] =
            assertActiveOrError(o.toCallSite("savepoint")) *>
            s.execute(internal"ROLLBACK TO ${savepoint}".command)

          override def savepoint(implicit o: Origin): F[Savepoint] =
            for {
              _ <- assertActive(o.toCallSite("savepoint"))
              i <- n.nextName("savepoint")
              _ <- s.execute(internal"SAVEPOINT $i".command)
            } yield i

        }
      }

    val release: (Transaction[F], ExitCase[Throwable]) => F[Unit] = (_, ec) =>
      s.transactionStatus.get.flatMap {
        case Idle              =>
          // This means the user committed manually, so there's nothing to do
          ().pure[F]
        case Failed =>
          ec match {
            // This is the normal failure case
            case Error(t)  => doRollback *> t.raiseError[F, Unit]
            // This is possible if you swallow an error
            case Completed => doRollback.void
            // This is possible if you swallow an error and the someone cancels the fiber
            case Canceled  => doRollback.void
          }
        case Active =>
          ec match {
            // This is the normal success case
            case Completed => doCommit.void
            // If someone cancels the fiber we roll back
            case Canceled  => doRollback.void
            // If an error escapes we roll back
            case Error(t)  => doRollback *> t.raiseError[F, Unit]
          }
      }

    Resource.makeCase(acquire)(release)

  }

}

