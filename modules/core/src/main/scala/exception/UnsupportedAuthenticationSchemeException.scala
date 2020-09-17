// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

import skunk.net.message.BackendMessage

class UnsupportedAuthenticationSchemeException(
  message: BackendMessage
) extends SkunkException(
  sql = None,
  message = s"Unsupported authentication scheme.",
  hint = Some(
    s"""|The server requested `$message`, but Skunk currently only supports `trust` and `password` (md5 and scram-sha-256).
        |""".stripMargin
  )
)