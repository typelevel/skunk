// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import epollcat.unsafe.EpollRuntime
import munit.CatsEffectSuite

trait FTestPlatform extends CatsEffectSuite {
  override def munitIORuntime = EpollRuntime.global
}