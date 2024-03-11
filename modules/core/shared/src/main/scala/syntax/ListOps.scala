// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.syntax

import cats.data.Ior
import scala.annotation.tailrec

final class ListOps[A](as: List[A]) {
  def align[B](bs: List[B]): List[A Ior B] = {
    @tailrec
    def go(as: List[A], bs: List[B], acc: List[A Ior B]): List[A Ior B] =
      (as, bs) match {
        case (a :: as, b :: bs) => go(as , bs , Ior.Both(a, b) :: acc)
        case (a :: as, Nil    ) => go(as , Nil, Ior.Left(a)    :: acc)
        case (Nil    , b :: bs) => go(Nil, bs , Ior.Right(b)    :: acc)
        case (Nil    , Nil    ) => acc.reverse
      }
    go(as, bs, Nil)
  }
}

trait ToListOps {
  implicit def toSkunkListOps[A](as: List[A]): ListOps[A] =
    new ListOps(as)
}

object list extends ToListOps
