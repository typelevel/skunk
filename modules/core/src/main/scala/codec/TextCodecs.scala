// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.implicits._
import skunk.data.Type

trait TextCodecs {

  val varchar: Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.varchar)
  def varchar(n: Int): Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.varchar(n))

  val bpchar:  Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.bpchar)
  def bpchar(n: Int):  Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.bpchar(n))

  val name:    Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.name) // TODO: I think this might be `Identifier`
  val text:    Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.text)

}

object text extends TextCodecs