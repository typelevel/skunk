package skunk

/**
 * A singly-inhabited type representing arguments to a parameterless statement.
 * @group Commands and Queries
 */
sealed trait Void

/**
 * @group Commands and Queries
 */
case object Void extends Void {

  val codec: Codec[Void] =
    new Codec[Void] {
      def encode(a: Void) = Nil
      def decode(ss: List[Option[String]]) = Void
      val oids = Nil
    }

}
