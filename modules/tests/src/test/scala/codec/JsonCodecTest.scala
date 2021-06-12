// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec
import io.circe.Json
import io.circe.parser.parse
import skunk._
import skunk.circe.codec.all._
import skunk.implicits._

class JsonCodecTest extends CodecTest {

  val j: Json = parse("""{"foo": [true, "bar"], "tags": {"a": 1, "b": null}}""").toOption.get


  /*
  
🔥
🔥  Postgres ERROR 08P01 raised in NewProtocolViolationErrorf (encoding.go:272)
🔥
🔥    Problem: Unknown oid type: 114.
🔥
🔥  The statement under consideration was defined
🔥    at /Users/ahjohannessen/Development/Personal/skunk/modules/tests/src/test/scala/codec/CodecTest.scala:35
🔥
🔥    select $1::json  
  
  */

  // roundtripTest(json)(j)
  // roundtripTest(json[Int ~ String])(42 ~ "foo")

  roundtripTest(jsonb)(j)
  roundtripTest(jsonb[Int ~ String])(42 ~ "foo")
  decodeFailureTest(jsonb[Int ~ String], List("woozle", "blah"))

}


