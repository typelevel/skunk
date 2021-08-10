// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import skunk.Statement

final case class TooManyParametersException(
  query: Statement[_],
) extends SkunkException(
  sql       = Some(query.sql.take(80) + "..."),
  message   = s"Statement has more than ${Short.MaxValue} parameters.",
  hint      = Some(s"Postgres can't handle this many parameters. Execute multiple statements instead."),
  sqlOrigin = Some(query.origin),
)