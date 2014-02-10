package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

sealed abstract class Response {
  def sender: Node
  def echoId: Id
}

case class PingResponse(sender: Node, echoId: Id) extends Response


