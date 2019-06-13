// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import cats.implicits._
import skunk.SqlState
import skunk.data.Type
import skunk.util.Origin

// TODO: turn this into an ADT of structured error types
class PostgresErrorException private[skunk](
  sql:             String,
  sqlOrigin:       Option[Origin],
  info:            Map[Char, String],
  history:         List[Either[Any, Any]],
  arguments:       List[(Type, Option[String])] = Nil,
  argumentsOrigin: Option[Origin]               = None
) extends SkunkException(
  sql       = Some(sql),
  message   = {
    val m = info.getOrElse('M', sys.error("Invalid ErrorInfo: no message"))
    m.take(1).toUpperCase + m.drop(1) + "."
  },
  position        = info.get('P').map(_.toInt),
  detail          = info.get('D'),
  hint            = info.get('H'),
  history         = history,
  arguments       = arguments,
  sqlOrigin       = sqlOrigin,
  argumentsOrigin = argumentsOrigin,
) {

  override def fields = {
    var map = super.fields

    map += "error.postgres.message"  -> message
    map += "error.postgres.severity" -> severity
    map += "error.postgres.code"     -> code

    internalPosition.foreach(a => map += "error.postgres.internalPosition" -> a)
    internalQuery   .foreach(a => map += "error.postgres.internalQuery"    -> a)
    where           .foreach(a => map += "error.postgres.where"            -> a)
    schemaName      .foreach(a => map += "error.postgres.schemaName"       -> a)
    tableName       .foreach(a => map += "error.postgres.tableName"        -> a)
    columnName      .foreach(a => map += "error.postgres.columnName"       -> a)
    dataTypeName    .foreach(a => map += "error.postgres.dataTypeName"     -> a)
    constraintName  .foreach(a => map += "error.postgres.constraintName"   -> a)
    fileName        .foreach(a => map += "error.postgres.fileName"         -> a)
    line            .foreach(a => map += "error.postgres.line"             -> a)
    routine         .foreach(a => map += "error.postgres.routine"          -> a)

    map
  }

  /**
   * The field contents are ERROR, FATAL, or PANIC (in an error message), or WARNING, NOTICE, DEBUG,
   * INFO, or LOG (in a notice message), or a localized translation of one of these .Always present.
   */
  def severity: String =
    info.getOrElse('S', sys.error("Invalid ErrorInfo: no severity"))

  /** The SQLSTATE code for the error (see Appendix A) .Not localizable .Always present .*/
  def code: String =
    info.getOrElse('C', sys.error("Invalid ErrorInfo: no code/sqlstate"))

  /**
   * Defined the same as the P field, but used when the cursor position refers to an internally
   * generated command rather than the one submitted by the client .The `query` field will always
   * appear when this field appears.
   */
  def internalPosition: Option[Int] =
    info.get('P').map(_.toInt)

  /**
   * The text of a failed internally-generated command .This could be, for example, a SQL query
   * issued by a PL/pgSQL function.
   */
  def internalQuery: Option[String] =
    info.get('q')

  /**
   * An indication of the context in which the error occurred .Presently this includes a call stack
   * traceback of active procedural language functions and internally-generated queries .The trace
   * is one entry per line, most recent first.
   */
  def where: Option[String] =
    info.get('w')

  /**
   * If the error was associated with a specific database object, the name of the schema containing
   * that object, if any.
   */
  def schemaName: Option[String] =
    info.get('s')

  /**
   * If the error was associated with a specific table, the name of the table .(Refer to the schema
   * name field for the name of the table's schema.)
   */
  def tableName: Option[String] =
    info.get('t')

  /**
   * If the error was associated with a specific table column, the name of the column .(Refer to
   * the schema and table name fields to identify the table.)
   */
  def columnName: Option[String] =
    info.get('c')

  /**
   * If the error was associated with a specific data type, the name of the data type .(Refer to
   * the schema name field for the name of the data type's schema.)
   */
  def dataTypeName: Option[String] =
    info.get('d')

  /**
   * If the error was associated with a specific constraint, the name of the constraint .Refer to
   * fields listed above for the associated table or domain .(For this purpose, indexes are treated
   * as constraints, even if they weren't created with constraint syntax.)
   */
  def constraintName: Option[String] =
    info.get('n')

  /** The file name of the source-code location where the error was reported .*/
  def fileName: Option[String] =
    info.get('F')

  /** The line number of the source-code location where the error was reported .*/
  def line: Option[Int] =
    info.get('L').map(_.toInt)

  /** The name of the source-code routine reporting the error .*/
  def routine: Option[String] =
    info.get('R')


  // These will likely get abstracted up and out, but for now we'll do it here in a single
  // error class.

  override def title: String = {
    val pgSource = (fileName, line, routine).mapN((f, l, r) => s"raised in $r ($f:$l)")
    s"Postgres ${severity} $code ${pgSource.orEmpty}"
  }

  private def trap: String =
    SqlState.values.find(_.code == code).foldMap { st =>
      s"""|If this is an error you wish to trap and handle in your application, you can do
          |so with a SqlState extractor .For example:
          |
          |  ${Console.GREEN}doSomething.recoverWith { case SqlState.${st.entryName}(ex) =>  ...}${Console.RESET}
          |
          |""".stripMargin
    }

  // private def errorResponse: String =
  //   if (info.isEmpty) "" else
  //   s"""|ErrorResponse map:
  //       |
  //       |  ${info.toList.map { case (k, v) => s"$k = $v" } .mkString("\n|  ")}
  //       |
  //       |""".stripMargin

  override def sections =
    List(header, statement, args, trap) //, exchanges, errorResponse)

}

object PostgresErrorException {

  def raiseError[F[_]: cats.MonadError[?[_], Throwable], A](
  sql:             String,
  sqlOrigin:       Option[Origin],
  info:            Map[Char, String],
  history:         List[Either[Any, Any]],
  arguments:       List[(Type, Option[String])] = Nil,
  argumentsOrigin: Option[Origin]               = None
  ): F[A] =
    new PostgresErrorException(sql, sqlOrigin, info, history, arguments, argumentsOrigin)
      .raiseError[F, A]


}
