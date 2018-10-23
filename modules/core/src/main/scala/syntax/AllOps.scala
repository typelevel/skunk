package skunk.syntax

trait ToAllOps
  extends ToIdOps
     with ToStringContextOps

object all extends ToAllOps