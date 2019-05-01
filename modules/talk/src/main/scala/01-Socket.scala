package talk

import fs2.Chunk
import scala.concurrent.duration._
import cats.effect.ContextShift
import cats.effect.Concurrent
import cats.effect.Resource
import cats.implicits._
import fs2.io.tcp.Socket
import cats.effect.Sync
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}

object Socket1 {

  // So we need to do pure fuctional socket programming. How do we want to think about this?
  // The way the Postgres protocol works we really only need two operations, something like this.

trait Socket[F[_]] {

  def readN(
    numBytes: Int,
    timeout:  FiniteDuration
  ): F[Array[Byte]]

  def write(
    bytes:   Array[Byte],
    timeout: FiniteDuration
  ): F[Unit]

}
    // This is a tagless interface, which is just a style of programming that parameterizes everything
    // on the effect in which values are computed. So `F` here is probably something like `IO` in real
    // code but you could mock up something in some other effect like StateT maybe, if you need to
    // test error-handling code for example and want to force some weird errors to happen.

    // So anyway we can read some number of bytes or we can write some number of bytes. In both
    // cases we can time out, which will raise an error in the `F` effect.

  // }

}

object Socket2 {

    // So it turns out there's already an implementation of something like this, provided by fs2.
    // One difference is that it uses a thing called a `Chunk` instead of a byte array. A chunk is
    // an immutable sequence of bytes. Another difference is that whe we read a chunk we get back
    // None if the socket terminates before we have read enough bytes. So let's go with this for
    // now.

    trait Socket[F[_]] {

      def readN(
        numBytes: Int,
        timeout:  Option[FiniteDuration]
      ): F[Option[Chunk[Byte]]]

      def write(
        bytes: Chunk[Byte],
        timeout: Option[FiniteDuration]
      ): F[Unit]

      // ...

    }

}

object Socket3 {

  // So how do you use such a thing?

  // So we have one annoyance to deal with. Creating an NIO asynchronous socket requires an
  // `AsynchronousChannelGroup` which is the thing java.nio uses to complete asynchronous
  // operations. We'll just have one of these and pass it down to where it's needed.

  // So this is a good time to introduce cats.effect.Resource, if you haven't seen it.
  // EXPLAIN

def asynchronousChannelGroup[G[_]: Sync]
  : Resource[G, AsynchronousChannelGroup] = {

  val alloc: G[AsynchronousChannelGroup] =
    Sync[G].delay {
      AsynchronousChannelGroup
        .withThreadPool(Executors.newCachedThreadPool())
    }

  val free: AsynchronousChannelGroup => G[Unit] =
    acg => Sync[G].delay(acg.shutdown())

  Resource.make(alloc)(free)
}

  // And now if we have an implicit `AsynchronousChannelGroup` we can construct a socket and use it.
  // - introduce Concurrent and ContextShift

  def socket[F[_]: Concurrent: ContextShift](
    host: String,
    port: Int
  ): Resource[F, Socket[F]] =
    asynchronousChannelGroup.flatMap { implicit acg =>
      Socket.client[F](new InetSocketAddress(host, port))
    }


}

// And let's write a little program.
// - introduce IOApp
// - introduce Traverse[Option]
// - introduce Sync

object Socket4 extends IOApp {
  import Socket3._

  def run(args: List[String]): IO[ExitCode] =
    socket[IO]("google.com", 80).use { sock =>
      val req = Chunk.array("GET / HTTP/1.0\n\n".getBytes)
      for {
        _ <- sock.write(req, Some(1.second))
        o <- sock.readN(256, Some(1.second))
        _ <- o.traverse(c => Sync[IO].delay {
              println(new String(c.toArray, "US-ASCII"))
            })
      } yield ExitCode.Success
    }

}

// And just as an exercise let's make this program effect-polymorphic since we're going to be doing
// a lot of that.

object Socket5 extends IOApp {
  import Socket3._

  def runF[F[_]: Concurrent: ContextShift]: F[ExitCode] =
    socket[F]("google.com", 80).use { sock =>
      val req = Chunk.array("GET / HTTP/1.0\n\n".getBytes)
      for {
        _ <- sock.write(req, Some(1.second))
        o <- sock.readN(512, Some(1.second))
        _ <- o.traverse(c => Sync[F].delay {
                println(new String(c.toArray, "US-ASCII"))
              })
      } yield ExitCode.Success
    }

  def run(args: List[String]): IO[ExitCode] =
    runF[IO]

}

// Ok so now we know how to read and write chunks to a socket, but `Chunk` is a really low level
// type that's not super fun to work with, so we're going to use a nicer type called a BitVector