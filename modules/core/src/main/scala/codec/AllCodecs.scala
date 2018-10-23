package skunk.codec

trait AllCodecs
  extends NumericCodecs
     with TextCodecs

object all extends AllCodecs