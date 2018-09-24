package skunk
package proto

import cats.Monad
import cats.effect.concurrent.Semaphore
import cats.implicits._
import skunk.proto.message._

object SimpleQuery {

  def simple[F[_]: Monad](
    ams:   ActiveMessageSocket[F],
    sem:   Semaphore[F],
    query: Query,
  ): F[(RowDescription, List[RowData])] = {

    def unroll(accum: List[RowData]): F[List[RowData]] =
      ams.receive.flatMap {
        case rd @ RowData(_)         => unroll(rd :: accum)
        case      CommandComplete(_) => accum.reverse.pure[F]
      }

    sem.withPermit {
      for {
        _  <- ams.send(query)
        rd <- ams.expect { case rd @ RowDescription(_) => rd }
        rs <- unroll(Nil)
        _  <- ams.expect { case ReadyForQuery(_) => }
      } yield (rd, rs)
    }

  }


}