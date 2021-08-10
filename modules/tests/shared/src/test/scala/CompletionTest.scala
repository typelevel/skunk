// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import skunk.data.Completion
import cats.effect.IO
import java.io.ByteArrayOutputStream

class CompletionTest extends ffstest.FTest {

  test("constructing a `Completion.Unknown` should log to stderr") {
    IO {
      val baos   = new ByteArrayOutputStream
      Console.withErr(baos)(Completion.Unknown("foo"))
      new String(baos.toByteArray)
    } flatMap { msg =>
      assert("log message", msg.contains("Just constructed an unknown completion 'foo'."))
    }
  }

}
