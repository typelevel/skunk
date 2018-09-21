package skunk
package proto

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.Stream
import skunk.proto.message._

object SimpleQuery {

  /**
   * Stream that reads rows from `ams` as long as `ref` is false, setting `ref` to to true and
   * terminating when `CommandComplete` is reached.
   */
  private def readRows[F[_]: Sync, A](
    ams: ActiveMessageSocket[F],
    ref: Ref[F, Boolean],
    f:   RowData => A
  ): Stream[F, A] =
    Stream.eval(ref.get).flatMap {
      case true  => Stream.empty
      case false =>
        Stream.eval(ams.receive).flatMap {
          case rd @ RowData(_)         => Stream.emit(f(rd)) ++ readRows(ams, ref, f)
          case      CommandComplete(_) => Stream.eval_(ref.set(true))
        }
    }

  /**
   * Logically equivalent to `readRows`, but the finalizer runs `readRows` to completion, ensuring
   * that all rows have been consumed, then consumes the `ReadyForQuery` prompt.
   */
  private def mkStream[F[_]: Sync, A](
    ams: ActiveMessageSocket[F],
    ref: Ref[F, Boolean],
    f:   RowData => A
  ): Stream[F, A] = {
    val s = readRows(ams, ref, f)
    s.onFinalize(s.compile.drain *> ams.expect { case ReadyForQuery(_) => ().pure[F] })
  }

  /**
   * A stream that, when run, will execute the given query and emit all rows, processed by `f`,
   * guaranteeing that any unconsumed rows will be drained and discarded.
   */
  def query[F[_]: Sync, A](
    ams:   ActiveMessageSocket[F],
    query: Query,
    f:     RowDescription => RowData => A
  ): Stream[F, A] =
    for {
      ref <- Stream.eval(Ref[F].of(false))
      rd  <- Stream.eval(ams.send(query) *> ams.expect { case rd @ RowDescription(_) => rd.pure[F] })
      a   <- mkStream(ams, ref, f(rd))
    } yield a

}