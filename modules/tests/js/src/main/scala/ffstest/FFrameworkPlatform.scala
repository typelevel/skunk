// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import cats.effect.IO
import fs2.compression.Compression
import munit.CatsEffectSuite

trait FTestPlatform extends CatsEffectSuite {
  implicit val fs2Compression: Compression[IO] =
    fs2.io.compression.fs2ioCompressionForIO

  final val isJVM = false
  final val isJS = true
  final val isNative = false
}
