// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

object feature {
  implicit lazy val legacyCommandSyntax: featureFlags.legacyCommandSyntax = featureFlags.legacyCommandSyntax
}

object featureFlags {
  sealed trait legacyCommandSyntax
  object legacyCommandSyntax extends legacyCommandSyntax
}