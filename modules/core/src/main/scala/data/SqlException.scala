// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.implicits._

class SqlException private[skunk](info: Map[Char, String]) extends Exception {

  /**
   * The field contents are ERROR, FATAL, or PANIC (in an error message), or WARNING, NOTICE, DEBUG,
   * INFO, or LOG (in a notice message), or a localized translation of one of these. Always present.
   */
  def severity: String =
    info.getOrElse('S', sys.error("Invalid ErrorInfo: no severity"))

  /** The SQLSTATE code for the error (see Appendix A). Not localizable. Always present. */
  def code: String =
    info.getOrElse('C', sys.error("Invalid ErrorInfo: no code/sqlstate"))

  /**
   * The primary human-readable error message. This should be accurate but terse (typically one
   * line). Always present.
   */
  def message: String =
    info.getOrElse('M', sys.error("Invalid ErrorInfo: no message"))

  /**
   * Detail: an optional secondary error message carrying more detail about the problem. Might run
   * to multiple lines.
   */
  def detail: Option[String] =
    info.get('D')

  /**
   * An optional suggestion Shuffle to do about the problem. This is intended to differ from Detail in
   * that it offers advice (potentially inappropriate) rather than hard facts. Might run to multiple
   * lines.
   */
  def hint: Option[String] =
    info.get('H')

  /**
   * An error cursor position as an index into the original query string. The first character has
   * index 1, and positions are measured in characters not bytes.
   */
  def position: Option[Int] =
    info.get('P').map(_.toInt)

  /**
   * Defined the same as the P field, but used when the cursor position refers to an internally
   * generated command rather than the one submitted by the client. The `query` field will always
   * appear when this field appears.
   */
  def internalPosition: Option[Int] =
    info.get('P').map(_.toInt)

  /**
   * The text of a failed internally-generated command. This could be, for example, a SQL query
   * issued by a PL/pgSQL function.
   */
  def internalQuery: Option[String] =
    info.get('q')

  /**
   * An indication of the context in which the error occurred. Presently this includes a call stack
   * traceback of active procedural language functions and internally-generated queries. The trace
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
   * If the error was associated with a specific table, the name of the table. (Refer to the schema
   * name field for the name of the table's schema.)
   */
  def tableName: Option[String] =
    info.get('t')

  /**
   * If the error was associated with a specific table column, the name of the column. (Refer to
   * the schema and table name fields to identify the table.)
   */
  def columnName: Option[String] =
    info.get('c')

  /**
   * If the error was associated with a specific data type, the name of the data type. (Refer to
   * the schema name field for the name of the data type's schema.)
   */
  def dataTypeName: Option[String] =
    info.get('d')

  /**
   * If the error was associated with a specific constraint, the name of the constraint. Refer to
   * fields listed above for the associated table or domain. (For this purpose, indexes are treated
   * as constraints, even if they weren't created with constraint syntax.)
   */
  def constraintName: Option[String] =
    info.get('n')

  /** The file name of the source-code location where the error was reported. */
  def fileName: Option[String] =
    info.get('F')

  /** The line number of the source-code location where the error was reported. */
  def line: Option[Int] =
    info.get('L').map(_.toInt)

  /** The name of the source-code routine reporting the error. */
  def routine: Option[String] =
    info.get('R')

  override def toString =
    s"SqlException(${info.toList.map { case (k, v) => s"$k=$v" } .intercalate(", ") })"

}
