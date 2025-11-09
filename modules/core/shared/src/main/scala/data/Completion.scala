// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data
import skunk.exception.SkunkException

sealed abstract class Completion
object Completion {
  case object Begin                     extends Completion
  case object Commit                    extends Completion
  case object CreateIndex               extends Completion
  case class  Delete(count: Int)        extends Completion
  case object DropIndex                 extends Completion
  case object Listen                    extends Completion
  case object LockTable                 extends Completion
  case object Notify                    extends Completion
  case object Reset                     extends Completion
  case object Rollback                  extends Completion
  case object Savepoint                 extends Completion
  case class  Select(count: Int)        extends Completion
  case object Set                       extends Completion
  case object Truncate                  extends Completion
  case object Unlisten                  extends Completion
  case class  Update(count: Int)        extends Completion
  case class  Insert(count: Int)        extends Completion
  case object CreateTable               extends Completion
  case object DropTable                 extends Completion
  case object AlterTable                extends Completion
  case object CreateSchema              extends Completion
  case object DropSchema                extends Completion
  case object CreateType                extends Completion
  case object DropType                  extends Completion
  case object AlterType                 extends Completion
  case object CreateFunction            extends Completion
  case object DropFunction              extends Completion
  case object AlterFunction             extends Completion
  case class  Copy(count: Int)          extends Completion
  case object Show                      extends Completion
  case object Do                        extends Completion
  case object CreateView                extends Completion
  case object DropView                  extends Completion
  case object CreateProcedure           extends Completion
  case object DropProcedure             extends Completion
  case object Call                      extends Completion
  case object CreateDomain              extends Completion
  case object DropDomain                extends Completion
  case object CreateSequence            extends Completion
  case object AlterSequence             extends Completion
  case object DropSequence              extends Completion
  case object CreateDatabase            extends Completion
  case object DropDatabase              extends Completion
  case object AlterDatabase             extends Completion
  case object CreateRole                extends Completion
  case object DropRole                  extends Completion
  case object AlterRole                 extends Completion
  case object CreateMaterializedView    extends Completion
  case object RefreshMaterializedView   extends Completion
  case object DropMaterializedView      extends Completion
  case object CreateExtension           extends Completion
  case object DropExtension             extends Completion
  case object CreateTrigger             extends Completion
  case object AlterTrigger              extends Completion
  case object DropTrigger               extends Completion
  case object SetConstraints            extends Completion
  case object Explain                   extends Completion
  case object Grant                     extends Completion
  case object Revoke                    extends Completion
  case object AlterIndex                extends Completion
  case class Merge(count: Int)          extends Completion
  case object CreatePolicy              extends Completion
  case object AlterPolicy               extends Completion
  case object DropPolicy                extends Completion
  case object Comment                   extends Completion
  case object Analyze                   extends Completion
  // more ...

  // weird Redshift variations

  /**
   * This is a variation on the protocol included to better support Redshift. Redshift
   * doesn't return a count for a <code>SELECT</code> statement. The
   * [[https://www.postgresql.org/docs/current/protocol-message-formats.html#PROTOCOL-MESSAGE-FORMATS-COMMANDCOMPLETE protocol documentation]]
   * appears to say it should:
   *
   * <blockquote>For a <code>SELECT</code> or <code>CREATE TABLE AS</code> command, the tag is <code>SELECT rows</code> where <code>rows</code> is the number of rows retrieved.</blockquote>
   *
   * but comments in the [[https://github.com/pgjdbc/pgjdbc/blob/5d1f2e8cd399cedfdee86728f9044c0e32d74129/pgjdbc/src/main/java/org/postgresql/core/CommandCompleteParser.java#L37-L44 JDBC driver]]
   * state
   *
   * <blockquote>
   * Parses <code>CommandComplete (B)</code> message.
   * Status is in the format of <code>COMMAND OID ROWS</code> where both <code>OID</code> and <code>ROWS</code> are optional
   * and <code>COMMAND</code> can have spaces within it, like <code>CREATE TABLE</code>.
   * </blockquote>
   *
   * so clients should probably be prepared to handle this.
   *
   * Practically speaking, true Postgres servers do return row counts, but Redshift does not.
   */
  case object SelectWithoutCount        extends Completion

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
