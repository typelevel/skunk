// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package tests.codec

import skunk.codec.all._
import skunk.refined.codec.syntax._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.types.numeric.{PosInt, NonNaNDouble}
import eu.timepit.refined.numeric.{Positive, NonNaN}
import eu.timepit.refined.cats._

class RefinedCodecTest extends CodecTest {

  roundtripTest(varchar.refine[NonEmpty])(NonEmptyString.unsafeFrom("foo"))
  roundtripTest(int4.refine[Positive])(PosInt.unsafeFrom(42))
  roundtripTest(float8.refine[NonNaN])(NonNaNDouble.unsafeFrom(2.0))

}
