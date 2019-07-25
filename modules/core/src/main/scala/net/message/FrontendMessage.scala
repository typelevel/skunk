// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import cats.implicits._
import scodec._
import scodec.codecs._
import scodec.interop.cats._

/** A typeclass for messages we send to the server. */
trait FrontendMessage[A] {

  /** Payload encoder (only). */
  def encoder: Encoder[A]

  /** Full encoder that adds a tag (if any) and length prefix. */
  def fullEncoder: Encoder[A]

}

object FrontendMessage {

  private def lengthPrefixed[A](e: Encoder[A]): Encoder[A] =
    Encoder { (a: A) =>
      for {
        p <- e.encode(a)
        l <- int32.encode((p.size / 8).toInt + 4)
      } yield l ++ p
    }

  def tagged[A](tag: Byte)(enc: Encoder[A]): FrontendMessage[A] =
    new FrontendMessage[A] {
      override val encoder:     Encoder[A] = enc
      override val fullEncoder: Encoder[A] = Encoder(a => byte.encode(tag) |+| lengthPrefixed(enc).encode(a))
    }

  def untagged[A](enc: Encoder[A]): FrontendMessage[A] =
    new FrontendMessage[A] {
      override val encoder:     Encoder[A] = enc
      override val fullEncoder: Encoder[A] = lengthPrefixed(enc)
    }

}
