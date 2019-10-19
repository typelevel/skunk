// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

sealed abstract class Completion
object Completion {
  case object Begin              extends Completion
  case object Commit             extends Completion
  case class  Delete(count: Int) extends Completion
  case object Listen             extends Completion
  case object Notify             extends Completion
  case object Reset              extends Completion
  case object Rollback           extends Completion
  case object Savepoint          extends Completion
  case class  Select(count: Int) extends Completion
  case object Set                extends Completion
  case object Unlisten           extends Completion
  case class  Update(count: Int) extends Completion
  case object Insert             extends Completion
  // more ...
}
