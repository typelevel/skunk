// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package talk

import cats._
import cats.effect._
import cats.implicits._
import scala.concurrent.duration._
import scodec._
import scodec.codecs._
import scodec.Encoder
import scodec.bits.BitVector
import skunk.net.BitVectorSocket

// Ok, so now can step up a level and start talking about sending protocol messages, which we
// encode as bitvectors, which we already know how to send back and forth.


object Message1 {


  // So let's just start at the beginning. The first message we have to send is the Startup message,
  // which identifies you with the back end and specifies which database you'd like to use. So our
  // first job is to figure out how to encode this message as a bitvector.

  // The layout of the startup message is like this: (diagram)
  // The only variables here are the user and database, so our data type for this message is simple.

  case class Startup(user: String, database: String)
  object Startup {

    // Now what we need to define is an scodec Encoder, so let's talk a little bit about that.
    // And encoder is basically just a function from a value to a BitVector, but we work in this
    // `Attempt` effect, which is a lot like `Either`, it has a success case and a failure case,
    // and it's a monad so you can compose them with a for-comprehension.

    // trait Encoder[-A] {
    //   def encode(value: A): Attempt[BitVector]
    //   // ...
    // }

    // The game here is to use big encoders to buid up smaller ones, so let's do that. We encode
    // each part of the message payload and then concatenate the BitVectors together. BitVector is
    // structured in such a way that this is a fast operation.

    val payload1: Encoder[Startup] =
      Encoder { s =>
        for {
          v  <- int32.encode(196608)
          uk <- cstring.encode("user")
          u  <- cstring.encode(s.user)
          dk <- cstring.encode("database")
          d  <- cstring.encode(s.database)
          n  <- byte.encode(0)
        } yield v ++ uk ++ u ++ dk ++ d ++ n
      }


    val payload: Encoder[Startup] =
      Encoder { s =>
        for {
          v  <- int32.encode(196608)
          u  <- keyed("user").encode(s.user)
          d  <- keyed("database").encode(s.database)
          n  <- byte.encode(0)
        } yield v ++ u ++ d ++ n
      }

    // This `keyed` thing is a combinator that encodes a constant key and a variable value, and
    // we can define it by operating on existing encoders. This says take a cstring encoder and
    // turn it in to an encoder that takes the unit value and

    def keyed(key: String): Encoder[String] =
      Encoder { s =>
        for {
          k <- cstring.encode(key)
          v <- cstring.encode(s)
        } yield k ++ v
      }

    // And I should point out that I'm being very verbose in these examples rather than using
    // combinators. This encoder can be written like `cstring.unit(key) ~> cstring`.

    // Ok, so one final wrinkle is that Postgres messages are always length-prefixe, so we need
    // to write a combinator to take an existing encoder and prefixes a length, which is pretty
    // straightforward.

    def lengthPrefixed[A](e: Encoder[A]): Encoder[A] =
      Encoder { (a: A) =>
        for {
          p <- e.encode(a)
          l <- int32.encode((p.size / 8).toInt + 4) // total length in bytes, including prefix
        } yield l ++ p
      }

    // So our final encoder for the Startup message is

    val encoder: Encoder[Startup] =
      lengthPrefixed(payload)

  }

}


object Message2 extends IOApp {
  import BitVector2._
  import Message1._

  // And our example is a lot like the last one, but we're relying on our encoder now for the
  // outgoing message.

  def runF[F[_]: Concurrent: ContextShift]: F[ExitCode] =
    bitVectorSocket[F](
      "localhost", 5432, 1.second, 1.seconds
    ).use { sock =>
      val msg   = Startup("postgres", "world")
      val bytes = Startup.encoder.encode(msg).require
      for {
        _  <- sock.write(bytes)
        bv <- sock.read(256)
        _  <- Sync[F].delay(println(bv.decodeAscii))
      } yield ExitCode.Success
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO]

}

object Message3 extends IOApp {
  import BitVector2._
  import Message1._

  // Ok so we're getting *something* back but it's just a bunch of bytes, so let's write a Decoder
  // for back-end messages. All back-end messages have a one-byte tag that identifies the message
  // type, followed by a payload that you interpret differently based on the tag. So let's start
  // with a catchall case for unknown messages. The tag is actually an ASCII character so we'll
  // call it a char in the model.

  case class Unknown(tag: Char, data: BitVector) {
    override def toString = s"Unknown($tag, <data>)"
  }

  // All messages are prefixed with their length, so the strategy is, we read the first five bytes
  // which gives us the length and tag, then we can read the payload.

  def readUnknown[F[_]: FlatMap](
    bvs: BitVectorSocket[F]
  ): F[Unknown] =
    bvs.read(5).flatMap { bits =>
      val tag ~ len = (byte ~ int32).decodeValue(bits).require
      bvs.read(len - 4).map(Unknown(tag.toChar, _))
    }

  // Then because we don't really have a better idea yet let's just read the response forever and
  // then the program will crash when the read socket times out.

  def finishStartup[F[_]: Sync](bvs: BitVectorSocket[F]): F[Unit] =
    for {
      m <- readUnknown(bvs)
      _ <- Sync[F].delay(println(m))
      _ <- finishStartup(bvs)
    } yield ()

