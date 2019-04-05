package skunk.exception

import skunk.Query

case class NoDataException(
  query: Query[_, _],
) extends SkunkException(
  sql       = query.sql,
  message   = "Statement does not return data.",
  hint      = Some(s"This ${framed("query")} returns no rows and should be a ${framed("command")}."),
  sqlOrigin = query.origin,
)