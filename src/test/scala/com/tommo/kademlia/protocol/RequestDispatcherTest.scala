package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.protocol.RequestDispatcher._

import akka.actor.{ Actor, ActorRef, Props }

import akka.testkit.TestActorRef
import akka.testkit.TestProbe

import org.mockito.Matchers._
import org.mockito.Mockito._
import scala.concurrent.duration.Duration
import Message._

class RequestDispatcherTest extends BaseTestKit("SenderSpec") with BaseProtocolFixture {
  val someProbe = TestProbe()
  val selfNode = ActorNode(TestProbe().ref, id)
  val reqHandlerProbe = TestProbe()

  lazy val mockProvider = mock[AuthActor.Provider]

  trait MockAuthProvider extends AuthActor.Provider {
    override def authSender(selfNode: ActorNode, node: ActorRef, discoverNewNode: Boolean,
      customData: Option[Any], timeout: Duration) = {
      mockProvider.authSender(selfNode, node, discoverNewNode, customData, timeout)
      wrapActorRef(someProbe.ref)
    }

    override def authReceiver(selfNode: ActorNode, requestHandler: ActorRef, timeout: Duration) = {
      mockProvider.authReceiver(selfNode, requestHandler, timeout)
      wrapActorRef(someProbe.ref)
    }

  }

  val verifyRef = TestActorRef[RequestDispatcher](Props(new RequestDispatcher(selfNode, reqHandlerProbe.ref, mockConfig.requestTimeOut) with MockAuthProvider))

  test("upon receiving a node request, create actor returned from authSender") {
    verifyRef ! NodeRequest(someProbe.ref, MockRequest(), false, customData = "custom data")
    awaitAssert(verify(mockProvider).authSender(selfNode, someProbe.ref, false, Some("custom data"), mockConfig.requestTimeOut))
  }

  test("upon receiving a AuthSenderRequest, create actor returned from authSender") {
    verifyRef ! AuthSenderRequest(MockRequest(), 123)
    awaitAssert(verify(mockProvider).authReceiver(selfNode, reqHandlerProbe.ref, mockConfig.requestTimeOut))
  }

  test("forward the request using the sender") {
    verifyRef ! NodeRequest(testActor, MockRequest())
    someProbe.expectMsgClass(classOf[Request])
    someProbe.lastSender == testActor
  }

  test("forward the authrequest using the sender") {
    verifyRef ! AuthSenderRequest(MockRequest(), 123)
    someProbe.expectMsgClass(classOf[AuthSenderRequest])
    someProbe.lastSender == testActor
  }

}