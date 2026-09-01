// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.effect.IO
import cats.syntax.all._
import ffstest.FTest
import org.typelevel.otel4s.metrics.Histogram
import org.typelevel.otel4s.trace.Tracer
import skunk.{ RedactionStrategy, Session, TypingStrategy }
import skunk.codec.all._
import skunk.data.{ Completion, TransactionStatus, Type }
import skunk.implicits._
import skunk.net.Protocol
import skunk.net.message._
import skunk.util.{ Namer, Typer }

/** Counts protocol exchanges per operation kind: how many times the client stops writing and waits
  * for the server, for each way of running a statement. Needs no database -- the counting decorator
  * sits between a real `Session` and a simulated backend.
  *
  * The asserted counts are the current ones, so a change that removes an exchange fails here and the
  * diff to the expected number is the claim it makes.
  */
class ExchangeCountTest extends FTest with SimMessageSocket.DSL {

  implicit val tracer: Tracer[IO] = Tracer.noop

  private val int4Column: RowDescription.Field =
    RowDescription.Field("?column?", 0, 0, Typer.Static.oidForType(Type.int4).get, 4, 0, 0)

  private def row(n: Int): RowData =
    RowData(List(Some(n.toString)))

  /** A simulated backend that answers whatever the client asks rather than following a fixed script.
    * Replies are produced per frontend message, which is enough for counting.
    *
    * @param columns `None` for a statement returning no rows (`NoData`), `Some` for a query.
    * @param rows what a portal yields; `Execute` honours `maxRows` and suspends when more remain.
    * @param completion the `CommandComplete` payload.
    * @param simpleCount how many `CommandComplete`s a simple query produces, for multi-statement.
    */
  private def backend(
    columns:     Option[List[RowDescription.Field]],
    rows:        List[RowData],
    completion:  Completion,
    simpleCount: Int
  ): Simulator = {

    def loop(remaining: List[RowData]): Simulator =
      flatExpect {

        case Parse(_, _, _) =>
          send(ParseComplete) *> loop(remaining)

        case Describe(_, _) =>
          send(ParameterDescription(Nil))                     *>
          columns.fold(send(NoData))(fs => send(RowDescription(fs))) *>
          loop(remaining)

        // A fresh Bind is a fresh portal, so the unread rows reset.
        case Bind(_, _, _) =>
          send(BindComplete) *> loop(rows)

        case Execute(_, maxRows) =>
          if (maxRows > 0 && remaining.length > maxRows) {
            val (chunk, rest) = remaining.splitAt(maxRows)
            chunk.traverse_(send) *> send(PortalSuspended) *> loop(rest)
          } else {
            remaining.traverse_(send) *> send(CommandComplete(completion)) *> loop(Nil)
          }

        case Close(_, _) =>
          send(CloseComplete) *> loop(remaining)

        case Sync =>
          send(ReadyForQuery(TransactionStatus.Idle)) *> loop(remaining)

        case Flush =>
          loop(remaining)

        case Query(_) =>
          columns.traverse_(fs => send(RowDescription(fs)))                *>
          rows.traverse_(send)                                             *>
          List.fill(simpleCount)(CommandComplete(completion)).traverse_(send) *>
          send(ReadyForQuery(TransactionStatus.Idle))                      *>
          loop(rows)

        case other =>
          error(s"Unsupported: $other") *> loop(remaining)

      }

    flatExpect {
      case StartupMessage(_, _, _) =>
        send(AuthenticationOk)                       *>
        send(ReadyForQuery(TransactionStatus.Idle))  *>
        loop(rows)
    }

  }

  private def session(sim: Simulator): IO[(Session[IO], ExchangeCounter)] =
    for {
      raw <- SimMessageSocket(sim)
      ctr <- ExchangeCounter(raw)
      nam <- Namer[IO]
      dc  <- skunk.net.protocol.Describe.Cache.empty[IO](1024, 1024)
      pc  <- skunk.net.protocol.Parse.Cache.empty[IO](1024)
      pro <- Protocol.fromMessageSocket(ctr, nam, dc, pc, RedactionStrategy.None, Histogram.noop[IO, Double])
      _   <- pro.startup("Bob", "db", None, Session.DefaultConnectionParameters)
      ses <- Session.fromProtocol(pro, nam, TypingStrategy.BuiltinsOnly, RedactionStrategy.None)
    } yield (ses, ctr)

