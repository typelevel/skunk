package skunk

import cats.effect._
import cats.implicits._
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import java.nio.channels._
import java.nio.charset._
import java.util.concurrent._
import scala.concurrent.duration._
import skunk.proto._
import skunk.proto.message._

object Main extends IOApp {

  val acg: Resource[IO, AsynchronousChannelGroup] = {
    val alloc = IO(AsynchronousChannelGroup.withThreadPool(Executors.newCachedThreadPool()))
    val free  = (acg: AsynchronousChannelGroup) => IO(acg.shutdown())
    Resource.make(alloc)(free)
  }

  val sock: Resource[IO, Socket[IO]] =
    acg.flatMap { implicit acg =>
      fs2.io.tcp.client(new InetSocketAddress("localhost", 5432))
    }

  val bvs: Resource[IO, BitVectorSocket[IO]] =
    sock.map(BitVectorSocket.fromSocket(_, 15.seconds, 15.seconds))

  val ms: Resource[IO, MessageSocket[IO]] =
    bvs.map(MessageSocket.fromBitVectorSocket[IO](_))

  val ams: Resource[IO, ActiveMessageSocket[IO]] =
    ms.flatMap { ms =>
      val alloc = ActiveMessageSocket.fromMessageSocket[IO](ms, 256)
      val free  = (ams: ActiveMessageSocket[IO]) => ms.send(Terminate)
      Resource.make(alloc)(free)
    }

  def decode: RowDescription => RowData => List[String] =
    desc => data => (desc.fields.map(_.name), data.decode(StandardCharsets.UTF_8)).zipped.map((a, b) => a + "=" + b)

  def run(args: List[String]): IO[ExitCode] =
    ams.use { s =>
      for {
        _   <- Startup(s, StartupMessage("postgres", "world"))
        st  <- s.transactionStatus.get
        enc <- s.parameters.get.map(_.get("client_encoding"))
        _   <- IO(println(s"Logged in! Transaction status is $st and client_encoding is $enc"))
        _   <- SimpleQuery.query(s, Query("select name, population from country where name like 'U%'"), decode).take(2).evalMap(r => IO(println(r))).compile.drain
        _   <- IO(println(s"Done with first query."))
        _   <- SimpleQuery.query(s, Query("select 'föf'"), decode).evalMap(r => IO(println(r))).compile.drain
        _   <- IO(println(s"Finishing up."))
      } yield ExitCode.Success
    }

}
