// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import munit.CatsEffectSuite

trait FTestPlatform extends CatsEffectSuite {
  final val isJVM = false
  final val isJS = false
  final val isNative = true
}
