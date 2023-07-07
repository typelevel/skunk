// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import skunk.data.Encoded

/**
  * Specifies how encoded values are redacted before being shown in exceptions and traces.
  */
sealed trait RedactionStrategy {
  def redactArguments(arguments: List[Option[Encoded]]): List[Option[Encoded]] =
    arguments.map(_.map(redactEncoded))

  def redactEncoded(encoded: Encoded): Encoded
}

object RedactionStrategy {
  /**
    * Values from encoders that have explicitly enabled redaction (via `.redact`)
    * are redacted and all other values are not.
    * 
    * This is the default strategy.
    */
  case object OptIn extends RedactionStrategy {
    def redactEncoded(encoded: Encoded): Encoded = encoded
  }

  /** All values are redacted, regardless of what encoders specify. */
  case object All extends RedactionStrategy {
    def redactEncoded(encoded: Encoded): Encoded = encoded.redact
  }

  /** No values are redacted, regardless of what encoders specify. */
  case object None extends RedactionStrategy {
    def redactEncoded(encoded: Encoded): Encoded = encoded.unredact
  }
}