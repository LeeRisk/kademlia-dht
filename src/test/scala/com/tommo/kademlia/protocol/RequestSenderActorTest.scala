package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestSenderActor._

import akka.actor.{ Actor, ActorRef, Props }

import akka.testkit.TestActorRef
import akka.testkit.TestProbe

import org.mockito.Matchers._
import org.mockito.Mockito._
import scala.concurrent.duration.Duration
class RequestSenderTest extends BaseTestKit("SenderSpec") with BaseProtocolFixture {
  val someProbe = TestProbe()
  val kBucketProbe = TestProbe()
  val selfNode = TestProbe().ref

  lazy val mockProvider = mock[AuthActor.Provider]

  trait MockAuthProvider extends AuthActor.Provider {
    override def authSender(kBucketActor: ActorRef, node: ActorRef, discoverNewNode: Boolean,
      customData: Option[Any], timeout: Duration, selfNode: ActorRef) = {
      mockProvider.authSender(kBucketActor, node, discoverNewNode, customData, timeout, selfNode)
      wrapActorRef(someProbe.ref)
    }
  }

  val verifyRef = TestActorRef[RequestSenderActor](Props(new RequestSenderActor(kBucketProbe.ref, mockConfig.requestTimeOut, selfNode) with MockAuthProvider))

  test("upon receiving a node request, create actor returned from authSender") {
    verifyRef ! NodeRequest(someProbe.ref, MockRequest(), false, customData = "custom data")
    awaitAssert(verify(mockProvider).authSender(kBucketProbe.ref, someProbe.ref, false, Some("custom data"), mockConfig.requestTimeOut, selfNode))
  }

  test("forward the request using the sender") {
    verifyRef ! NodeRequest(testActor, MockRequest())
    someProbe.expectMsgClass(classOf[Request])
    someProbe.lastSender == testActor
  }
}