// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.data.State
import cats.implicits._

/**
 * A singly-inhabited type representing arguments to a parameterless statement.
 * @group Codecs
 */
sealed trait Void

/** @group Companions */
case object Void extends Void {

  val codec: Codec[Void] =
    new Codec[Void] {
      def encode(a: Void) = Nil
      def decode(index: Int, ss: List[Option[String]]) =
        ss match {
          case Nil => Void.asRight
          case _   => Left(Decoder.Error(index, 0, s"Expected no values, found $ss"))
        }
      val types = Nil
      val sql   = "".pure[State[Int, ?]]
    }

}
