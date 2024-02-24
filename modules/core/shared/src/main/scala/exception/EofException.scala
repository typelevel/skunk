// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

case class EofException(bytesRequested: Int, bytesRead: Int) extends SkunkException(
  sql       = None,
  message   = "EOF was reached on the network socket.",
  detail    = Some(s"Attempt to read $bytesRequested byte(s) failed after $bytesRead bytes(s) were read, because the connection had closed."),
  hint      = Some(s"Discard this session and retry with a new one."),
  sqlOrigin = None,
)