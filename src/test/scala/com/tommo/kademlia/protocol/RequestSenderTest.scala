package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._

import akka.actor.{ Actor, ActorRef, Props }

import akka.testkit.TestActorRef
import akka.testkit.TestProbe

import org.mockito.Matchers._
import org.mockito.Mockito._

class RequestSenderTest extends BaseTestKit("SenderSpec") with BaseProtocolFixture {
  val someProbe = TestProbe()
  val kBucketProbe = TestProbe()

  lazy val mockProvider = mock[AuthActor.Provider]

  trait MockAuthProvider extends AuthActor.Provider {
    override def authSender(id: Id, kBucketActor: ActorRef, node: ActorRef) = { mockProvider.authSender(id, kBucketActor, node); wrapActorRef(someProbe.ref) }
  }
  
  val verifyRef = TestActorRef[RequestSenderActor](Props(new RequestSenderActor(mockZeroId(4), kBucketProbe.ref) with MockAuthProvider))
		  
  test("upon receiving a node request, create actor returned from authSender") {
    verifyRef ! NodeRequest(someProbe.ref, MockRequest())
    awaitAssert(verify(mockProvider).authSender(mockZeroId(4), kBucketProbe.ref, someProbe.ref))
  }

  test("forward the request using the sender") {
    verifyRef ! NodeRequest(testActor, MockRequest())
    someProbe.expectMsgClass(classOf[Request])
    someProbe.lastSender == testActor
  }
}