package skunk

case class Fragment[A](sql: String, encoder: Encoder[A]) {

  def query[B](decoder: Decoder[B]): Query[A, B] =
    Query(sql, encoder, decoder)

  def command: Command[A] =
    Command(sql, encoder)

}
