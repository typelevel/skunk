package skunk

import cats.effect.Concurrent
import cats.effect.concurrent.Semaphore
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Signal
import skunk.proto.message._
import skunk.dsl.Fragment
import skunk.util.Namer
import shapeless.HList

trait Session[F[_]] {
  def parameters: Signal[F, Map[String, String]]
  def query[A](sql: String): F[(RowDescription, List[RowData])]
  def startup(user: String, database: String): F[Unit]
  def transactionStatus: Signal[F, ReadyForQuery.Status]
  def listen(channel: String, maxQueued: Int): Stream[F, NotificationResponse]
  def notify(channel: String, message: String): F[Unit]
  def parse[H <: HList](frag: Fragment[H]): F[Unit]
}

object Session {

  def fromActiveMessageSocket[F[_]: Concurrent](ams: ActiveMessageSocket[F]): F[Session[F]] =
    for {
      nam <- Namer[F]("statement")
      sem <- Semaphore[F](1)
    } yield new Session[F] {

        def parameters: Signal[F, Map[String, String]] =
          ams.parameters

        def query[A](sql: String): F[(RowDescription, List[RowData])] =
          sem.withPermit {

            def unroll(accum: List[RowData]): F[List[RowData]] =
              ams.receive.flatMap {
                case rd @ RowData(_)         => unroll(rd :: accum)
                case      CommandComplete(_) => accum.reverse.pure[F]
              }

            for {
              _  <- ams.send(Query(sql))
              rd <- ams.expect { case rd @ RowDescription(_) => rd }
              rs <- unroll(Nil)
              _  <- ams.expect { case ReadyForQuery(_) => }
            } yield (rd, rs)

          }

        def startup(user: String, database: String) =
          sem.withPermit {
            for {
              _ <- ams.send(StartupMessage(user, database))
              _ <- ams.expect { case AuthenticationOk => }
              _ <- ams.expect { case ReadyForQuery(_) => }
            } yield ()
          }

        def transactionStatus: Signal[F, ReadyForQuery.Status] =
          ams.transactionStatus

        def listen(channel: String, maxQueued: Int): Stream[F, NotificationResponse] =
          Stream.eval {
            sem.withPermit {
              for {
                _ <- ams.send(Query(s"LISTEN $channel"))
                _ <- ams.expect { case CommandComplete("LISTEN") => }
                _ <- ams.expect { case ReadyForQuery(_) => }
              } yield ()
            }
          } >> ams.notifications(maxQueued).filter(_.channel === channel)

        def notify(channel: String, message: String): F[Unit] =
          sem.withPermit {
            for {
              _ <- ams.send(Query(s"NOTIFY $channel, '$message'")) // TODO: escape
              _ <- ams.expect { case CommandComplete("NOTIFY") => }
              _ <- ams.expect { case ReadyForQuery(_) => }
            } yield ()
          }

        def parse[H <: HList](frag: Fragment[H]): F[Unit] =
          sem.withPermit {
            for {
              n <- nam.nextName
              _ <- ams.send(Parse(n, frag.sql, frag.encoder.oids.toList)) // blergh
              _ <- ams.send(Sync)
              _ <- ams.expect { case ParseComplete => }
              _ <- ams.expect { case ReadyForQuery(_) => }
            } yield ()
          }

      }

}