package skunk
package codec

import skunk.data.Type

trait TextCodecs {

  val varchar: Codec[String] = Codec.simple(_.toString, _.toString, Type.varchar)
  val name:    Codec[String] = Codec.simple(_.toString, _.toString, Type.name)
  val bpchar:  Codec[String] = Codec.simple(_.toString, _.toString, Type.bpchar)

}

object text extends TextCodecs