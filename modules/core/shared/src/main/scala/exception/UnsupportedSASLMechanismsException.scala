// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.exception

class UnsupportedSASLMechanismsException(
  mechanisms: List[String]
) extends SkunkException(
  sql = None,
  message = "No compatible SASL mechanisms.",
  hint = Some(
    s"""|The server supports SASL authentication mechanisms ${mechanisms.mkString(", ")} but Skunk currently only supports `SCRAM-SHA-256`.
        |""".stripMargin)
)