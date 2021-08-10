// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import skunk.Command

final case class UnexpectedDataException(
  command: Command[_],
) extends SkunkException(
  sql       = Some(command.sql),
  message   = "Command returns data.",
  hint      = Some(s"This ${framed("command")} returns row data. Use a ${framed("query")} instead."),
  sqlOrigin = Some(command.origin),
)