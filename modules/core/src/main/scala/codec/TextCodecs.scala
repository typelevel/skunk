// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import cats.implicits._
import skunk.data.Type

trait TextCodecs {

  val varchar: Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.varchar)
  val name:    Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.name)
  val bpchar:  Codec[String] = Codec.simple(_.toString, _.toString.asRight, Type.bpchar)

}

object text extends TextCodecs