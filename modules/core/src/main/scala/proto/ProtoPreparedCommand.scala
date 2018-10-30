package skunk.proto

trait ProtoPreparedCommand[F[_], A] {
  def bind(args: A): F[ProtoCommandPortal[F]]
  def check: F[Unit]
  def close: F[Unit]
}