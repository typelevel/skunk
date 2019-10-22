// Copyright (c) 2018 by Rob Norris
// This software is licensed under the MIT License (MIT).
// For more information see LICENSE or https://opensource.org/licenses/MIT

package foo.bar // try with other package declarations, including package foo; packagae bar

object Test {

  case class City(id: Int, name: String, code: String, district: String, pop: Int)

  val t: Twiddler.Aux[City, ((((Int, String), String), String), Int)] = Twiddler[City]

}