package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import akka.actor.{Actor, ActorRef}

import akka.testkit.TestActorRef
import akka.testkit.TestProbe

class SenderActorTest extends BaseTestKit("SenderSpec") {
  
	class MockRequest extends Request {
	  val sender = mockZeroId(4)
	}
  
//	"A SenderActor" should "upon receiving a request get an AuthSenderActor" in {
//		var providerCalled = false
//	  
//		trait MockAuthProvider extends AuthProvider {
//		  override def authSender(id: Id, kBucketActor: ActorRef, node: ActorRef, toForward: ActorRef): Actor = {
//			providerCalled = true
//		    wrapTestActor
//		  }
//		}
//		
//		val authSender = TestActorRef[SenderActor](new SenderActor(testActor) with MockAuthProvider)
//		
//		authSender ! new MockRequest
//		
//		awaitCond(providerCalled)
//		
//	}
}