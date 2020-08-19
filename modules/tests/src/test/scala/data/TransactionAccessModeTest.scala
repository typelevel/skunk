package data

import cats.effect.IO
import skunk.data.TransactionAccessMode

case object TransactionAccessModeTest extends ffstest.FTest {

  test("toLiteral should map ReadOnly to READ ONLY") {
    IO {
      TransactionAccessMode.toLiteral(TransactionAccessMode.ReadOnly)
    } flatMap { literal =>
      assert("ReadOnly should be READ ONLY", literal == "READ ONLY")
    }
  }

  test("toLiteral should map ReadWrite to READ WRITE") {
    IO {
      TransactionAccessMode.toLiteral(TransactionAccessMode.ReadWrite)
    } flatMap { literal =>
      assert("ReadWrite should be READ WRITE", literal == "READ WRITE")
    }
  }

}
