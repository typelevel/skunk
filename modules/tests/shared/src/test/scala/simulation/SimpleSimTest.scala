// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.effect.IO
import cats.syntax.all._
import skunk.codec.all._
import skunk.data.{ Completion, TransactionStatus, Type }
import skunk.exception.PostgresErrorException
import skunk.implicits._
import skunk.net.message._
import skunk.util.Typer

class SimpleSimTest extends SimTest {

  // Simulated Postgres server.
  val sim: Simulator = {

    // we can handle simple queries of this form
    val pat = "select (\\d{1,5})".r

    // main query loop
    lazy val mainLoop: Simulator =
      send(ReadyForQuery(TransactionStatus.Idle)) *>
      flatExpect {
        case Query(pat(n)) =>
          send(RowDescription(List(RowDescription.Field("?column?", 0, 0, Typer.Static.oidForType(Type.int4).get, 4, 0, 0)))) *>
          send(RowData(List(Some(n))))                *>
          send(CommandComplete(Completion.Select(1))) *>
          mainLoop
        case other =>
          error(s"Unsupported: $other") *>
          mainLoop
      }

    // entry point
    flatExpect {
      case StartupMessage(_, _, _) => send(AuthenticationOk) *> mainLoop
    }

  }

  simTest("simple query", sim) { s =>
    for {
      ns <- List(1,2,345).traverse(n => s.unique(sql"select #${n.toString}".query(int4)))
      _  <- assertEqual("nums", ns, List(1,2,345))
    } yield ("ok")
  }

  simTest("sim error", sim) { s =>
    s.prepare(sql"select $int4".query(int4)).flatMap(_ => IO.unit)
     .assertFailsWith[PostgresErrorException]
     .as("ok")
  }

}
