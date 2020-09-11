// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import cats._
import skunk.implicits._

/**
 * A fragment applied to its argument, yielding an existentially-typed fragment + argument pair
 * that can be useful when constructing dynamic queries in application code. Applied fragments must
 * be deconstructed in order to prepare and execute.
 */
sealed trait AppliedFragment { outer =>

  type A
  def fragment: Fragment[A]
  def argument: A

  /** Concatenate this `AppliedFragment` with `other`, pairwise. */
  def |+|(other: AppliedFragment): AppliedFragment =
    AppliedFragment(fragment ~ other.fragment, argument ~ other.argument)

  override def toString =
    s"AppledFragment($fragment, $argument)"

}

object AppliedFragment {

  /** Construct an `AppliedFragment` from a `Fragment` and its argument. */
  def apply[A](fragment: Fragment[A], argument: A): AppliedFragment = {
    // dum dee dum
    type A0 = A
    val fragment0 = fragment
    val argument0 = argument
    new AppliedFragment {
      type A = A0
      val fragment = fragment0
      val argument = argument0
    }
  }

  /** The empty `AppliedFragment`. */
  lazy val empty: AppliedFragment =
    AppliedFragment(Fragment.empty, Void)

  /** `AppliedFragment` is a monoid. */
  implicit val MonoidAppFragment: Monoid[AppliedFragment] =
    new Monoid[AppliedFragment] {
      def combine(x: AppliedFragment, y: AppliedFragment): AppliedFragment = x |+| y
      def empty: AppliedFragment = AppliedFragment.empty
    }

}
