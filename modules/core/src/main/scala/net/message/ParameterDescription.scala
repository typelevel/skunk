package skunk.net.message

import cats.implicits._
import scodec._
import scodec.codecs._
import scodec.interop.cats._

case class ParameterDescription(oids: List[Int]) extends BackendMessage

object ParameterDescription {

  final val Tag = 't'

  val decoder: Decoder[ParameterDescription] =
    int16.flatMap { n =>
      int32.asDecoder.replicateA(n).map(ParameterDescription(_))
    }

}