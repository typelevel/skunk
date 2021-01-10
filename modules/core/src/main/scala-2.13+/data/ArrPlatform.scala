// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

import scala.collection.mutable.ArrayBuffer
import scala.collection.Factory

/** Platform superclass to support `flattenTo` in both 2.12 and 2.13+. */
abstract class ArrPlatform[A] {

  protected def data: ArrayBuffer[A]

  /**
   * Construct this `Arr`'s elements as a collection `C`, as if first reshaped to be
   * single-dimensional.
   */
  def flattenTo[C](fact: Factory[A, C]): C =
    data.to(fact)

}