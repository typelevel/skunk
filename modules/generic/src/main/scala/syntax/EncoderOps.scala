// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

// package skunk.generic.syntax

// import skunk.Encoder
// import skunk.generic.util.Twiddler

// final class EncoderOps[A](private val codec: Encoder[A]) extends AnyVal {
//   def gcontramap[B](implicit ev: Twiddler.Aux[B, A]): Encoder[B] =
//     codec.contramap(ev.to)
// }

// trait ToEncoderOps {
//   implicit def toEncoderOps[A](codec: Encoder[A]): EncoderOps[A] =
//     new EncoderOps(codec)
// }

// object encoder extends ToEncoderOps
