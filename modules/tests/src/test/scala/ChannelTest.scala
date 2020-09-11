// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats._
import cats.syntax.all._
import cats.effect._
import skunk._
import skunk.implicits._
import cats.arrow.FunctionK
import cats.arrow.Profunctor

case object ChannelTest extends SkunkTest {

  sessionTest("channel (coverage)") { s =>
    val data = List("foo", "bar", "baz")
    val ch0 = s.channel(id"channel_test")
    val ch1 = ch0.mapK(FunctionK.id)
    val ch2 = Functor[Channel[IO, String, ?]].map(ch1)(identity[String])
    val ch3 = Contravariant[Channel[IO, ?, String]].contramap(ch2)(identity[String])
    val ch  = Profunctor[Channel[IO, ?, ?]].dimap(ch3)(identity[String])(identity[String])
    for {
      f <- ch.listen(42).map(_.value).takeThrough(_ != data.last).compile.toList.start
      _ <- data.traverse_(ch.notify)
      d <- f.join
      _ <- assert(s"channel data $d $data", data.endsWith(d)) // we may miss the first few
    } yield "ok"
  }

}