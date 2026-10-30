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
import skunk.implicits._

/**
 * Checkout + checkin of a pooled session, with the full recycler (UNLISTEN * and RESET ALL on each
 * checkin) and with the idle recycler (no statements on checkin). Needs the `world` database from
 * docker-compose.yml on localhost:5432.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = Array("-Xmx2g"))
@Warmup(iterations = 5, time = 5)
@Measurement(iterations = 5, time = 5)
class PoolRecyclerBench {

  implicit val tracerProvider: TracerProvider[IO] = TracerProvider.noop[IO]
  implicit val meterProvider: MeterProvider[IO] = MeterProvider.noop[IO]

  @Param(Array("full", "idle"))
  var recycler: String = _

  var pool: Resource[IO, Session[IO]] = _
  var release: IO[Unit] = _

  val query: Query[Int, Int] = sql"select $int4".query(int4)

  @Setup(Level.Trial)
  def setup(): Unit = {
    val r = recycler match {
      case "full" => Session.Recyclers.full[IO]
      case "idle" => Session.Recyclers.idle[IO]
      case other  => sys.error(s"unknown recycler $other")
    }
    val (p, rel) =
      Session.Builder[IO]
        .withUserAndPassword("jimmy", "banana")
        .withDatabase("world")
        .withRecycler(r)
        .pooled(1)
        .allocated
        .unsafeRunSync()
    pool = p
    release = rel
  }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    release.unsafeRunSync()

  /** Checkout and checkin only: the cost of the recycler itself. */
  @Benchmark
  def checkoutCheckin(): Unit =
    pool.use(_ => IO.unit).unsafeRunSync()

  /** Checkout, one warm parameterized query, checkin: a typical request. */
  @Benchmark
  def checkoutQueryCheckin(): Int =
    pool.use(_.unique(query)(42)).unsafeRunSync()

}
