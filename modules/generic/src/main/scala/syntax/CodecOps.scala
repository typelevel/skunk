// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

// package skunk.generic.syntax

// import skunk.Codec
// import skunk.generic.util.Twiddler

// final class CodecOps[A](private val codec: Codec[A]) extends AnyVal {
//   def gimap[B](implicit ev: Twiddler.Aux[B, A]): Codec[B] =
//     codec.imap(ev.from)(ev.to)
// }

// trait ToCodecOps {
//   implicit def toCodecOps[A](codec: Codec[A]): CodecOps[A] =
//     new CodecOps(codec)
// }

// object codec extends ToCodecOps
