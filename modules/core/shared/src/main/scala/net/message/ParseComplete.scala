// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder

case object ParseComplete extends BackendMessage {
  final val Tag = '1'
  def decoder: Decoder[ParseComplete.type] = ParseComplete.pure[Decoder]
}