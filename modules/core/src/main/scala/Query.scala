package skunk

import cats.arrow.Profunctor

final case class Query[A, B](sql: String, encoder: Encoder[A], decoder: Decoder[B]) {

  def dimap[C, D](f: C => A)(g: B => D): Query[C,D] =
    Query(sql, encoder.contramap(f), decoder.map(g))

}

object Query {

  implicit val ProfunctorQuery: Profunctor[Query] =
    new Profunctor[Query] {
      def dimap[A, B, C, D](fab: Query[A,B])(f: C => A)(g: B => D) =
        fab.dimap(f)(g)
    }

}