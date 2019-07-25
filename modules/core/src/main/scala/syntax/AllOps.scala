// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.syntax

// format: off
trait ToAllOps
  extends ToIdOps
     with ToStringContextOps
     with ToListOps
// format: on

object all extends ToAllOps