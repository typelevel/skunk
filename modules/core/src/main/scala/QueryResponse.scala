package skunk

import cats.data.Chain
import skunk.proto.message._

sealed trait QueryResponse
object QueryResponse {

  case class Error(err: ErrorResponse) extends QueryResponse
  case class Notice(notice: NoticeResponse) extends QueryResponse
  case class Complete(completion: CommandComplete, rows: Option[RowSet]) extends QueryResponse

  // TODO: CopyIn, CopyOut

  case class RowSet(description: RowDescription, data: Chain[RowData])


}