  def runF[F[_]: Concurrent: ContextShift]: F[ExitCode] =
    bitVectorSocket[F](
      "localhost", 5432, 1.day, 1.second
    ).use { sock =>
      val msg   = Startup("postgres", "world")
      val bytes = Startup.encoder.encode(msg).require
      for {
        _ <- sock.write(bytes)
        _ <- finishStartup(sock)
      } yield ExitCode.Success
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO]

}

// Ok so we see that we're getting back several different types of messages.
// We have an 'R' and a bunch of 'S's and then a 'K' and a 'Z'.

// So I can tell you that the 'R' is an authentication response that says authentication is ok
// The 'S's are all parameter status messages that tell you things about the database configuration,
// like the timezone and character encoding and database version, and these messages can actually
// arrive at any time, asynchronously. So if someone changes the server timezone you'll get an
// asynchronous message. So handling asynchronous messages like this is an interestin challenge and
// we'll return to in in a few minutes.
// The 'K' is a backend key that you can use to cancel running queries. You connect on another
// socket and pass that key and the server will try to kill whatever's going on in this channel.
// And the 'Z' is the ready for query message that says we're all set up and can now do some
// database stuff.

object Message4 extends IOApp {
  import skunk.net._
  import skunk.net.message.{ Sync => _, _ }

  // So using all this machinery we can create wrap our BitVectorSocket and create a MessageSocket
  // that knows how to speak in terms of these messages, using encoders and decoders just like we
  // have seen. I'm not going to go through the whole implementation but it's straightforward.

  // trait MessageSocket[F[_]] {

  //   def receive: F[BackendMessage]
  //   def send[A: FrontendMessage](a: A): F[Unit]

  //   // ...

  // }



  def finishStartupXX[F[_]: Monad](ms: MessageSocket[F]): F[Unit] =
    ms.receive.flatMap {
      case ReadyForQuery(_) => ().pure[F]
      case _                => finishStartupXX(ms)
    }

  def finishStartup[F[_]: Monad](
    ms: MessageSocket[F]
  ): F[Map[String, String]] = {

    def go(accum: Map[String, String]): F[Map[String, String]] =
      ms.receive.flatMap {
        case ReadyForQuery(_)      => accum.pure[F]
        case ParameterStatus(k, v) => go(accum + (k -> v))
        case _                     => go(accum)
      }

    go(Map.empty)
  }

  def runF[F[_]: Concurrent: ContextShift]: F[ExitCode] =
    BitVectorSocket("localhost", 5432, 10.seconds, 1.second, BitVectorSocket.GlobalACG).use { bvs =>
      for {
        ms <- MessageSocket.fromBitVectorSocket(bvs, true)
        _  <- ms.send(StartupMessage("postgres", "world"))
        ps <- finishStartup(ms)
        _  <- ps.toList.traverse { case (k, v) =>
                Sync[F].delay(println(s"$k -> $v"))
              }
      } yield ExitCode.Success
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO]

}

// So you see how in `receiveAll` we're pattern-matching on messages, and we can do this to
// build up protocol interactions, like if we send a query message we'll expect back a row
// description message followed by a bunch of row data messages, a completion message, and then
// ready for query. So we can define that exchange in terms of normal monadic composition.

object Message5 extends IOApp {
  import Message4._

  import skunk.net._
  import skunk.net.message.{ Sync => _, _ }

  def processResults[F[_]: Sync](
    ms: MessageSocket[F]
  ): F[List[RowData]] = {

    def expectF[A](
      f: PartialFunction[BackendMessage, F[A]]
    ): F[A] =
      ms.receive.flatMap { m =>
        if (f.isDefinedAt(m)) f(m)
        else new Exception(s"Unexpected: $m").raiseError[F, A]
      }

    def expect[A](
      f: PartialFunction[BackendMessage, A]
    ): F[A] =
      expectF(f.andThen(_.pure[F]))

    def unroll(accum: List[RowData]): F[List[RowData]] =
      expectF {
        case rd @ RowData(_)         => unroll(rd :: accum)
        case cc @ CommandComplete(_) => accum.reverse.pure[F]
      }

    for {
      _  <- expect { case RowDescription(_)     => }
      rs <- unroll(Nil)
      _  <- expect { case rq @ ReadyForQuery(_) => }
    } yield rs

  }


  def runF[F[_]: Concurrent: ContextShift]: F[ExitCode] =
    BitVectorSocket("localhost", 5432, 10.seconds, 1.second, BitVectorSocket.GlobalACG).use { bvs =>
      for {
        ms <- MessageSocket.fromBitVectorSocket(bvs, true)
        _  <- ms.send(StartupMessage("postgres", "world"))
        _  <- finishStartup(ms)
        _  <- ms.send(Query(
                """select name, population from country
                    where name like 'U%'"""
              ))
        rs <- processResults(ms)
        _  <- rs.toList.traverse { rd =>
                Sync[F].delay(println(rd))
              }
      } yield ExitCode.Success
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO]

}

// And we can factor out combinators for the repeated stuff like "what happens if we get an
// unexpected message?" and we can build this stuff up. But we have this problem where we might
// receive asynchronous messages at any time during the session, so these random messages might
// pop up anywhere in our code and we have to be sure they're handled correctly. And they also
// might pop up when we're not actively waiting to receive a message, so an asynchronous message
// might not get noticed until next time we execute a query or something. So this is no good and
// we need to figure out a better approach.

