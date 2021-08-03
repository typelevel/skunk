// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.exception

import skunk._
import skunk.codec.all._
import skunk.implicits._
import tests.SkunkTest
import skunk.data.Type
import skunk.exception.UnknownOidException

abstract class UnknownOidExceptionTest1 extends SkunkTest(strategy = Strategy.SearchPath) {

    val mood = `enum`[String](identity, Option(_), Type("mood"))
    sessionTest("raise UnknownOidException when referencing a new type, using Strategy.SearchPath") { s =>
      for {
        _ <- s.execute(sql"DROP TYPE IF EXISTS mood".command)
        _ <- s.execute(sql"CREATE TYPE mood AS ENUM ('sad', 'ok', 'happy')".command)
        _ <- s.unique(sql"SELECT 'sad'::mood AS blah".query(mood)).assertFailsWith[UnknownOidException]
      } yield "ok"
    }

}

class UnknownOidExceptionTest2 extends SkunkTest(strategy = Strategy.BuiltinsOnly) {

    val myenum = `enum`[String](identity, Option(_), Type("myenum"))
    sessionTest("raise UnknownOidException when referencing a user-defined type with Strategy.BuiltinsOnly") { s =>
      s.unique(sql"SELECT 'foo'::myenum AS blah".query(myenum)).assertFailsWith[UnknownOidException]
       .as("ok")
    }

}