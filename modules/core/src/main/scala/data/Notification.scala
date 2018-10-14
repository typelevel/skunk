package skunk
package data

final case class Notification(pid: Int, channel: Identifier, value: String)