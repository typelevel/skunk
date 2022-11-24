// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package test

import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import cats.Eq
import scala.concurrent.duration._
import skunk.Decoder
import skunk.data.Type

class QueryTest extends SkunkTest{

    case class Number(value: Int)
    implicit val numberEq: Eq[Number] = Eq.by(_.value)

    sessionTest("map") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).map(_.toString)).use { ps =>
            for {
                n <- ps.unique(123)
                _ <- assertEqual("123", n, "123")
            } yield "ok"
        }
    }

    sessionTest("gmap") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).gmap[Number]).use { ps =>
            for {
                n <- ps.unique(123)
                _ <- assertEqual("123", n, Number(123))
            } yield "ok"
        }
    }

    sessionTest("contramap") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).contramap[String](_.toInt)).use { ps =>
            for {
                n <- ps.unique("123")
                _ <- assertEqual("123", n, 123)
            } yield "ok"
        }
    }

    sessionTest("gcontramap") { s =>
        val f = sql"select $int4"
        s.prepare(f.query(int4).gcontramap[Number]).use { ps =>
            for {
                n <- ps.unique(Number(123))
                _ <- assertEqual("123", n, 123)
            } yield "ok"
        }
    }

    val void: Decoder[skunk.Void] = new Decoder[skunk.Void] {
      def types: List[Type] = List(Type.void)
      def decode(offset: Int, ss: List[Option[String]]): Either[Decoder.Error, skunk.Void] = Right(skunk.Void)
    }


    pooledTest("timeout", 2.seconds) { getS =>
        val f = sql"select pg_sleep($int4)"
        def getErr[X]: Either[Throwable, X] => Option[String] = _.swap.toOption.collect {
            case e: java.util.concurrent.TimeoutException => e.getMessage()
        }
        for {
            sessionBroken <- getS.use { s =>
                s.prepare(f.query(void)).use { ps =>
                    for {
                        ret <- ps.unique(8).attempt
                        _ <- assertEqual("timeout error check", getErr(ret), Option("2 seconds"))
                    } yield "ok"
                }
            }.attempt
            _ <- assertEqual("timeout error check", getErr(sessionBroken), Option("2 seconds"))
            _ <- getS.use { s =>
                s.prepare(f.query(void)).use { ps =>
                    for {
                        ret <- ps.unique(1).attempt
                        _ <- assertEqual("timeout error ok", ret.isRight, true)
                    } yield "ok"
                }
            }
        } yield "ok"
    }

}
