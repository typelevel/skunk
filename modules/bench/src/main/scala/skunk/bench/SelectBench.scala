// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.bench

import cats.effect._
import skunk._
import skunk.implicits._
import skunk.codec.all._

import java.sql.DriverManager
import org.openjdk.jmh.annotations._
import org.typelevel.otel4s.trace.Tracer

@State(Scope.Benchmark)
object SelectBenchScope {
  implicit val tracer: Tracer[IO] = Tracer.noop[IO]

  val defaultChunkSize = 512

  val session: Resource[IO, skunk.Session[IO]] =
    Session.Builder.default[IO]
      .withDatabase("world")
      .withUserAndPassword("jimmy", "banana")
      .single
}

class SelectBench {
  import SelectBenchScope._
  import cats.effect.unsafe.implicits.global

  // Baseline hand-written JDBC code
  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements","org.wartremover.warts.While"))
  def jdbcBench(n: Int): Int = {
    Class.forName("org.postgresql.Driver")
    val co = DriverManager.getConnection("jdbc:postgresql:world", "jimmy", "banana")
    try {
      co.setAutoCommit(false)
      val ps = co.prepareStatement("select a.name, b.name, co.name from country a, country b, country co limit ?")
      try {
        ps.setInt(1, n)
        val rs = ps.executeQuery
        try {
          val accum = List.newBuilder[(String, String, String)]
          while (rs.next) {
            val a = rs.getString(1) ; rs.wasNull
            val b = rs.getString(2) ; rs.wasNull
            val c = rs.getString(3) ; rs.wasNull
            accum += ((a, b, c))
          }
          accum.result().length
        } finally rs.close
      } finally ps.close
    } finally {
      co.commit()
      co.close()
    }
  }

  // Reading via .stream
  def skunkBenchP(n: Int): Int =
    session.use { s =>
        val query = sql"select a.name, b.name, c.name from country a, country b, country c limit $int4".query(varchar *: varchar *: varchar)

        s.prepare(query)
          .flatMap(_.stream(n, defaultChunkSize).compile.toList)
          .map(_.length)
    }.unsafeRunSync()

  @Benchmark
  @OperationsPerInvocation(1000)
  def list_accum_1000_jdbc: Int = jdbcBench(1000)

  @Benchmark
  @OperationsPerInvocation(1000)
  def stream_accum_1000: Int = skunkBenchP(1000)

}
