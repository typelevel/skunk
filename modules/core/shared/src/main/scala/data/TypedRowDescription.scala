// Copyright (c) 2018-2024 by Rob Norris and Contributors
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk.data

final case class TypedRowDescription(fields: List[TypedRowDescription.Field]) {
  def types: List[Type] = fields.map(_.tpe)
}
object TypedRowDescription {
  case class Field(name: String, tpe: Type)
}
