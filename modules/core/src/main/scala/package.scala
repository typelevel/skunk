package object skunk {

  private[skunk] def void(a: Any): Unit = (a, ())._2

  type ~[+A, +B] = (A, B)
  object ~ {
    def unapply[A, B](t: (A, B)): Some[(A, B)] = Some(t)
  }
  implicit class AnyTwiddleOps[A](a: A) {
    def ~[B](b: B): (A, B) = (a, b)
  }

  type Void

  object implicits
    extends ToStringOps

}

