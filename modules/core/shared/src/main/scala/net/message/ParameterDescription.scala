// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec._
import scodec.codecs._

case class ParameterDescription(oids: List[Int]) extends BackendMessage

object ParameterDescription {

  final val Tag = 't'

  val decoder: Decoder[ParameterDescription] =
    codecs.listOfN(int16, int32).map(ParameterDescription(_))

}