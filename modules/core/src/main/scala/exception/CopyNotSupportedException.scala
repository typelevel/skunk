// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import skunk.Statement

class CopyNotSupportedException(stmt: Statement[_]) extends SkunkException(
  message   = "COPY is not yet supported, sorry.",
  sql       = Some(stmt.sql),
  sqlOrigin = Some(stmt.origin),
)