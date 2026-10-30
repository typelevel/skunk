// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.bench

import java.util.concurrent.TimeUnit

import cats.effect._
import cats.effect.unsafe.implicits.global
import org.openjdk.jmh.annotations._
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.trace.TracerProvider
import skunk._
import skunk.codec.all._
import skunk.data.Completion
import skunk.implicits._

/**
 * Warm parameterized statements on one session, so that parse and describe are cached and each
 * operation is a single Bind/Execute/Sync exchange. Needs the `world` database from
 * docker-compose.yml on localhost:5432.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = Array("-Xmx2g"))
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
class WarmSessionBench {

  implicit val tracerProvider: TracerProvider[IO] = TracerProvider.noop[IO]
  implicit val meterProvider: MeterProvider[IO] = MeterProvider.noop[IO]

  var session: Session[IO] = _
  var release: IO[Unit] = _

  val query: Query[Int, Int] = sql"select $int4".query(int4)
  val command: Command[Int] = sql"update warm_session_bench set v = $int4".command

  @Setup(Level.Trial)
  def setup(): Unit = {
    val (s, rel) =
      Session.Builder[IO]
        .withUserAndPassword("jimmy", "banana")
        .withDatabase("world")
        .single
        .allocated
        .unsafeRunSync()
    session = s
    release = rel
    (s.execute(sql"create temporary table warm_session_bench (v int4)".command) *>
     s.execute(sql"insert into warm_session_bench values (0)".command) *>
     s.unique(query)(0) *>
     s.execute(command)(0)).void.unsafeRunSync()
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    release.unsafeRunSync()

  @Benchmark
  def warmQuery(): Int =
    session.unique(query)(42).unsafeRunSync()

  @Benchmark
  def warmCommand(): Completion =
    session.execute(command)(42).unsafeRunSync()

}
