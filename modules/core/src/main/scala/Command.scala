package skunk

final case class Command[A](
  sql:     String,
  encoder: Encoder[A]
)
