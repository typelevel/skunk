// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder

case class ParameterStatus(name: String, value: String) extends BackendMessage

object ParameterStatus {
  final val Tag = 'S'
  val decoder: Decoder[ParameterStatus] = (utf8z.asDecoder, utf8z.asDecoder).mapN(apply)
}