// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package example

import cats.effect._
import cats.syntax.all._
import skunk._
import skunk.implicits._
import skunk.codec.all._
import natchez.Trace.Implicits.noop

/** Round-trip a list of values. You can use this pattern to do bulk-inserts. */
object Values extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      user     = "jimmy",
      database = "world",
      password = Some("banana"),
    )

  case class Data(n: Int, s: String, b: Boolean)

  val data: Codec[Data] =
    (int4 ~ bpchar ~ bool).gimap[Data]

  // SQL depends on the number of `Data` elements we wish to "insert"
  def query(len: Int): Query[List[Data], Data] =
    sql"VALUES ${data.values.list(len)}".query(data)

  val examples: List[Data] =
    List(
      Data(10, "foo", true),
      Data(11, "bar", true),
      Data(12, "baz", false),
    )

  def run(args: List[String]): IO[ExitCode] =
    session.use { s =>
      val q = query(examples.length)
      s.prepare(q).use { pq =>
        for {
          _  <- IO(println(q.sql))
          ds <- pq.stream(examples, 64).compile.to(List)
          _  <- ds.traverse(d => IO(println(d)))
          _  <- IO(println(s"Are they the same? ${ds == examples}"))
        } yield ExitCode.Success
      }
    }

}
