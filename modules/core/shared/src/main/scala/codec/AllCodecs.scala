// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.codec

trait AllCodecs
  extends NumericCodecs
     with TextCodecs
     with TemporalCodecs
     with BooleanCodec
     with EnumCodec
     with UuidCodec
     with BinaryCodecs
     with LTreeCodec

object all extends AllCodecs
