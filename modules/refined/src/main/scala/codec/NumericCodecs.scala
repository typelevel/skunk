// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.refined.codec

import eu.timepit.refined.boolean.And
import eu.timepit.refined.api.Validate
import skunk.Codec
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import skunk.data.Type
import eu.timepit.refined._
import eu.timepit.refined.api.Refined

trait Scale[N]
trait Precision[N]

trait NumericCodecs {

  def validateScale[N <: Int](n: Int): Validate.Plain[BigDecimal, Scale[N]] =
    Validate.fromPredicate[BigDecimal, Scale[N]](
      _.scale <= n, d => s"($d has scale ≤ $n)", new Scale[N] {})

  def validatePrecision[N <: Int](n: Int): Validate.Plain[BigDecimal, Precision[N]] =
    Validate.fromPredicate[BigDecimal, Precision[N]](
      _.precision <= n, d => s"($d has precision ≤ $n)", new Precision[N] {})

  implicit class NumericOps(num: Codec[BigDecimal]) {
    def apply[S <: Int, P <: Int](precision: P, scale: S): Codec[BigDecimal Refined (Precision[P] And Scale[S])] = {
      implicit val __s: Validate.Plain[BigDecimal, Scale[S]] = validateScale(scale)
      implicit val __p: Validate.Plain[BigDecimal, Precision[P]] = validatePrecision(precision)
      Codec.simple(
        bd => bd.toString,
        st => refineV[Precision[P] And Scale[S]](BigDecimal(st)),
        Type.numeric
      )
    }
  }

  skunk.codec.numeric.numeric(3, 4)

}

object numeric extends NumericCodecs