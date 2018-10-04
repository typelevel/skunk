package skunk.message

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
      val encoder = enc
      val fullEncoder = Encoder(a => byte.encode(tag) |+| lengthPrefixed(enc).encode(a))
    }

  def untagged[A](enc: Encoder[A]): FrontendMessage[A] =
    new FrontendMessage[A] {
      val encoder = enc
      val fullEncoder = lengthPrefixed(enc)
    }

}
