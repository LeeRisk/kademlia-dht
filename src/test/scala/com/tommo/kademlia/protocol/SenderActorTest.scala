package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.SenderActor._

import akka.actor.{ Actor, ActorRef }

import akka.testkit.TestActorRef
import akka.testkit.TestProbe

class SenderActorTest extends BaseTestKit("SenderSpec") {

  trait Fixture extends BaseProtocolFixture {
    val someProbe = TestProbe()
    val kBucketProbe = TestProbe()

    var providerCalled = false
    
    trait MockAuthProvider extends AuthActor.Provider {
      override def authSender(id: Id, kBucketActor: ActorRef, node: ActorRef) = {
        providerCalled = true
        wrapActorRef(someProbe.ref)
      }
    }
    
    val verifyRef = TestActorRef[SenderActor](new SenderActor(mockZeroId(4), kBucketProbe.ref) with MockAuthProvider)
  }

  "A SenderActor" should "upon receiving a node request, create actor returned from authSender " in new Fixture {
    verifyRef ! NodeRequest(someProbe.ref, MockRequest())
    awaitCond(providerCalled)
  }
  
  it should "forward the request using the sender" in new Fixture { 
	  verifyRef ! NodeRequest(testActor, MockRequest())
	  someProbe.expectMsgClass(classOf[Request])
	  someProbe.lastSender == testActor
  }
}