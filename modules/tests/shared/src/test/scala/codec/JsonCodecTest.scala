// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import io.circe.Json
import io.circe.jawn.parse
import skunk.circe.codec.all._

class JsonCodecTest extends CodecTest {

  val j: Json = parse("""{"foo": [true, "bar"], "tags": {"a": 1, "b": null}}""").toOption.get

  roundtripTest(json)(j)
  roundtripTest(json[(Int, String)])((42, "foo"))
  roundtripTest(jsonb)(j)
  roundtripTest(jsonb[(Int, String)])((42, "foo"))
  decodeFailureTest(jsonb[(Int, String)], List("woozle", "blah"))
}


