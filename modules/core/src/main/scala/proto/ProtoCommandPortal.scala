package skunk.proto

import skunk.data.Completion

trait ProtoCommandPortal[F[_]] {
  def close: F[Unit]
  def execute: F[Completion]
}

