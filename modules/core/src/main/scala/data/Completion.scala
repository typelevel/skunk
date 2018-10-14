package skunk.data

sealed abstract class Completion
object Completion {
  case object Listen             extends Completion
  case object Notify             extends Completion
  case object Unlisten           extends Completion
  case object Set                extends Completion
  case object Reset              extends Completion
  case class  Select(count: Int) extends Completion
  // more ...
}