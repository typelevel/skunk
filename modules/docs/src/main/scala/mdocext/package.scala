// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats.effect.IO
import cats.effect.unsafe.implicits.global

package object mdocext {

  implicit class IOOps[A](fa: IO[A]) {
    def unsafeRunSyncWithRedirect(): A = {
      val oldOut = System.out
      val newOut = Console.out // important to do this on the calling thread!
      try {
        System.setOut(newOut)
        fa.unsafeRunSync()
      } finally System.setOut(oldOut)
    }
  }

}
