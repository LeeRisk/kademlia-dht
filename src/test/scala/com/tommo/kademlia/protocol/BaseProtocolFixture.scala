package com.tommo.kademlia.protocol

import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.BaseFixture

trait BaseProtocolFixture extends BaseFixture {
  	case class MockRequest(val sender: Id = Id("1")) extends Request 
  	case class MockReply(val sender: Id = Id("1")) extends Reply
}