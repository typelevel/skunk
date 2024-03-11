// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.syntax.all._
import skunk.data.{Arr,Type}

trait TextCodecs {

  val varchar: Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.varchar)
  def varchar(n: Int): Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.varchar(n))

  val bpchar:  Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.bpchar)
  def bpchar(n: Int):  Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.bpchar(n))

  val name:    Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.name) // TODO: I think this might be `Identifier`
  val text:    Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.text)


  val _varchar: Codec[Arr[String]] = Codec.array(identity, Right(_), Type._varchar)
  val _bpchar:  Codec[Arr[String]] = Codec.array(identity, Right(_), Type._bpchar)
  val _text:    Codec[Arr[String]] = Codec.array(identity, Right(_), Type._text)
  val _name:    Codec[Arr[String]] = Codec.array(identity, Right(_), Type._name)

}

object text extends TextCodecs