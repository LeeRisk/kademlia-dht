package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

sealed abstract class Request {
  def self: Node
  def toEchoId: Id
}
case class PingRequest(self: Node, toEchoId: Id) extends Request
