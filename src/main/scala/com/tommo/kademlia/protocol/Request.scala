package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

sealed abstract class Request 
case class PingRequest(self: AbstractNode, randomId: Id) extends Request
