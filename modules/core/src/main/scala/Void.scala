// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

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
      def decode(ss: List[Option[String]]) = Void
      val types = Nil
    }

}
