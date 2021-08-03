// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._

abstract class BooleanCodecTest extends CodecTest {
  roundtripTest(bool)(true, false)
  decodeFailureTest(bool, List("xx"))
}


