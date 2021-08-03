// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

// Copyright (c) 2018-2020 by Rob Norris// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package simulation

import cats.syntax.all._
import skunk.codec.all._
import skunk.implicits._
import skunk.net.message._
import skunk.data.TransactionStatus
import skunk.exception.ProtocolError

abstract class ProtocolErrorSimTest extends SimTest {

  val sim: Simulator =
    flatExpect {
      case StartupMessage(_, _, _) =>
        send(AuthenticationOk)                      *>
        send(ReadyForQuery(TransactionStatus.Idle)) *>
        flatExpect {
          case Query(_) =>
            send(RowData(Nil)) *> // illegal, RowDescription expected, will cause protocol error
            halt
        }
    }

  simTest("protocol error", sim) { s =>
    s.unique(sql"select 3".query(int4))
      .assertFailsWith[ProtocolError]
      .as("ok")
  }

}
