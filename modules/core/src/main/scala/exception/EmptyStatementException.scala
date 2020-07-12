// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import skunk.Statement

class EmptyStatementException(stmt: Statement[_]) extends SkunkException(
  message   = "Query is empty.",
  sql       = Some(stmt.sql),
  sqlOrigin = Some(stmt.origin),
)