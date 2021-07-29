// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import skunk.Query

final case class NoDataException(
  query: Query[_, _],
) extends SkunkException(
  sql       = Some(query.sql),
  message   = "Statement does not return data.",
  hint      = Some(s"This ${framed("query")} returns no row. Use a ${framed("command")} instead."),
  sqlOrigin = Some(query.origin),
)