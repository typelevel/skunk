package skunk
package proto

trait ProtoQueryPortal[F[_], A] {
  def close: F[Unit]
  def execute(maxRows: Int): F[List[A] ~ Boolean]
}

