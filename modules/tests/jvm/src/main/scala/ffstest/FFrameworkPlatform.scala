// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package ffstest

import munit.CatsEffectSuite

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

trait FTestPlatform extends CatsEffectSuite {

  // ensure that we have bountiful threads
  val executor = Executors.newCachedThreadPool()

  override val munitExecutionContext: ExecutionContext =
    ExecutionContext.fromExecutor(executor)

  override def afterAll(): Unit = {
    super.afterAll();
    executor.shutdown()
  }

}
