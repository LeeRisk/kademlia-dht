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
  val selfNode = ActorNode(TestProbe().ref, id)

  lazy val mockProvider = mock[AuthActor.Provider]

  trait MockAuthProvider extends AuthActor.Provider {
    override def authSender(selfNode: ActorNode, kBucketActor: ActorRef, node: ActorRef, discoverNewNode: Boolean,
      customData: Option[Any], timeout: Duration) = {
      mockProvider.authSender(selfNode, kBucketActor, node, discoverNewNode, customData, timeout)
      wrapActorRef(someProbe.ref)
    }
  }

  val verifyRef = TestActorRef[RequestSenderActor](Props(new RequestSenderActor(selfNode, kBucketProbe.ref, mockConfig.requestTimeOut) with MockAuthProvider))

  test("upon receiving a node request, create actor returned from authSender") {
    verifyRef ! NodeRequest(someProbe.ref, MockRequest(), false, customData = "custom data")
    awaitAssert(verify(mockProvider).authSender(selfNode, kBucketProbe.ref, someProbe.ref, false, Some("custom data"), mockConfig.requestTimeOut))
  }

  test("forward the request using the sender") {
    verifyRef ! NodeRequest(testActor, MockRequest())
    someProbe.expectMsgClass(classOf[Request])
    someProbe.lastSender == testActor
  }
}