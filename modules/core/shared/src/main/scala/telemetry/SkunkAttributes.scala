// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

import org.typelevel.otel4s.{Attribute, AttributeKey}

private[skunk] object SkunkAttributes {

  object Keys {
    val OperationName = AttributeKey[String]("skunk.operation.name")
    val PortalId = AttributeKey[String]("skunk.portal.id")
    val StatementId = AttributeKey[String]("skunk.statement.id")
    val StatementParameterTypes = AttributeKey[String]("skunk.statement.parameter_types")
    val ResultColumnTypes = AttributeKey[String]("skunk.result.column_types")
    val FetchMaxRows = AttributeKey[Long]("skunk.fetch.max_rows")
    val ResponseRowCount = AttributeKey[Long]("skunk.response.row_count")
    val ResponseMoreRows = AttributeKey[Boolean]("skunk.response.more_rows")
  }

  def operationName(value: String): Attribute[String] = Keys.OperationName(value)
  def portalId(value: String): Attribute[String] = Keys.PortalId(value)
  def statementId(value: String): Attribute[String] = Keys.StatementId(value)
  def statementParameterTypes(value: String): Attribute[String] = Keys.StatementParameterTypes(value)
  def resultColumnTypes(value: String): Attribute[String] = Keys.ResultColumnTypes(value)
  def fetchMaxRows(value: Long): Attribute[Long] = Keys.FetchMaxRows(value)
  def responseRowCount(value: Long): Attribute[Long] = Keys.ResponseRowCount(value)
  def responseMoreRows(value: Boolean): Attribute[Boolean] = Keys.ResponseMoreRows(value)
}
