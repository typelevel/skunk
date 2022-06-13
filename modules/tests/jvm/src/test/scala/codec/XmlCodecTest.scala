// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.Eq
import skunk.scalaxml.codec.all._

import scala.xml.{Elem, XML}

class XmlCodecTest extends CodecTest {
  implicit val eq: Eq[Elem] = Eq.fromUniversalEquals

  val x: Elem = XML.loadString("<foo>bar</foo>")

  roundtripTest(xml)(x)
}


