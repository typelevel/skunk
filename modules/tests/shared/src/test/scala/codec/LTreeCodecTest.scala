// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._
import skunk.data.LTree
import skunk.util.Typer

class LTreeCodecTest extends CodecTest(strategy = Typer.Strategy.SearchPath) {

  roundtripTest(ltree)(LTree.Empty)
  roundtripTest(ltree)(LTree.unsafe("abc", "def"))
  roundtripTest(ltree)(LTree.unsafe("abcdefghijklmnopqrstuvwxyz".toList.map(_.toString()) :_*))

}


