package com.tommo.kademlia.protocol

import com.tommo.kademlia.BaseTestKit
import com.tommo.kademlia.identity.Id
import com.tommo.kademlia.routing.KBucketSetActor.Add
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.TestActorRef
import akka.testkit.TestProbe

import org.mockito.Matchers._
import org.mockito.Mockito._

class AutoActorTest extends BaseTestKit("AuthSpec") with BaseProtocolFixture {

  val bucketProbe = TestProbe()

  /* Test base class Auth */

  trait AuthFixture {
    val mockAuth = mock[AuthActor]

    class MockAuthActor extends AuthActor(bucketProbe.ref) {
      override def authChallenge(msg: Message) = mockAuth.authChallenge(msg)
      override def authSuccess(reply: AuthReply) = mockAuth.authSuccess(reply)
    }

    def mockAuthReply(ref: TestActorRef[MockAuthActor]) = AuthSenderReply(mockZeroId(4), echoId = ref.underlyingActor.toEchoId)

    lazy val mockAuthActor: MockAuthActor = new MockAuthActor

    val verifyRef = TestActorRef[MockAuthActor](Props(mockAuthActor))

    verifyRef.underlyingActor.requestor = testActor
  }

  test("initially invoke authChallenge for when a Request is received") { // todo mock
    new AuthFixture {
      verifyRef ! MockRequest()
      verify(mockAuth).authChallenge(MockRequest())
    }
  }

  test("invoke authSuccess if echoId matches toEchoId") { // todo mock
    new AuthFixture {
      verifyRef.underlyingActor.init = true

      val authReply = mockAuthReply(verifyRef)
      verifyRef ! authReply

      verify(mockAuth).authSuccess(authReply)
    }
  }
  
    test("add sender to kBucketActor after confirming authencity") {
      new AuthFixture {
        verifyRef.underlyingActor.init = true
        val msg = Add(ActorNode(self, mockZeroId(4)))
  
        verifyRef ! mockAuthReply(verifyRef)
  
        bucketProbe.expectMsg(msg)
      }
    }

  test("save sender of the Request") {
    new AuthFixture {
      verifyRef ! MockRequest()
      awaitCond(verifyRef.underlyingActor.requestor == testActor)
    }
  }

  /* Test SenderAuth */

  trait SenderAuthFixTure {
    val nodeProbe = TestProbe()

    val verifyRef = TestActorRef[SenderAuthActor](Props(new SenderAuthActor(mockZeroId(4), bucketProbe.ref, nodeProbe.ref)))
    verifyRef.underlyingActor.requestor = testActor
  }

  test("on receiving a RequestEnvelope forward an AuthSenderRequest to node") {
    new SenderAuthFixTure {
      verifyRef ! MockRequest()
      nodeProbe.expectMsg(AuthSenderRequest(MockRequest(), verifyRef.underlyingActor.toEchoId))
    }
  }

  test("reply to original sender of Request and send an ack to the receiver") {
    new SenderAuthFixTure {
      verifyRef.underlyingActor.init = true
      verifyRef ! AuthRecieverReply(MockReply(), verifyRef.underlyingActor.toEchoId, 1)

      expectMsg(MockReply())
      nodeProbe.expectMsg(AuthSenderReply(mockZeroId(4), 1))
    }
  }

  /* Test ReceiverAuth */
  trait ReceiverAuthFixTure {
    val requestProbe = TestProbe()
    val selfProbe = TestProbe()

    val verifyRef = TestActorRef[ReceiverAuthActor](Props(new ReceiverAuthActor(bucketProbe.ref, requestProbe.ref, selfProbe.ref)))

    verifyRef.underlyingActor.requestor = testActor
  }

  test("delegate to requestHandler Actor for request") {
    new ReceiverAuthFixTure {
      verifyRef ! AuthSenderRequest(MockRequest(), 1)
      requestProbe.expectMsg(MockRequest())
    }
  }

  test("send toEchoId along with the echoId of the request and the result") {
    new ReceiverAuthFixTure {
      val expectedEcho = 1
      verifyRef.underlyingActor.toEchoBack = expectedEcho

      verifyRef ! MockReply()

      expectMsg(AuthRecieverReply(MockReply(), expectedEcho, verifyRef.underlyingActor.toEchoId))
    }
  }

  test("use this kad node root actorRef as the sender when replying to the remote node") {
    new ReceiverAuthFixTure {
      verifyRef ! MockReply()

      expectMsgAnyClassOf(classOf[AuthRecieverReply]) // this syncs with the test otherwise an error will be thrown
      lastSender shouldBe selfProbe.ref
    }
  }
}