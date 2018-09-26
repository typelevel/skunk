package skunk

import cats.effect._
import cats.implicits._
import fs2._
import fs2.Sink.showLinesStdOut
import fs2.io.tcp.Socket
import java.net.InetSocketAddress
import java.nio.channels._
import java.nio.charset._
import java.util.concurrent._
import scala.concurrent.duration._
import skunk.proto.message._
import skunk.dsl._
import skunk.dsl.Codec._

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
    sock.map(BitVectorSocket.fromSocket(_, 1.day, 5.seconds))

  val ms: Resource[IO, MessageSocket[IO]] =
    bvs.map(MessageSocket.fromBitVectorSocket[IO](_))

  val ams: Resource[IO, ActiveMessageSocket[IO]] =
    ms.flatMap { ms =>
      val alloc = ActiveMessageSocket.fromMessageSocket[IO](ms, 256)
      val free  = (ams: ActiveMessageSocket[IO]) => ms.send(Terminate)
      Resource.make(alloc)(free)
    }

  val session: Resource[IO, Session[IO]] =
    ams.flatMap(ams => Resource.liftF(Session.fromActiveMessageSocket(ams)))

  def decode: RowDescription => RowData => List[String] =
    desc => data => (desc.fields.map(_.name), data.decode(StandardCharsets.UTF_8)).zipped.map(_ + "=" + _)

  def report: ((RowDescription, List[RowData])) => IO[Unit] = { case (rd, rs) =>
    rs.traverse_(r => putStrLn(decode(rd)(r).mkString("Row(", ", ", ")")))
  }

  def putStrLn(a: Any): IO[Unit] =
    IO(println(a))

  val anyLinesStdOut: Sink[IO, Any] =
    _.map(_.toString).to(showLinesStdOut)

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      for {
        _   <- s.startup("postgres", "world")
        st  <- s.transactionStatus.get
        enc <- s.parameters.get.map(_.get("client_encoding"))
        _   <- putStrLn(s"Logged in! Transaction status is $st and client_encoding is $enc")
        _   <- s.listen("foo", 10).to(anyLinesStdOut).compile.drain.start
        _   <- s.query("select name, population from country limit 20").flatMap(report)
        _   <- s.notify("foo", "here is a message")
        _   <- s.query("select 'föf'").flatMap(report)
        _   <- putStrLn("Done.")
        _   <- s.parse(sql"select * from country where population < $int4")
        _   <- IO.sleep(3.seconds)
      } yield ExitCode.Success
    }

}
