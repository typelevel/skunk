// Copyright (c) 2018-2021 by Rob Norris
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
import scala.concurrent.duration._
import cats.effect.kernel.Outcome.Succeeded

class ChannelTest extends SkunkTest {

  override def munitIgnore: Boolean = true // Not currently supported by CRDB

  sessionTest("channel (coverage)") { s =>
    val data = List("foo", "bar", "baz")
    val ch0 = s.channel(id"channel_test")
    val ch1 = ch0.mapK(FunctionK.id)
    val ch2 = Functor[Channel[IO, String, *]].map(ch1)(identity[String])
    val ch3 = Contravariant[Channel[IO, *, String]].contramap(ch2)(identity[String])
    val ch  = Profunctor[Channel[IO, *, *]].dimap(ch3)(identity[String])(identity[String])
    for {
      // There is a race here. If this fiber doesn't start running quickly enough all the data will
      // be written to the channel before we execute LISTEN. We can't add a latch to `listen` that
      // indicates LISTEN has completed because it makes it impossible to implement `mapK` for
      // `Channel` and thus for `Session`. So for now we're just going to sleep a while. I'm not
      // sure it's a problem in real life but it makes this test hard to write.
      f <- ch.listen(42).map(_.value).takeThrough(_ != data.last).compile.toList.start
      _ <- IO.sleep(1.second) // sigh
      _ <- data.traverse_(ch.notify)
      o <- f.join
      Succeeded(fa) = o
      d <- fa
      _ <- assert(s"channel data $d $data", data.endsWith(d)) // we may miss the first few
    } yield "ok"
  }

}