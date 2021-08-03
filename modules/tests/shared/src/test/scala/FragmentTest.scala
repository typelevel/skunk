// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests

import cats._
import skunk._
import skunk.codec.all._
import skunk.implicits._

abstract class FragmentTest extends SkunkTest {

  sessionTest("contramap") { s =>
    val f = sql"select $int4".contramap[String](_.toInt)
    s.prepare(f.query(int4)).use { ps =>
      for {
        n <- ps.unique("123")
        _ <- assertEqual("123", n, 123)
      } yield "ok"
    }
  }

  sessionTest("product") { s =>
    val f = sql"select $int4" product sql", $varchar"
    s.prepare(f.query(int4 ~ varchar)).use { ps =>
      for {
        n <- ps.unique(123 ~ "456")
        _ <- assertEqual("123 ~ \"456\"", n, 123 ~ "456")
      } yield "ok"
    }
  }

  sessionTest("~") { s =>
    val f = sql"select $int4" ~ sql", $varchar"
    s.prepare(f.query(int4 ~ varchar)).use { ps =>
      for {
        n <- ps.unique(123 ~ "456")
        _ <- assertEqual("123 ~ \"456\"", n, 123 ~ "456")
      } yield "ok"
    }
  }

  sessionTest("contramap via ContravariantSemigroupal") { s =>
    val f = ContravariantSemigroupal[Fragment].contramap[Int, String](sql"select $int4")(_.toInt)
    s.prepare(f.query(int4)).use { ps =>
      for {
        n <- ps.unique("123")
        _ <- assertEqual("123", n, 123)
      } yield "ok"
    }
  }

  sessionTest("product via ContravariantSemigroupal") { s =>
    val f = ContravariantSemigroupal[Fragment].product(sql"select $int4", sql", $varchar")
    s.prepare(f.query(int4 ~ varchar)).use { ps =>
      for {
        n <- ps.unique(123 ~ "456")
        _ <- assertEqual("123 ~ \"456\"", n, 123 ~ "456")
      } yield "ok"
    }
  }
}

