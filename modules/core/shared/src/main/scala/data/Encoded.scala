// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import cats.kernel.Eq

final case class Encoded(value: String, redacted: Boolean) {
  def redact: Encoded = if (redacted) this else Encoded(value, true)
  def unredact: Encoded = if (!redacted) this else Encoded(value, false)
  override def toString: String = if (redacted) Encoded.RedactedText else value
}

object Encoded {
  def apply(value: String): Encoded = Encoded(value, false)

  final val RedactedText: String = "?"

  implicit val eqInstance: Eq[Encoded] = Eq.fromUniversalEquals[Encoded]
}
