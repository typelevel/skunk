// Copyright (c) 2018-2021 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package skunk

import skunk.util.Origin
import skunk.data.Type

trait Statement[A] {
  def sql:      String
  def origin:   Origin
  def encoder:  Encoder[A]
  def cacheKey: Statement.CacheKey
}

object Statement {

  /**
   * An ordered digest of a `Statement`, consisting only of the SQL statement and asserted
   * input/output types. This data type has lawful universal equality/hashing so we can use it as
   * a hash key, which we do internally. There is probably little use for this in end-user code.
   */
  final case class CacheKey(sql: String, encodedTypes: List[Type], decodedTypes: List[Type])

}