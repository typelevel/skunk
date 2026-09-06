// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.telemetry

private[skunk] final case class ConnectionInfo(
    database: String,
    serverAddress: String,
    serverPort: Option[Long]
)
