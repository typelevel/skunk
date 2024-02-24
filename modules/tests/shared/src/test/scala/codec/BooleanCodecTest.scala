// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._

class BooleanCodecTest extends CodecTest {
  roundtripTest(bool)(true, false)
  decodeFailureTest(bool, List("xx"))
}


