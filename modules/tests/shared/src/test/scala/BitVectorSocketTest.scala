// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats.effect._
import com.comcast.ip4s.{Host, IpAddress, Port, SocketAddress}
import fs2.io.net.{Socket, SocketGroup, SocketOption}
import skunk.exception.SkunkException
import skunk.net.BitVectorSocket
import scala.concurrent.duration.Duration

class BitVectorSocketTest extends ffstest.FTest {

  private val dummySg = new SocketGroup[IO] {
    override def client(to: SocketAddress[Host], options: List[SocketOption]): Resource[IO, Socket[IO]] = ???

    override def server(address: Option[Host], port: Option[Port], options: List[SocketOption]): fs2.Stream[IO, Socket[IO]] = ???

    override def serverResource(address: Option[Host], port: Option[Port], options: List[SocketOption]): Resource[IO, (SocketAddress[IpAddress], fs2.Stream[IO, Socket[IO]])] = ???
  }

  private val socketOptions = List(SocketOption.noDelay(true))

  test("Invalid host") {
    BitVectorSocket("", 1, dummySg, socketOptions, None, Duration.Inf).use(_ => IO.unit).assertFailsWith[SkunkException]
      .flatMap(e => assertEqual("message", e.message, """Hostname: "" is not syntactically valid."""))
  }
  test("Invalid port") {
    BitVectorSocket("localhost", -1, dummySg, socketOptions, None, Duration.Inf).use(_ => IO.unit).assertFailsWith[SkunkException]
      .flatMap(e => assertEqual("message", e.message, "Port: -1 falls out of the allowed range."))
  }

}
