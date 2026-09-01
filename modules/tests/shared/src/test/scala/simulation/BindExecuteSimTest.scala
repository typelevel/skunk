// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.syntax.all._
import skunk.codec.all._
import skunk.data.{ Completion, TransactionStatus }
import skunk.implicits._
import skunk.net.message._

/** Pins the frontend message sequence for executing a parameterized command, so a change to the
  * protocol flow shows up as a change to this script.
  *
  * `SimState.advance` queues backend messages only as far as the next `Expect`, and `receive` fails
  * on an empty queue, so a client that read `BindComplete` before sending `Sync` fails this script.
  * It cannot catch a client that pipelines more than the script; `ExchangeCountTest` measures that.
  */
class BindExecuteSimTest extends SimTest {

  private val sim: Simulator = {

    lazy val mainLoop: Simulator =
      send(ReadyForQuery(TransactionStatus.Idle)) *>
      flatExpect {

        // Parse and Describe are pipelined behind a single Flush, so the three replies are
        // produced together.
        case Parse(_, _, _) =>
          expect { case Describe(_, _) => } *>
          expect { case Flush           => } *>
          send(ParseComplete)                *>
          send(ParameterDescription(Nil))    *>
          send(NoData)                       *>
          bindExecuteLoop

        case other =>
          error(s"Unsupported: $other") *> mainLoop
      }

    // Bind, Execute and Sync go out together, so all three replies come back from one exchange. That
    // ReadyForQuery reports an idle transaction, so the portal is already gone and no Close follows.
    lazy val bindExecuteLoop: Simulator =
      flatExpect {
        case Bind(_, _, _) =>
          expect { case Execute(_, _) => }                  *>
          expect { case Sync          => }                  *>
          send(BindComplete)                                *>
          send(CommandComplete(Completion.Insert(1)))       *>
          send(ReadyForQuery(TransactionStatus.Idle))       *>
          bindExecuteLoop

        case other =>
          error(s"Unsupported: $other") *> mainLoop
      }

    flatExpect {
      case StartupMessage(_, _, _) => send(AuthenticationOk) *> mainLoop
    }
  }

  simTest("command: parse+describe, then bind+execute+sync in one exchange", sim) { s =>
    for {
      c <- s.execute(sql"insert into foo values ($int4)".command)(42)
      _ <- assert("completion", c == Completion.Insert(1))
    } yield "ok"
  }

}
