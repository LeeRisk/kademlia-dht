package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

abstract sealed class Response

case class PingResponse(sender: AbstractNode, echoId: Id) extends Response


