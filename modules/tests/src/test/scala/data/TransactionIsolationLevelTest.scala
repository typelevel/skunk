package data

import cats.effect.IO
import skunk.data.TransactionIsolationLevel

case object TransactionIsolationLevelTest extends ffstest.FTest {

  test("toLiteral should map Serializable to SERIALIZABLE") {
    IO {
      TransactionIsolationLevel.toLiteral(TransactionIsolationLevel.Serializable)
    } flatMap { literal =>
      assert("Serializable should be SERIALIZABLE", literal == "SERIALIZABLE")
    }
  }

  test("toLiteral should map RepeatableRead to REPEATABLE READ") {
    IO {
      TransactionIsolationLevel.toLiteral(TransactionIsolationLevel.RepeatableRead)
    } flatMap { literal =>
      assert("RepeatableRead should be REPEATABLE READ", literal == "REPEATABLE READ")
    }
  }

  test("toLiteral should map ReadCommitted to READ COMMITTED") {
    IO {
      TransactionIsolationLevel.toLiteral(TransactionIsolationLevel.ReadCommitted)
    } flatMap { literal =>
      assert("ReadCommitted should be READ COMMITTED", literal == "READ COMMITTED")
    }
  }

  test("toLiteral should map ReadUncommitted to READ UNCOMMITTED") {
    IO {
      TransactionIsolationLevel.toLiteral(TransactionIsolationLevel.ReadUncommitted)
    } flatMap { literal =>
      assert("ReadUncommitted should be READ UNCOMMITTED", literal == "READ UNCOMMITTED")
    }
  }

}
