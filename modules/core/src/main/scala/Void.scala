// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import skunk.data.Type
import cats.data.State
import cats.syntax.all._
import cats.Eq

/**
 * A singly-inhabited type representing arguments to a parameterless statement.
 * @group Codecs
 */
sealed trait Void

/** @group Companions */
case object Void extends Void {

  implicit val eqVoid: Eq[Void] =
    Eq.fromUniversalEquals

  val codec: Codec[Void] =
    new Codec[Void] {
      override def encode(a: Void): List[Option[String]] = Nil
      override def decode(index: Int, ss: List[Option[String]]): Either[Decoder.Error, Void.type ] =
        ss match {
          case Nil => Void.asRight
          case _   => Left(Decoder.Error(index, 0, s"Expected no values, found $ss"))
        }
      override val types: List[Type] = Nil
      override val sql: State[Int, String]   = "".pure[State[Int, *]]
    }

}
