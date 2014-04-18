package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id

trait BaseProtocolFixture {
  	case class MockRequest(val sender: Id = Id("1")) extends Request 
  	case class MockReply(val sender: Id = Id("1")) extends Reply
}