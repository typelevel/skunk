// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package scalaxml.codec

import cats.syntax.all._

import scala.xml._
import skunk.data.{Arr, Type}

trait XmlCodecs {
  val xml: Codec[Elem] = Codec.simple(_.toString, s => Either.catchNonFatal(XML.loadString(s)).leftMap(_.getMessage), Type.xml)
  val _xml: Codec[Arr[Elem]] = Codec.array(_.toString, s => Either.catchNonFatal(XML.loadString(s)).leftMap(_.getMessage), Type._xml)
}

object xml extends XmlCodecs

object all extends XmlCodecs
