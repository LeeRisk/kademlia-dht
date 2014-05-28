package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor.Add
import RequestDispatcher._
import akka.actor.{ Actor, Props, ReceiveTimeout }
import akka.testkit.{ TestActorRef, TestProbe }

import com.tommo.kademlia.protocol.Message._
import scala.concurrent.duration._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.OneInstancePerTest

class AutoActorTest extends BaseTestKit("AuthSpec") with BaseProtocolFixture with OneInstancePerTest {

  trait BaseFixture {
    val kProbe = TestProbe()
  }

  /* Test base class Auth */

  trait AuthFixture extends BaseFixture {

    val mockAuth = mock[AuthActor[MockRequest]]

    when(mockAuth.addToKBucket).thenReturn(true)

    class MockAuthActor extends AuthActor[MockRequest](kProbe.ref, mockConfig.requestTimeOut) {
      override def doInChallenge(msg: MockRequest) = mockAuth.doInChallenge(msg)
      override def authSuccess(reply: AuthReply) = mockAuth.authSuccess(reply)
      override val addToKBucket = mockAuth.addToKBucket
      override def doTimeOut() = { mockAuth.doTimeOut }
    }

    def mockAuthReply(ref: TestActorRef[MockAuthActor]) = AuthSenderReply(mockZeroId(4), echoId = ref.underlyingActor.toEchoId)

    lazy val verifyRef = TestActorRef[MockAuthActor](Props(new MockAuthActor))
  }

  test("initially invoke authChallenge for when a Request is received") {
    new AuthFixture {
      verifyRef ! MockRequest()
      verify(mockAuth).doInChallenge(MockRequest())
    }
  }

  test("invoke authSuccess if echoId matches toEchoId") {
    new AuthFixture {
      verifyRef.underlyingActor.init = true

      val authReply = mockAuthReply(verifyRef)
      verifyRef ! authReply

      verify(mockAuth).authSuccess(authReply)
    }
  }

  test("add sender self node after confirming authencity and addToKBucket is true") {
    new AuthFixture {
      verifyRef.underlyingActor.init = true
      val msg = Add(ActorNode(self, mockZeroId(4)))

      verifyRef ! mockAuthReply(verifyRef)

      kProbe.expectMsg(msg)
    }
  }

  test("don't add to kBucketActor if addToKBucket is false") {
    new AuthFixture {
      when(mockAuth.addToKBucket).thenReturn(false)

      verifyRef.underlyingActor.init = true
      val msg = Add(ActorNode(self, mockZeroId(4)))

      verifyRef ! mockAuthReply(verifyRef)

      kProbe.expectNoMsg(500 millisecond)
    }
  }

  test("save sender of the Request") {
    new AuthFixture {
      verifyRef ! MockRequest()
      awaitCond(verifyRef.underlyingActor.requestor == testActor)
    }
  }

  test("invokes doTimeout when ReceiveTimeout received") {
    new AuthFixture {
      verifyRef ! ReceiveTimeout

      awaitAssert(verify(mockAuth).doTimeOut())
    }
  }

  /* Test SenderAuth */

  trait SenderAuthFixTure extends BaseFixture {
    val selfNode = ActorNode(TestProbe().ref, id)
    val nodeProbe = TestProbe()
    lazy val customData: Option[Any] = None
    val verifyRef = TestActorRef[SenderAuthActor](Props(new SenderAuthActor(selfNode, kProbe.ref, nodeProbe.ref, true, customData, mockConfig.requestTimeOut)))
    verifyRef.underlyingActor.requestor = testActor
  }

  test("on receiving a request forward an AuthSenderRequest to node") {
    new SenderAuthFixTure {
      verifyRef ! MockRequest()
      nodeProbe.expectMsg(AuthReceiverRequest(MockRequest(), verifyRef.underlyingActor.toEchoId))
      nodeProbe.lastSender shouldBe selfNode.ref
    }
  }

  test("reply to original sender of Request and send an ack to the receiver") {
    new SenderAuthFixTure {
      verifyRef.underlyingActor.init = true
      verifyRef ! AuthRecieverReply(MockReply(), verifyRef.underlyingActor.toEchoId, 1, id)

      expectMsg(MockReply())
      nodeProbe.expectMsg(AuthSenderReply(mockZeroId(4), 1))
      nodeProbe.lastSender shouldBe selfNode.ref
    }
  }

  test("reply to original sender CustomReply if there was custom data") {
    new SenderAuthFixTure {
      override lazy val customData = Some("custom data")
      verifyRef.underlyingActor.init = true
      verifyRef ! AuthRecieverReply(MockReply(), verifyRef.underlyingActor.toEchoId, 1, id)

      expectMsg(CustomReply(MockReply(), "custom data"))
    }
  }

  test("if timeout occurred then send RequestTimeout") {
    new SenderAuthFixTure {
      override lazy val customData = Some("custom data")

      verifyRef.underlyingActor.request = Some(MockRequest())
      verifyRef.underlyingActor.doTimeOut()

      expectMsg(RequestTimeout(MockRequest(), "custom data"))
    }
  }

  /* Test ReceiverAuth */
  trait ReceiverAuthFixTure extends BaseFixture {
    val requestProbe = TestProbe()
    val selfNode = ActorNode(TestProbe().ref, id)

    val verifyRef = TestActorRef[ReceiverAuthActor](Props(new ReceiverAuthActor(selfNode, kProbe.ref, requestProbe.ref, mockConfig.requestTimeOut)))

    verifyRef.underlyingActor.requestor = testActor
  }

  test("delegate to requestHandler Actor to handle request before receiving echoId if Request is an immutable one") {
    new ReceiverAuthFixTure {
      verifyRef ! AuthReceiverRequest(MockRequest(), 1)
      requestProbe.expectMsg(MockRequest())
    }
  }

  test("delegate to requestHandler Actor to handle request after receiving echoId if Request is a mutable one") {
    new ReceiverAuthFixTure {
      val senderToEchoBack = verifyRef.underlyingActor.toEchoId

      verifyRef ! AuthReceiverRequest(MockMutableRequest(), 1)
      requestProbe.expectNoMsg(500 millisecond)

      verifyRef ! AuthSenderReply(mockZeroId(4), senderToEchoBack)
      requestProbe.expectMsg(MockMutableRequest())
    }
  }

  test("send AckReply if request was a mutable one") {
    new ReceiverAuthFixTure {
      val expectedEcho = 1
      verifyRef.underlyingActor.toEchoBack = expectedEcho

      verifyRef ! AuthReceiverRequest(MockMutableRequest(), 1)

      expectMsg(AuthRecieverReply(AckReply, expectedEcho, verifyRef.underlyingActor.toEchoId, id))
    }
  }

  test("send toEchoId along with the echoId of the request and the result") {
    new ReceiverAuthFixTure {
      val expectedEcho = 1
      verifyRef.underlyingActor.init = true
      verifyRef.underlyingActor.toEchoBack = expectedEcho

      verifyRef ! MockReply()

      expectMsg(AuthRecieverReply(MockReply(), expectedEcho, verifyRef.underlyingActor.toEchoId, id))
    }
  }

  test("use this kad node root actorRef as the sender when replying to the remote node") {
    new ReceiverAuthFixTure {
      verifyRef.underlyingActor.init = true

      verifyRef ! MockReply()

      expectMsgAnyClassOf(classOf[AuthRecieverReply]) // this syncs with the test otherwise an error will be thrown
      lastSender shouldBe selfNode.ref
    }
  }

}