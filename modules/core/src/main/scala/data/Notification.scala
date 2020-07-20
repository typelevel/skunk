// Copyright (c) 2018-2020 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk
package data

import cats._
import cats.implicits._

final case class Notification[A](pid: Int, channel: Identifier, value: A) {
  def map[B](f: A => B): Notification[B] = copy(value = f(value))
}

object Notification {

  implicit val TraverseNotification: Traverse[Notification] =
    new Traverse[Notification] {
      def foldLeft[A, B](fa: Notification[A], b: B)(f: (B, A) => B): B = f(b, fa.value)
      def foldRight[A, B](fa: Notification[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = f(fa.value, lb)
      def traverse[G[_]: Applicative, A, B](fa: Notification[A])(f: A => G[B]): G[Notification[B]] = f(fa.value).map(b => fa.copy(value = b))
      override def map[A, B](fa: Notification[A])(f: A => B): Notification[B] = fa.map(f)
    }

}