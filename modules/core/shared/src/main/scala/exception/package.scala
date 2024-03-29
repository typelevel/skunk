// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

package object exception {

  private[exception] def framed(s: String): String =
    "\u001B[4m" + s + "\u001B[24m"

}