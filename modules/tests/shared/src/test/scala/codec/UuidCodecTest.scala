// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import skunk.codec.all._
import java.util.UUID

/** Test that we can round=trip values via codecs. */
abstract class UuidCodecTest extends CodecTest {

  val u1 = UUID.fromString("E778E40E-11F7-4A49-85F1-73496D296FA6")
  val u2 = UUID.fromString("FBBD6EFF-3C78-4211-B805-9A818627D970")

  roundtripTest(uuid)(u1, u2)
  roundtripTest(uuid.opt)(Some(u1), Some(u2), None)
  decodeFailureTest(time, List("x"))

}


