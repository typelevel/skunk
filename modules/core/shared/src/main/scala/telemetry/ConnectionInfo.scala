// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

sealed trait ConnectionInfo {
  def database: String
  def serverAddress: String
  def serverPort: Option[Long]
}

object ConnectionInfo {

  def apply(
      database: String,
      serverAddress: String,
      serverPort: Option[Long]
  ): ConnectionInfo =
    Impl(database, serverAddress, serverPort)

  private final case class Impl(
      database: String,
      serverAddress: String,
      serverPort: Option[Long]
  ) extends ConnectionInfo
}
