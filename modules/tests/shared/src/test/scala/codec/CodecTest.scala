// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests
package codec

import cats.Eq
import cats.syntax.all._
import skunk._
import skunk.codec.all._
import skunk.implicits._
import skunk.util.Typer
import ffstest.FTest
import cats.InvariantSemigroupal
import cats.effect.IO

/** Tests that we check if we can round-trip values via codecs. */
abstract class CodecTest(
  debug:    Boolean = false,
  strategy: Typer.Strategy = Typer.Strategy.BuiltinsOnly
) extends SkunkTest(debug, strategy) {

  case class Box[A](a: A)
  object Box {
    implicit def eqBox[A: Eq]: Eq[Box[A]] =
      Eq.by(_.a)
  }


  def roundtripTest[A: Eq](codec: Codec[A])(as: A*): Unit = {

    // required for parametrized types
    val sqlString: Fragment[A] = (codec.types match {
      case head :: Nil => sql"select $codec::#${head.name}"
      case _           => sql"select $codec"
    }).asInstanceOf[Fragment[A]] // macro doesn't quite work in dotty yet

    sessionTest(s"${codec.types.mkString(", ")}") { s =>
      s.prepare(sqlString.query(codec)).use { ps =>
        as.toList.traverse { a =>
          for {
            aʹ <- ps.unique(a)
            _  <- assertEqual(s"$a", aʹ, a)
          } yield a
        } .map(_.mkString(", "))
      }
    }

    sessionTest(s"${codec.types.mkString(", ")} (gmap)") { s =>
      s.prepare(sqlString.query(codec.gmap[Box[A]])).use { ps =>
        as.toList.traverse { a =>
          for {
            aʹ <- ps.unique(a)
            _  <- assertEqual(s"$a", aʹ, Box(a))
          } yield a
        } .map(_.mkString(", "))
      }
    }

  }

  // Test a specific special value like NaN where equals doesn't work
  def roundtripWithSpecialValueTest[A](name: String, codec: Codec[A], ascription: Option[String] = None)(value: A, isOk: A => Boolean): Unit =
    sessionTest(s"${codec.types.mkString(",")}") { s =>
      s.prepare(sql"select $codec#${ascription.foldMap("::" + _)}".asInstanceOf[Fragment[A]].query(codec)).use { ps =>
        ps.unique(value).flatMap { a =>
          assert(name, isOk(a)).as(name)
        }
      }
    }

  def decodeFailureTest[A](codec: Codec[A], invalid: List[String]) : Unit = {
    test(s"${codec.types.mkString(",")} decode (invalid)") {
      assert("should fail", codec.decode(0, invalid.map(_.some)).isLeft)
    }
    test(s"${codec.types.mkString(",")} decode (insufficient input)") {
      assert("should fail", codec.decode(0, invalid.as(none)).isLeft)
    }
  }

}

class CodecCombinatorsTest extends FTest {

  val c = int2 ~ (int4 ~ float8) ~ varchar

  test("composed codec generates correct sql") {
    assertEqual("sql", c.sql.runA(1).value, "$1, $2, $3, $4")
  }

  test("contramapped codec generated correct sql") {
    assertEqual("sql", c.contramap[Short ~ (Int ~ Double) ~ String](identity).sql.runA(1).value, "$1, $2, $3, $4")
  }

  test("imapped codec generated correct sql") {
    assertEqual("sql", c.imap[Short ~ (Int ~ Double) ~ String](identity)(identity).sql.runA(1).value, "$1, $2, $3, $4")
  }

  test("eimapped codec generated correct sql") {
    assertEqual("sql", c.eimap[Short ~ (Int ~ Double) ~ String](_.asRight)(identity).sql.runA(1).value, "$1, $2, $3, $4")
  }

  test("text.eimap (fail)") {
    text.eimap(value => Either.catchNonFatal(value.toInt).leftMap(_ => "Not an Int"))(String.valueOf)
      .decode(1, List(Some("foo"))) match {
      case Left(err) => assertEqual("error", err, Decoder.Error(1, 1, "Not an Int", None))
      case Right(n) => fail(s"Expected failure, got $n")
    }
  }

  test("invariant semigroupal (coverage)") {
    val c = skunk.codec.all.int4
    InvariantSemigroupal[Codec].imap(c)(identity)(identity)
    InvariantSemigroupal[Codec].product(c, c)
    IO("ok")
  }

}