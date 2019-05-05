// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import scodec.codecs._
import skunk.data.TransactionStatus

case class ReadyForQuery(status: TransactionStatus) extends BackendMessage

object ReadyForQuery {

  final val Tag = 'Z'

  def decoder: Decoder[BackendMessage] =
    byte.map {
      case 'I' => ReadyForQuery(TransactionStatus.Idle)
      case 'T' => ReadyForQuery(TransactionStatus.Active)
      case 'E' => ReadyForQuery(TransactionStatus.Failed)
    }

}