package skunk
package proto
package message

import cats.implicits._
import scodec._
import scodec.codecs._
import scodec.interop.cats._


// ParameterDescription (B)
// Byte1('t')
// Identifies the message as a parameter description.

// Int32
// Length of message contents in bytes, including self.

// Int16
// The number of parameters used by the statement (can be zero).

// Then, for each parameter, there is the following:

// Int32
// Specifies the object ID of the parameter data type.


case class ParameterDescription(oids: List[Int]) extends BackendMessage

object ParameterDescription {

  // Byte1('T') - Identifies the message as a Parameter description.
  val Tag = 't'

  val decoder: Decoder[ParameterDescription] =
    int16.flatMap { n =>
      int32.asDecoder.replicateA(n).map(ParameterDescription(_))
    }

}