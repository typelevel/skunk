// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package codec

import skunk.data.Type
import skunk.data.LTree

trait LTreeCodec {

  val ltree: Codec[LTree] =
    Codec.simple[LTree](
      ltree => ltree.toString(),
      s => LTree.fromString(s),
      Type("ltree")
    )

}

object ltree extends LTreeCodec