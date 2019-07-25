// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import skunk.data.Type

trait BooleanCodec {

  val bool: Codec[Boolean] =
    Codec.simple(
      b => if (b) "t" else "f", {
        case "t" => Right(true)
        case "f" => Right(false)
        case s   => Left(s"Expected 't' or 'f', got $s")
      },
      Type.bool
    )

}

object boolean extends BooleanCodec
