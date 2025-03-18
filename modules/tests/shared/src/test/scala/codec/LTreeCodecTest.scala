// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import skunk.TypingStrategy
import skunk.codec.all._
import skunk.data.LTree

class LTreeCodecTest extends CodecTest(strategy = TypingStrategy.SearchPath) {

  roundtripTest(ltree)(LTree.Empty)
  roundtripTest(ltree)(LTree.fromLabels("abc", "def").toOption.get)
  roundtripTest(ltree)(LTree.fromLabels("abcdefghijklmnopqrstuvwxyz0123456789".toList.map(_.toString()) :_*).toOption.get)
  roundtripTest(ltree)(LTree.fromString("foo.βar.baz").toOption.get)
}