  /** Run `f` and report what it cost. Unless `warm` is false, `f` runs once first to warm the parse
    * and describe caches, and only the second run is counted.
    */
  private def measure[A](
    columns:     Option[List[RowDescription.Field]] = None,
    rows:        List[RowData]                      = Nil,
    completion:  Completion                         = Completion.Insert(1),
    simpleCount: Int                                = 1,
    warm:        Boolean                            = true
  )(f: Session[IO] => IO[A]): IO[ExchangeCounts] =
    session(backend(columns, rows, completion, simpleCount)).flatMap { case (s, ctr) =>
      f(s).void.whenA(warm) *> ctr.reset *> f(s) *> ctr.counts
    }

  private def render(rows: List[(String, ExchangeCounts, Int)]): String = {
    val w = rows.map(_._1.length).max
    val head = s"| ${"Operation".padTo(w, ' ')} | Exchanges | Writes | Bytes |"
    val rule = s"|-${"-" * w}-|-----------|--------|-------|"
    val body = rows.map { case (label, c, _) =>
      f"| ${label.padTo(w, ' ')} | ${c.exchanges}%9d | ${c.writes}%6d | ${c.bytesSent}%5d |"
    }
    (head :: rule :: body).mkString("\n")
  }

  test("exchange counts by operation kind") {

    val cmd   = sql"insert into foo values ($int4)".command
    val qry   = sql"select $int4".query(int4)
    val cmd0  = sql"insert into foo values (1)".command
    val qry0  = sql"select 1".query(int4)
    val multi = sql"insert into foo values (1); insert into foo values (2)".command

    val oneCol   = Some(List(int4Column))
    val oneRow   = List(row(1))
    val threeRow = List(row(1), row(2), row(3))

    for {
      cmdWarm  <- measure()(_.execute(cmd)(42))
      cmdCold  <- measure(warm = false)(_.execute(cmd)(42))
      qAll     <- measure(oneCol, threeRow, Completion.Select(3))(_.execute(qry)(42))
      qUnique  <- measure(oneCol, oneRow, Completion.Select(1))(_.unique(qry)(42))
      qOption  <- measure(oneCol, oneRow, Completion.Select(1))(_.option(qry)(42))
      qStream  <- measure(oneCol, threeRow, Completion.Select(3))(_.stream(qry)(42, 2).compile.toList)
      simpCmd  <- measure()(_.execute(cmd0))
      simpQry  <- measure(oneCol, oneRow, Completion.Select(1))(_.execute(qry0))
      discard  <- measure(simpleCount = 2)(_.executeDiscard(multi))
      begin    <- measure(completion = Completion.Begin)(_.execute(sql"begin".command))

      table = List(
        ("parameterized command, warm",     cmdWarm, 2),
        ("parameterized command, cold",     cmdCold, 3),
        ("parameterized query, all rows",   qAll,    4),
        ("parameterized query, unique",     qUnique, 3),
        ("parameterized query, option",     qOption, 3),
        ("streaming query, 3 rows / 2",     qStream, 4),
        ("parameterless command (simple)",  simpCmd, 1),
        ("parameterless query (simple)",    simpQry, 1),
        ("executeDiscard (simple, 2 stmt)", discard, 1),
        ("BEGIN (simple)",                  begin,   1)
      )

      _ <- IO.println("\n" + render(table) + "\n")
      _ <- table.traverse_ { case (label, c, expected) =>
             assertEqual(s"exchanges for $label", c.exchanges, expected)
           }
    } yield ()

  }


  // The counter observes ReadyForQuery on its way past and reports the status it carries, which is
  // what lets a harness scenario touch Session.transaction at all. The signal starts Idle, so
  // reading Active back can only have come from the wire. Self-contained rather than built on
  // `backend`, since what it needs is one specific reply.
  test("transaction status is tracked, not stubbed") {
    lazy val loop: Simulator = flatExpect {
      case Query(_) =>
        send(CommandComplete(Completion.Begin))       *>
        send(ReadyForQuery(TransactionStatus.Active)) *>
        loop
      case other =>
        error(s"Unsupported: $other") *> loop
    }
    val sim: Simulator = flatExpect {
      case StartupMessage(_, _, _) =>
        send(AuthenticationOk) *> send(ReadyForQuery(TransactionStatus.Idle)) *> loop
    }
    session(sim).flatMap { case (s, _) =>
      for {
        before <- s.transactionStatus.get
        _      <- assertEqual("starts idle", before, TransactionStatus.Idle)
        _      <- s.execute(sql"begin".command)
        after  <- s.transactionStatus.get
        _      <- assertEqual("status came from the wire", after, TransactionStatus.Active)
      } yield ()
    }
  }

}
