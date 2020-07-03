package tests.exception

import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import skunk.data.Type
import skunk.exception.UnknownOidException

case object UnknownOidExceptionTest1 extends SkunkTest(strategy = Strategy.SearchPath) {

    val mood = enum[String](identity, Option(_), Type("mood"))
    sessionTest("raise UnknownOidException when referencing a new type, using Strategy.SearchPath") { s =>
      for {
        _ <- s.execute(sql"DROP TYPE IF EXISTS mood".command)
        _ <- s.execute(sql"CREATE TYPE mood AS ENUM ('sad', 'ok', 'happy')".command)
        _ <- s.unique(sql"select 'sad'::mood".query(mood)).assertFailsWith[UnknownOidException](show = true)
      } yield "ok"
    }

}

case object UnknownOidExceptionTest2 extends SkunkTest(strategy = Strategy.BuiltinsOnly) {

    val myenum = enum[String](identity, Option(_), Type("myenum"))
    sessionTest("raise UnknownOidException when referencing a user-defined type with Strategy.BuiltinsOnly") { s =>
      for {
        _ <- s.unique(sql"select 'foo'::myenum".query(myenum)).assertFailsWith[UnknownOidException](show = true)
      } yield "ok"
    }

}