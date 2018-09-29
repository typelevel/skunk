
package object skunk
  extends ToStringOps {

  private[skunk] def void(a: Any): Unit = (a, ())._2

}

