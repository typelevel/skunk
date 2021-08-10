// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data
import skunk.exception.SkunkException

sealed abstract class Completion
object Completion {
  case object Begin              extends Completion
  case object Commit             extends Completion
  case object CreateIndex        extends Completion
  case class  Delete(count: Int) extends Completion
  case object DropIndex          extends Completion
  case object Listen             extends Completion
  case object LockTable          extends Completion
  case object Notify             extends Completion
  case object Reset              extends Completion
  case object Rollback           extends Completion
  case object Savepoint          extends Completion
  case class  Select(count: Int) extends Completion
  case object Set                extends Completion
  case object Truncate           extends Completion
  case object Unlisten           extends Completion
  case class  Update(count: Int) extends Completion
  case class  Insert(count: Int) extends Completion
  case object CreateTable        extends Completion
  case object DropTable          extends Completion
  case object AlterTable         extends Completion
  case object CreateSchema       extends Completion
  case object DropSchema         extends Completion
  case object CreateType         extends Completion
  case object DropType           extends Completion
  case object CreateFunction     extends Completion
  case class  Copy(count: Int)   extends Completion
  case object Show               extends Completion
  // more ...

  /**
   * Instead of crashing (which breaks the protocol and hangs everything) let's allow for unknown
   * completion messages and print out a stacktrace on construction.
   */
  case class Unknown(text: String) extends Completion {
    new SkunkException(
      sql     = None,
      message = s"Just constructed an unknown completion '$text'.  Note that your program has not crashed. This message is here to annoy you.",
      hint    = Some("Please open an issue, or open a PR adding a case in Completion.scala and a parser in CommandComplete.scala")
    ).printStackTrace(Console.err)
  }

}
