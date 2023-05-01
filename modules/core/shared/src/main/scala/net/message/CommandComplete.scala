// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.net.message

import scodec.Decoder
import skunk.data.Completion

import scala.util.matching.Regex

/**
 * Command-completed response. The command tag is usually a single word that identifies which SQL
 * command was completed.
 *
 * - For an INSERT command, the tag is `INSERT <oid> <rows>`, where rows is the number of rows inserted.
 *   oid is the object ID of the inserted row if rows is 1 and the target table has OIDs; otherwise
 *   oid is 0.
 * - For a DELETE command, the tag is DELETE rows where rows is the number of rows deleted.
 * - For an UPDATE command, the tag is UPDATE rows where rows is the number of rows updated.
 * - For a SELECT or CREATE TABLE AS command, the tag is SELECT rows where rows is the number of
 *   rows retrieved.
 * - For a MOVE command, the tag is MOVE rows where rows is the number of rows the cursor's position
 *   has been changed by.
 * - For a FETCH command, the tag is FETCH rows where rows is the number of rows that have been
 *   retrieved from the cursor.
 * - For a COPY command, the tag is COPY rows where rows is the number of rows copied. (Note: the
 *   row count appears only in PostgreSQL 8.2 and later.)
 *
 * @param completion The command tag.
 */
case class CommandComplete(completion: Completion) extends BackendMessage

object CommandComplete {

  final val Tag = 'C'

  private object Patterns {
    val Select: Regex = """SELECT (\d+)""".r
    val Delete: Regex = """DELETE (\d+)""".r
    val Update: Regex = """UPDATE (\d+)""".r
    val Insert: Regex = """INSERT (\d+ \d+)""".r
    val Copy:   Regex = """COPY (\d+)""".r
  }

  //TODO: maybe make lazy val
  def decoder: Decoder[CommandComplete] = utf8z.map {
    case "BEGIN"                      => apply(Completion.Begin)
    case "COMMIT"                     => apply(Completion.Commit)
    case "CREATE INDEX"               => apply(Completion.CreateIndex)
    case "DROP INDEX"                 => apply(Completion.DropIndex)
    case "LISTEN"                     => apply(Completion.Listen)
    case "LOCK TABLE"                 => apply(Completion.LockTable)
    case "NOTIFY"                     => apply(Completion.Notify)
    case "RESET"                      => apply(Completion.Reset)
    case "SET"                        => apply(Completion.Set)
    case "TRUNCATE TABLE"             => apply(Completion.Truncate)
    case "UNLISTEN"                   => apply(Completion.Unlisten)
    case "ROLLBACK"                   => apply(Completion.Rollback)
    case "SAVEPOINT"                  => apply(Completion.Savepoint)
    case "CREATE TABLE"               => apply(Completion.CreateTable)
    case "DROP TABLE"                 => apply(Completion.DropTable)
    case "ALTER TABLE"                => apply(Completion.AlterTable)
    case "CREATE SCHEMA"              => apply(Completion.CreateSchema)
    case "DROP SCHEMA"                => apply(Completion.DropSchema)
    case "CREATE TYPE"                => apply(Completion.CreateType)
    case "DROP TYPE"                  => apply(Completion.DropType)
    case "ALTER TYPE"                 => apply(Completion.AlterType)
    case "CREATE FUNCTION"            => apply(Completion.CreateFunction)
    case "DROP FUNCTION"              => apply(Completion.DropFunction)
    case "SHOW"                       => apply(Completion.Show)
    case "DO"                         => apply(Completion.Do)
    case "CREATE PROCEDURE"           => apply(Completion.CreateProcedure)
    case "DROP PROCEDURE"             => apply(Completion.DropProcedure)
    case "CALL"                       => apply(Completion.Call)
    case "CREATE VIEW"                => apply(Completion.CreateView)
    case "DROP VIEW"                  => apply(Completion.DropView)
    case "CREATE DOMAIN"              => apply(Completion.CreateDomain)
    case "DROP DOMAIN"                => apply(Completion.DropDomain)
    case "CREATE SEQUENCE"            => apply(Completion.CreateSequence)
    case "ALTER SEQUENCE"             => apply(Completion.AlterSequence)
    case "DROP SEQUENCE"              => apply(Completion.DropSequence)
    case "CREATE DATABASE"            => apply(Completion.CreateDatabase)
    case "DROP DATABASE"              => apply(Completion.DropDatabase)
    case "CREATE ROLE"                => apply(Completion.CreateRole)
    case "DROP ROLE"                  => apply(Completion.DropRole)
    case "CREATE MATERIALIZED VIEW"   => apply(Completion.CreateMaterializedView)
    case "REFRESH MATERIALIZED VIEW"  => apply(Completion.RefreshMaterializedView)
    case "DROP MATERIALIZED VIEW"     => apply(Completion.DropMaterializedView)
    case "CREATE EXTENSION"           => apply(Completion.CreateExtension)
    case "DROP EXTENSION"             => apply(Completion.DropExtension)
    case "CREATE TRIGGER"             => apply(Completion.CreateTrigger)
    case "ALTER TRIGGER"              => apply(Completion.AlterTrigger)
    case "DROP TRIGGER"               => apply(Completion.DropTrigger)
    case Patterns.Select(s)           => apply(Completion.Select(s.toInt))
    case Patterns.Delete(s)           => apply(Completion.Delete(s.toInt))
    case Patterns.Update(s)           => apply(Completion.Update(s.toInt))
    case Patterns.Insert(s)           => apply(Completion.Insert(s.drop(2).toInt))
    case Patterns.Copy(s)             => apply(Completion.Copy(s.toInt))
    case "SET CONSTRAINTS"            => apply(Completion.SetConstraints)
    // more .. fill in as we hit them

    case s                  => apply(Completion.Unknown(s))
  }

}
