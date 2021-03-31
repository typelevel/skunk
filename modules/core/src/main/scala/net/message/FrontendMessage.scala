// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.syntax.all._
import scodec._
import scodec.codecs._
import scodec.interop.cats._
import scodec.bits.BitVector

sealed trait FrontendMessage {
  protected def encodeBody: Attempt[BitVector]
  def encode: BitVector
}

abstract class UntaggedFrontendMessage extends FrontendMessage {
  final def encode: BitVector = {
    for {
      b <- encodeBody
      l <- int32.encode(((b.size) / 8).toInt + 4)
    } yield l |+| b
  } .require
}

abstract class TaggedFrontendMessage(tag: Byte) extends FrontendMessage {
  final def encode: BitVector = {
    for {
      t <- byte.encode(tag)
      b <- encodeBody
      l <- int32.encode((b.size / 8).toInt + 4)
    } yield t |+| l |+| b
  } .require
}

abstract class ConstFrontendMessage(tag: Byte) extends TaggedFrontendMessage(tag) {
  final def encodeBody = Attempt.successful(BitVector.empty)
}
