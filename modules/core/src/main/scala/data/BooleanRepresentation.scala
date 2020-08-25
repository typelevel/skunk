// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

/** Enumerated types of valid boolean types. See the companion object for more information. */
sealed abstract class TrueBooleanRepresentation(private[skunk] val sql: String) extends Product with Serializable
sealed abstract class FalseBooleanRepresentation(private[skunk] val sql: String) extends Product with Serializable

object BooleanRepresentation {
    case object True extends TrueBooleanRepresentation("true")  

    case object False extends FalseBooleanRepresentation("false")

    case object T extends TrueBooleanRepresentation("y")

    case object F extends FalseBooleanRepresentation("f")

    case object Yes extends TrueBooleanRepresentation("yes")

    case object No extends FalseBooleanRepresentation("no")

    case object Y extends TrueBooleanRepresentation("y")

    case object N extends FalseBooleanRepresentation("n")

    case object On extends TrueBooleanRepresentation("on")
    
    case object Off extends FalseBooleanRepresentation("off")

    case object `1` extends TrueBooleanRepresentation("1")

    case object `0` extends FalseBooleanRepresentation("0")
}