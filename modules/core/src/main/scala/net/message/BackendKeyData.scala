// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec.interop.cats._
import scodec.Decoder
import scodec.codecs._

/**
 * Cancellation key data. The frontend must save these values if it wishes to be able to issue
 * `CancelRequest` messages later.
 * @param pid The process ID of this backend.
 * @param key The secret key of this backend.
 */
final case class BackendKeyData(pid: Int, key: Int) extends BackendMessage

object BackendKeyData {
  final val Tag = 'K'
  val decoder: Decoder[BackendMessage] = (int32.asDecoder, int32.asDecoder).mapN(BackendKeyData(_, _))
}