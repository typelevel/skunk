package skunk.proto

trait ProtoPreparedQuery[F[_], A, B] {
  def close: F[Unit]
  def check: F[Unit]
  def bind(args: A): F[ProtoQueryPortal[F, B]]
}
