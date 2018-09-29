package skunk

final case class Query[A, B](
  sql:     String,
  encoder: Encoder[A],
  decoder: Decoder[B]
)
